import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { api } from '../api/client'

type Status = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'INSPECTED' | 'REFUNDED'
type ReturnItem = {
  id: number; sourceItemId: number; sku: string; productName: string; unitOfMeasure: string
  unitPrice: number; requestedQuantity: number; approvedQuantity: number
  restockQuantity: number; damagedQuantity: number; scrapQuantity: number
}
type ReturnCase = {
  id: number; returnNumber: string; sourceType: 'CUSTOMER_ORDER' | 'POS_SALE'; sourceNumber: string
  customerName: string | null; customerEmail: string | null; status: Status; reason: string; notes: string | null
  rejectionReason: string | null; currencyCode: string; refundAmount: number; requestedAt: string
  items: ReturnItem[]; refund: null | { refundNumber: string; method: string; amount: number; reference: string | null }
}
type Sale = { id: number; saleNumber: string; items: Array<{ id: number; sku: string; productName: string; unitOfMeasure: string; quantity: number }> }
type Page<T> = { content: T[]; totalElements: number; totalPages: number }
type ReturnsApi = Pick<typeof api, 'get' | 'post'>

const panel: CSSProperties = { background: 'var(--surface, #fff)', border: '1px solid var(--border, #dbe2ea)', borderRadius: 12, padding: 18 }
const field: CSSProperties = { display: 'grid', gap: 5, margin: '10px 0' }

