import { useEffect, useState, type FormEvent } from 'react'
import { Check, PackageCheck, Plus, RotateCcw, X } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './purchasing.css'

type Page<T> = { content: T[]; totalElements: number; totalPages: number }
type ReturnStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'DISPATCHED' | 'CANCELLED'
type PurchaseOrder = { id: number; orderNumber: string; supplierName: string; status: string }
type ReturnableItem = {
  purchaseOrderItemId: number; productId: number; sku: string; description: string
  receivedQuantity: number; returnedQuantity: number; returnableQuantity: number; unitCost: number
}
type ReturnItem = {
  id: number; purchaseOrderItemId: number; productId: number; sku: string; description: string
  quantity: number; damagedQuantity: number; unitCost: number; lineTotal: number
}
type CreditNote = { id: number; supplierReference: string; amount: number; currencyCode: string; notes: string | null; recordedBy: string; recordedAt: string }
type SupplierPayablePayment = { id: number; paymentReference: string; paymentMethod: string; amount: number; currencyCode: string; notes: string | null; recordedByEmail: string; paidAt: string }
type SupplierPayable = {
  id: number; payableNumber: string; purchaseOrderId: number; purchaseOrderNumber: string; supplierId: number; supplierName: string
  invoiceReference: string | null; status: 'OPEN' | 'PARTIALLY_PAID' | 'SETTLED' | 'CANCELLED'; currencyCode: string
  grossAmount: number; creditAppliedAmount: number; netPayableAmount: number; amountPaid: number; balanceDue: number
  dueDate: string | null; notes: string | null; createdAt: string; payments: SupplierPayablePayment[]
}
type SupplierReturn = {
  id: number; returnNumber: string; purchaseOrderId: number; orderNumber: string; supplierName: string
  warehouseName: string; status: ReturnStatus; reason: string; notes: string | null; rejectionReason: string | null
  currencyCode: string; expectedCreditAmount: number; creditedAmount: number; requestedBy: string
  reviewedBy: string | null; dispatchedBy: string | null; cancelledBy: string | null; requestedAt: string; reviewedAt: string | null
  dispatchedAt: string | null; cancelledAt: string | null; items: ReturnItem[]; creditNotes: CreditNote[]
}

const statusLabel: Record<ReturnStatus, string> = {
  PENDING_APPROVAL: 'Pending approval', APPROVED: 'Approved', REJECTED: 'Rejected',
  DISPATCHED: 'Dispatched', CANCELLED: 'Cancelled',
}
function money(value: number, currency: string) {
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) }
  catch { return currency + ' ' + value.toFixed(2) }
}

export function SupplierReturnsPage() {
  const { user } = useAuth()
  const roles = user?.roles ?? []
  const canCreate = roles.some(role => ['OWNER', 'ADMIN', 'PURCHASING_OFFICER'].includes(role))
  const canReview = roles.some(role => ['OWNER', 'ADMIN'].includes(role))
  const canDispatch = roles.some(role => ['OWNER', 'ADMIN', 'WAREHOUSE_STAFF', 'INVENTORY_MANAGER'].includes(role))
  const canCredit = roles.some(role => ['OWNER', 'ADMIN', 'ACCOUNTANT'].includes(role))
  const canCancel = roles.some(role => ['OWNER', 'ADMIN', 'PURCHASING_OFFICER'].includes(role))
  const [rows, setRows] = useState<SupplierReturn[]>([])
  const [selected, setSelected] = useState<SupplierReturn | null>(null)
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [orderId, setOrderId] = useState('')
  const [returnable, setReturnable] = useState<ReturnableItem[]>([])
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [damaged, setDamaged] = useState<Record<number, string>>({})
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [creditReference, setCreditReference] = useState('')
  const [creditAmount, setCreditAmount] = useState('')
  const [creditNotes, setCreditNotes] = useState('')
  const [payables, setPayables] = useState<SupplierPayable[]>([])
  const [payablePoId, setPayablePoId] = useState('')
  const [invoiceRef, setInvoiceRef] = useState('')
  const [paymentAmount, setPaymentAmount] = useState<Record<number, string>>({})
  const [paymentRef, setPaymentRef] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [messageError, setMessageError] = useState(false)

  async function load(selectedId = selected?.id) {
    setLoading(true)
    try {
      const { data } = await api.get<Page<SupplierReturn>>('/admin/supplier-returns', { params: { page: 0, size: 50 } })
      setRows(data.content)
      const nextId = selectedId && data.content.some(row => row.id === selectedId) ? selectedId : data.content[0]?.id
      setSelected(nextId ? (await api.get<SupplierReturn>('/admin/supplier-returns/' + nextId)).data : null)
      try {
        const payablesRes = await api.get<Page<SupplierPayable>>('/admin/supplier-payables', { params: { page: 0, size: 50 } })
        setPayables(payablesRes.data.content ?? [])
      } catch {
        // ignore if role does not have payables access
      }
    } catch (error) {
      setMessage(getErrorMessage(error, 'Supplier returns could not be loaded.'))
      setMessageError(true)
    } finally { setLoading(false) }
  }

  useEffect(() => { void load(undefined) }, [])

  async function selectReturn(id: number) {
    setMessage('')
    try { setSelected((await api.get<SupplierReturn>('/admin/supplier-returns/' + id)).data) }
    catch (error) { setMessage(getErrorMessage(error, 'Supplier return details could not be loaded.')); setMessageError(true) }
  }

  async function openCreate() {
    setMessage('')
    try {
      const { data } = await api.get<Page<PurchaseOrder>>('/admin/purchase-orders', { params: { page: 0, size: 100 } })
      setOrders(data.content)
      setOrderId('')
      setReturnable([])
      setQuantities({})
      setDamaged({})
      setReason('')
      setNotes('')
      setCreateOpen(true)
    } catch (error) { setMessage(getErrorMessage(error, 'Purchase orders could not be loaded.')); setMessageError(true) }
  }

  async function chooseOrder(value: string) {
    setOrderId(value)
    setReturnable([])
    setQuantities({})
    setDamaged({})
    if (!value) return
    try {
      const { data } = await api.get<ReturnableItem[]>('/admin/supplier-returns/returnable-items', { params: { purchaseOrderId: value } })
      setReturnable(data)
    } catch (error) { setMessage(getErrorMessage(error, 'Returnable order items could not be loaded.')); setMessageError(true) }
  }

  async function createReturn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const items = returnable.flatMap(item => {
      const quantity = Number(quantities[item.purchaseOrderItemId] || 0)
      return quantity > 0 ? [{
        purchaseOrderItemId: item.purchaseOrderItemId,
        quantity,
        damagedQuantity: Number(damaged[item.purchaseOrderItemId] || 0),
      }] : []
    })
    if (items.length === 0) { setMessage('Enter a quantity for at least one received item.'); setMessageError(true); return }
    if (items.some(item => item.damagedQuantity < 0 || item.damagedQuantity > item.quantity)) {
      setMessage('Damaged quantity must be between zero and the return quantity.'); setMessageError(true); return
    }
    setBusy(true); setMessage(''); setMessageError(false)
    try {
      const { data } = await api.post<SupplierReturn>('/admin/supplier-returns', {
        idempotencyKey: crypto.randomUUID(), purchaseOrderId: Number(orderId), reason: reason.trim(),
        notes: notes.trim() || null, items,
      })
      setCreateOpen(false)
      setMessage('Supplier return sent for approval. Inventory will change when it is dispatched.')
      await load(data.id)
    } catch (error) { setMessage(getErrorMessage(error, 'Supplier return could not be created.')); setMessageError(true) }
    finally { setBusy(false) }
  }

  async function act(action: 'approve' | 'reject' | 'dispatch' | 'cancel' | 'credit-notes', body: unknown = {}) {
    if (!selected) return
    setBusy(true); setMessage(''); setMessageError(false)
    try {
      await api.post('/admin/supplier-returns/' + selected.id + '/' + action, body)
      setMessage(action === 'credit-notes' ? 'Supplier credit note recorded.' : 'Supplier return ' + action + ' completed.')
      if (action === 'credit-notes') { setCreditReference(''); setCreditAmount(''); setCreditNotes('') }
      if (action === 'reject') setRejectReason('')
      await load(selected.id)
    } catch (error) { setMessage(getErrorMessage(error, 'Supplier return action failed.')); setMessageError(true) }
    finally { setBusy(false) }
  }

  function recordCredit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!creditReference.trim() || Number(creditAmount) <= 0) { setMessage('Enter a supplier reference and a positive credit amount.'); setMessageError(true); return }
    void act('credit-notes', {
      idempotencyKey: crypto.randomUUID(), supplierReference: creditReference.trim(),
      amount: Number(creditAmount), notes: creditNotes.trim() || null,
    })
  }

  async function generatePayable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!payablePoId) return
    setBusy(true); setMessage(''); setMessageError(false)
    try {
      await api.post('/admin/supplier-payables', {
        purchaseOrderId: Number(payablePoId),
        invoiceReference: invoiceRef.trim() || null,
      })
      setInvoiceRef('')
      setPayablePoId('')
      setMessage('Supplier payable created and reconciled with received PO value and credit notes.')
      await load(selected?.id)
    } catch (error) {
      setMessage(getErrorMessage(error, 'Supplier payable could not be created.'))
      setMessageError(true)
    } finally {
      setBusy(false)
    }
  }

  async function recordSupplierPayment(payableId: number) {
    const amount = Number(paymentAmount[payableId] || 0)
    const ref = (paymentRef[payableId] || '').trim()
    if (amount <= 0 || !ref) {
      setMessage('Enter a payment reference and positive payment amount.')
      setMessageError(true)
      return
    }
    setBusy(true); setMessage(''); setMessageError(false)
    try {
      await api.post('/admin/supplier-payables/' + payableId + '/payments', {
        idempotencyKey: crypto.randomUUID(),
        paymentReference: ref,
        paymentMethod: 'BANK_TRANSFER',
        amount,
      })
      setPaymentAmount(curr => ({ ...curr, [payableId]: '' }))
      setPaymentRef(curr => ({ ...curr, [payableId]: '' }))
      setMessage('Supplier payment recorded and payable balance updated.')
      await load(selected?.id)
    } catch (error) {
      setMessage(getErrorMessage(error, 'Supplier payment could not be recorded.'))
      setMessageError(true)
    } finally {
      setBusy(false)
    }
  }

  const availableOrders = orders.filter(order => ['ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'].includes(order.status))

  return <main className="page-shell supplier-returns-page">
    <header className="page-title-row"><div><p className="eyebrow">Purchasing operations</p><h1>Supplier returns</h1><p>Approve returned quantities, dispatch stock from the warehouse, and record supplier credit notes.</p></div>
      {canCreate && <button className="primary-button" onClick={() => void openCreate()}><Plus size={15} /> New supplier return</button>}</header>
    {message && <div className={'notice ' + (messageError ? 'notice-error' : 'notice-success')} role="status">{message}</div>}
    <div className="supplier-return-layout">
      <section className="supplier-return-list" aria-label="Supplier return queue"><div className="panel-heading"><div><h2>Return queue</h2><p>{rows.length} recent records</p></div><button className="icon-button" aria-label="Refresh supplier returns" onClick={() => void load()}><RotateCcw size={15} /></button></div>
        {loading ? <div className="catalog-message">Loading supplier returns…</div> : rows.length === 0 ? <div className="catalog-empty"><strong>No supplier returns yet</strong><span>Returns linked to received purchase orders will appear here.</span></div> : rows.map(row => <button className={'supplier-return-row ' + (selected?.id === row.id ? 'active' : '')} key={row.id} type="button" onClick={() => void selectReturn(row.id)} aria-pressed={selected?.id === row.id}>
          <span><strong>{row.returnNumber}</strong><small>{row.orderNumber} · {row.supplierName}</small></span><span className={'status-pill status-' + row.status.toLowerCase().replaceAll('_', '-')}>{statusLabel[row.status]}</span>
        </button>)}
      </section>
      {selected ? <section className="supplier-return-detail" aria-label="Selected supplier return">
        <div className="panel-heading"><div><p className="eyebrow">{selected.orderNumber} · {selected.supplierName}</p><h2>{selected.returnNumber}</h2><p>{selected.warehouseName} · requested {new Date(selected.requestedAt).toLocaleString()}</p></div><span className={'status-pill status-' + selected.status.toLowerCase().replaceAll('_', '-')}>{statusLabel[selected.status]}</span></div>
        <div className="supplier-return-summary"><p><strong>Reason:</strong> {selected.reason}</p>{selected.notes && <p><strong>Notes:</strong> {selected.notes}</p>}{selected.rejectionReason && <p><strong>Rejection reason:</strong> {selected.rejectionReason}</p>}<p><strong>Requested by:</strong> {selected.requestedBy}</p>{selected.cancelledBy && <p><strong>Cancelled by:</strong> {selected.cancelledBy} · {selected.cancelledAt ? new Date(selected.cancelledAt).toLocaleString() : ''}</p>}</div>
        <div className="supplier-return-table-wrap"><table><thead><tr><th>Product</th><th>Returning</th><th>Damaged</th><th>Unit cost</th><th>Expected credit</th></tr></thead><tbody>{selected.items.map(item => <tr key={item.id}><td>{item.description}<small>{item.sku}</small></td><td>{item.quantity}</td><td>{item.damagedQuantity}</td><td>{money(item.unitCost, selected.currencyCode)}</td><td>{money(item.lineTotal, selected.currencyCode)}</td></tr>)}</tbody></table></div>
        <div className="supplier-credit-summary"><span>Expected at purchase cost<strong>{money(selected.expectedCreditAmount, selected.currencyCode)}</strong></span><span>Credits recorded<strong>{money(selected.creditedAmount, selected.currencyCode)}</strong></span></div>
        {selected.creditNotes.length > 0 && <div className="supplier-credit-notes"><h3>Recorded supplier credit notes</h3>{selected.creditNotes.map(note => <div key={note.id}><span><strong>{note.supplierReference}</strong><small>{note.recordedBy} · {new Date(note.recordedAt).toLocaleString()}{note.notes ? ' · ' + note.notes : ''}</small></span><strong>{money(note.amount, note.currencyCode)}</strong></div>)}</div>}
        <div className="supplier-return-actions">
          {selected.status === 'PENDING_APPROVAL' && canReview && <><button className="primary-button" disabled={busy} onClick={() => void act('approve')}><Check size={15} /> Approve return</button><label>Rejection reason<input maxLength={500} value={rejectReason} onChange={event => setRejectReason(event.target.value)} /></label><button className="secondary-button" disabled={busy || !rejectReason.trim()} onClick={() => void act('reject', { reason: rejectReason.trim() })}>Reject</button></>}
          {selected.status === 'APPROVED' && canDispatch && <><p>Dispatch checks on hand, reserved, and damaged quantities before it changes inventory.</p><button className="primary-button" disabled={busy} onClick={() => void act('dispatch')}><PackageCheck size={15} /> Dispatch supplier return</button></>}
          {['PENDING_APPROVAL', 'APPROVED'].includes(selected.status) && canCancel && <button className="secondary-button" disabled={busy} onClick={() => void act('cancel')}>Cancel request</button>}
          {selected.status === 'DISPATCHED' && canCredit && <form className="credit-note-form" onSubmit={recordCredit}><h3>Record a supplier credit note</h3><label>Supplier reference<input required maxLength={100} value={creditReference} onChange={event => setCreditReference(event.target.value)} placeholder="Credit note number" /></label><label>Credit amount<input required type="number" min="0.0001" step="0.0001" value={creditAmount} onChange={event => setCreditAmount(event.target.value)} /></label><label>Notes <span className="optional-label">Optional</span><input maxLength={500} value={creditNotes} onChange={event => setCreditNotes(event.target.value)} /></label><button className="primary-button" disabled={busy}><Check size={15} /> Record credit note</button><small>This records the supplier’s issued credit for accounting follow-up; it does not post to an external accounting system.</small></form>}
        </div>
      </section> : <section className="supplier-return-detail supplier-return-empty"><RotateCcw size={22} /><h2>Select a supplier return</h2><p>Choose a return from the queue to review its inventory and credit history.</p></section>}
    </div>
    {(canCreate || canCredit) && (
      <section className="panel" style={{ marginTop: '1.5rem' }} aria-label="Supplier payables and credit reconciliation">
        <div className="panel-heading">
          <div>
            <h2>Supplier payables & credit reconciliation</h2>
            <p>Generate supplier payables from received purchase orders, automatically apply supplier return credit notes, and record supplier settlements.</p>
          </div>
        </div>
        <form className="adjustment-form" onSubmit={event => void generatePayable(event)} style={{ marginBottom: '1rem' }}>
          <label>Received purchase order ID<input required type="number" min="1" value={payablePoId} onChange={e => setPayablePoId(e.target.value)} placeholder="e.g. 1" /></label>
          <label>Supplier invoice reference<input maxLength={120} value={invoiceRef} onChange={e => setInvoiceRef(e.target.value)} placeholder="INV-2026-001" /></label>
          <button className="secondary-button" type="submit" disabled={busy || !payablePoId}>Generate / Reconcile Payable</button>
        </form>
        {payables.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Payable #</th>
                  <th>PO / Supplier</th>
                  <th>Gross Received</th>
                  <th>Credits Applied</th>
                  <th>Paid</th>
                  <th>Balance Due</th>
                  <th>Status</th>
                  {canCredit && <th>Settlement</th>}
                </tr>
              </thead>
              <tbody>
                {payables.map(p => (
                  <tr key={p.id}>
                    <td><strong>{p.payableNumber}</strong>{p.invoiceReference && <small>{p.invoiceReference}</small>}</td>
                    <td>{p.purchaseOrderNumber} · {p.supplierName}</td>
                    <td>{money(p.grossAmount, p.currencyCode)}</td>
                    <td>{money(p.creditAppliedAmount, p.currencyCode)}</td>
                    <td>{money(p.amountPaid, p.currencyCode)}</td>
                    <td><strong>{money(p.balanceDue, p.currencyCode)}</strong></td>
                    <td><span className="status-pill status-neutral">{p.status}</span></td>
                    {canCredit && (
                      <td>
                        {p.balanceDue > 0 ? (
                          <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                            <input aria-label={'Payment ref for ' + p.payableNumber} placeholder="Ref #" value={paymentRef[p.id] ?? ''} onChange={e => setPaymentRef(c => ({ ...c, [p.id]: e.target.value }))} style={{ width: '90px' }} />
                            <input aria-label={'Payment amount for ' + p.payableNumber} type="number" step="0.01" placeholder={String(p.balanceDue)} value={paymentAmount[p.id] ?? ''} onChange={e => setPaymentAmount(c => ({ ...c, [p.id]: e.target.value }))} style={{ width: '90px' }} />
                            <button type="button" className="secondary-button" disabled={busy} onClick={() => void recordSupplierPayment(p.id)}>Pay</button>
                          </div>
                        ) : <small>Settled</small>}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    )}
    {createOpen && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setCreateOpen(false) }}><section className="product-modal purchase-modal" role="dialog" aria-modal="true" aria-labelledby="supplier-return-form-title">
      <div className="modal-heading"><div><div className="eyebrow">Purchasing operations</div><h2 id="supplier-return-form-title">New supplier return</h2></div><button type="button" className="icon-button" aria-label="Close" onClick={() => setCreateOpen(false)}><X size={18} /></button></div>
      <form onSubmit={event => void createReturn(event)} className="product-form">
        <label>Purchase order<select required value={orderId} onChange={event => void chooseOrder(event.target.value)}><option value="">Select a purchase order</option>{availableOrders.map(order => <option key={order.id} value={order.id}>{order.orderNumber} · {order.supplierName} · {order.status}</option>)}</select></label>
        {orderId && <div className="receipt-line-list"><div className="supplier-return-entry supplier-return-entry-heading"><span>Product</span><span>Returnable</span><span>Return qty</span><span>Damaged qty</span></div>
          {returnable.length === 0 ? <p className="purchase-notes">There are no received quantities left to return on this order.</p> : returnable.map(item => <div className="supplier-return-entry" key={item.purchaseOrderItemId}><span><strong>{item.description}</strong><small>{item.sku} · received {item.receivedQuantity}</small></span><span>{item.returnableQuantity}</span><input aria-label={'Return quantity for ' + item.sku} type="number" min="0" max={item.returnableQuantity} step="0.001" value={quantities[item.purchaseOrderItemId] ?? ''} onChange={event => setQuantities(current => ({ ...current, [item.purchaseOrderItemId]: event.target.value }))} /><input aria-label={'Damaged quantity for ' + item.sku} type="number" min="0" max={quantities[item.purchaseOrderItemId] || 0} step="0.001" value={damaged[item.purchaseOrderItemId] ?? '0'} onChange={event => setDamaged(current => ({ ...current, [item.purchaseOrderItemId]: event.target.value }))} /></div>)}</div>}
        <label>Reason<input required maxLength={500} value={reason} onChange={event => setReason(event.target.value)} placeholder="Describe why the goods are being returned" /></label>
        <label>Notes <span className="optional-label">Optional</span><textarea rows={3} maxLength={1000} value={notes} onChange={event => setNotes(event.target.value)} /></label>
        <p className="idempotency-hint">Requested items are reserved against this purchase order. Stock leaves the warehouse only after approval and dispatch.</p>
        {message && <div className="notice notice-error">{message}</div>}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={() => setCreateOpen(false)}>Close</button><button className="primary-button" disabled={busy || !orderId || returnable.length === 0}>{busy ? 'Submitting…' : 'Submit for approval'}</button></div>
      </form>
    </section></div>}
  </main>
}