export function ReturnsAdminPage({ client = api }: { client?: ReturnsApi } = {}) {
  const [rows, setRows] = useState<ReturnCase[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [selected, setSelected] = useState<ReturnCase | null>(null)
  const [status, setStatus] = useState('')
  const [approved, setApproved] = useState<Record<number, string>>({})
  const [inspection, setInspection] = useState<Record<number, { restock: string; damaged: string; scrap: string }>>({})
  const [rejectReason, setRejectReason] = useState('')
  const [method, setMethod] = useState('CASH')
  const [reference, setReference] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [saleId, setSaleId] = useState('')
  const [sale, setSale] = useState<Sale | null>(null)
  const [saleQuantities, setSaleQuantities] = useState<Record<number, string>>({})
  const [saleReason, setSaleReason] = useState('')

  async function load(id = selectedId) {
    const params: Record<string, string | number> = { page: 0, size: 50 }
    if (status) params.status = status
    const { data } = await client.get<Page<ReturnCase>>('/admin/returns', { params })
    setRows(data.content)
    const next = id && data.content.some(row => row.id === id) ? id : data.content[0]?.id ?? null
    setSelectedId(next)
    if (next) {
      const detail = await client.get<ReturnCase>('/admin/returns/' + next)
      setSelected(detail.data)
      setApproved(Object.fromEntries(detail.data.items.map(item => [item.id, String(item.requestedQuantity)])))
      setInspection(Object.fromEntries(detail.data.items.map(item => [item.id, { restock: String(item.approvedQuantity), damaged: '0', scrap: '0' }])))
    } else setSelected(null)
  }

  useEffect(() => { void load(null).catch(error => setMessage(error.response?.data?.message ?? 'Could not load return requests.')) }, [status])
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('saleId')
    if (!id) return
    setSaleId(id)
    void loadSale(id)
  }, [])

  async function act(path: string, body: unknown) {
    if (!selected) return
    setBusy(true); setMessage('')
    try {
      await client.post('/admin/returns/' + selected.id + '/' + path, body)
      setMessage('Return ' + path + ' completed.')
      await load(selected.id)
    } catch (error: any) { setMessage(error.response?.data?.message ?? 'Action failed. Check the return state and try again.') }
    finally { setBusy(false) }
  }

  const itemCount = useMemo(() => selected?.items.length ?? 0, [selected])

  async function loadSale(id = saleId) {
    if (!id || Number(id) < 1) return
    try {
      const { data } = await client.get<Sale>('/admin/pos/sales/' + id)
      setSale(data)
      setSaleQuantities(Object.fromEntries(data.items.map(item => [item.id, ''])))
      setMessage('POS sale loaded.')
    } catch (error: any) { setSale(null); setMessage(error.response?.data?.message ?? 'Could not load that POS sale.') }
  }

  async function submitPosReturn() {
    if (!sale || !saleReason.trim()) return
    const items = Object.entries(saleQuantities).filter(([, quantity]) => Number(quantity) > 0)
      .map(([sourceItemId, quantity]) => ({ sourceItemId: Number(sourceItemId), quantity }))
    if (!items.length) { setMessage('Enter a quantity for at least one POS item.'); return }
    setBusy(true); setMessage('')
    try {
      await client.post('/admin/pos/sales/' + sale.id + '/returns', {
        idempotencyKey: crypto.randomUUID(), reason: saleReason, items,
      })
      setMessage('POS return request added to the staff review queue.')
      setSale(null); setSaleReason(''); setSaleId(''); await load(null)
    } catch (error: any) { setMessage(error.response?.data?.message ?? 'Could not submit this POS return.') }
    finally { setBusy(false) }
  }

  return <main className="page-shell" style={{ display: 'grid', gap: 18 }}>
    <header><p className="eyebrow">Sales operations</p><h1>Returns and refunds</h1><p>Review requests, inspect every returned item, and record completed refunds.</p></header>
    {message && <p role="status">{message}</p>}
    <details style={panel}><summary>Start a POS sale return</summary>
      <div style={{ display: 'flex', gap: 10, alignItems: 'end', flexWrap: 'wrap' }}>
        <label style={field}>Sale ID<input type="number" min="1" value={saleId} onChange={event => setSaleId(event.target.value)} /></label>
        <button type="button" disabled={!saleId} onClick={() => void loadSale()}>Load sale</button>
      </div>
      {sale && <div><h3>{sale.saleNumber}</h3>{sale.items.map(item => <label key={item.id} style={{ display: 'flex', gap: 12, margin: '8px 0', alignItems: 'center' }}>
        <span>{item.productName} · {item.sku} · sold {item.quantity} {item.unitOfMeasure}</span>
        <input aria-label={'POS return quantity ' + item.productName} type="number" min="0" max={item.quantity} step="0.001" value={saleQuantities[item.id] ?? ''} onChange={event => setSaleQuantities(current => ({ ...current, [item.id]: event.target.value }))} />
      </label>)}
        <label style={field}>Reason<input required maxLength={500} value={saleReason} onChange={event => setSaleReason(event.target.value)} /></label>
        <button type="button" disabled={busy || !saleReason.trim()} onClick={() => void submitPosReturn()}>Submit POS return</button>
      </div>}
    </details>
    <label style={{ ...field, maxWidth: 260 }}>Filter by status<select value={status} onChange={event => setStatus(event.target.value)}>
      <option value="">All returns</option>{['REQUESTED', 'APPROVED', 'REJECTED', 'INSPECTED', 'REFUNDED'].map(value => <option key={value}>{value}</option>)}
    </select></label>
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(250px, 0.8fr) minmax(0, 1.6fr)', gap: 18, alignItems: 'start' }}>
      <section style={panel} aria-label="Return requests"><h2>Requests ({rows.length})</h2>
        {rows.length === 0 ? <p>No return requests in this view.</p> : rows.map(row => <button key={row.id} type="button" onClick={() => void load(row.id)} aria-pressed={selectedId === row.id} style={{ display: 'block', width: '100%', textAlign: 'left', padding: 12, margin: '8px 0', borderRadius: 8 }}>
          <strong>{row.returnNumber}</strong><br />{row.sourceNumber} · {row.status}<br /><small>{row.customerName ?? 'POS sale'} · {new Date(row.requestedAt).toLocaleDateString()}</small>
        </button>)}
      </section>
      {selected && <section style={{ ...panel, display: 'grid', gap: 14 }} aria-label="Selected return">
        <div><p className="eyebrow">{selected.sourceType.replace('_', ' ')} · {selected.sourceNumber}</p><h2>{selected.returnNumber}</h2><p>{selected.customerName ?? 'POS sale'} {selected.customerEmail && `(${selected.customerEmail})`}</p><p><strong>Status:</strong> {selected.status} · <strong>Reason:</strong> {selected.reason}</p>{selected.notes && <p>{selected.notes}</p>}{selected.rejectionReason && <p>Rejection reason: {selected.rejectionReason}</p>}</div>
        <div><h3>Items ({itemCount})</h3><div style={{ overflowX: 'auto' }}><table><thead><tr><th>Product</th><th>Requested</th><th>Approved</th><th>Restock</th><th>Damaged</th><th>Scrap</th></tr></thead><tbody>
          {selected.items.map(item => <tr key={item.id}><td>{item.productName}<br /><small>{item.sku}</small></td><td>{item.requestedQuantity} {item.unitOfMeasure}</td>
            <td>{selected.status === 'REQUESTED' ? <input aria-label={'Approved quantity ' + item.productName} type="number" min="0" max={item.requestedQuantity} step="0.001" value={approved[item.id] ?? '0'} onChange={event => setApproved(value => ({ ...value, [item.id]: event.target.value }))} /> : item.approvedQuantity}</td>
            {(['restock', 'damaged', 'scrap'] as const).map(kind => {
              const value = kind === 'restock' ? item.restockQuantity : kind === 'damaged' ? item.damagedQuantity : item.scrapQuantity
              return <td key={kind}>{selected.status === 'APPROVED' ? <input aria-label={`${kind} quantity ${item.productName}`} type="number" min="0" max={item.approvedQuantity} step="0.001" value={inspection[item.id]?.[kind] ?? '0'} onChange={event => setInspection(current => ({ ...current, [item.id]: { ...current[item.id], [kind]: event.target.value } }))} /> : value}</td>
            })}
          </tr>)}
        </tbody></table></div></div>
        {selected.status === 'REQUESTED' && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'end' }}>
          <button disabled={busy} onClick={() => void act('approve', { items: selected.items.map(item => ({ returnItemId: item.id, approvedQuantity: approved[item.id] || '0' })) })}>Approve quantities</button>
          <label style={field}>Rejection reason<input maxLength={500} value={rejectReason} onChange={event => setRejectReason(event.target.value)} /></label>
          <button disabled={busy || !rejectReason.trim()} onClick={() => void act('reject', { reason: rejectReason })}>Reject</button>
        </div>}
        {selected.status === 'APPROVED' && <div><p>Inspection must account for all approved quantities. Damaged items stay out of sellable stock.</p><button disabled={busy} onClick={() => void act('inspect', { items: selected.items.map(item => ({ returnItemId: item.id, restockQuantity: inspection[item.id]?.restock || '0', damagedQuantity: inspection[item.id]?.damaged || '0', scrapQuantity: inspection[item.id]?.scrap || '0' })) })}>Save inspection</button></div>}
        {selected.status === 'INSPECTED' && selected.refundAmount > 0 && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'end' }}>
          <p>Refund amount set by the sale record: <strong>{selected.currencyCode} {Number(selected.refundAmount).toFixed(2)}</strong></p>
          <label style={field}>Refund method<select value={method} onChange={event => setMethod(event.target.value)}><option value="CASH">Cash</option><option value="MANUAL_TRANSFER">Manual transfer</option><option value="EXTERNAL_TERMINAL">External terminal</option></select></label>
          {method !== 'CASH' && <label style={field}>External refund reference<input required value={reference} onChange={event => setReference(event.target.value)} /></label>}
          <button disabled={busy || (method !== 'CASH' && !reference.trim())} onClick={() => void act('refund', { idempotencyKey: crypto.randomUUID(), method, reference: reference || null })}>Record completed refund</button>
        </div>}
        {selected.refund && <p>Refund {selected.refund.refundNumber}: {selected.currencyCode} {Number(selected.refund.amount).toFixed(2)} by {selected.refund.method} {selected.refund.reference && `· ${selected.refund.reference}`}</p>}
      </section>}
    </div>
  </main>
}
