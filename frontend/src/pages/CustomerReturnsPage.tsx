import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/client'

type Page<T> = { content: T[]; totalElements: number; totalPages: number }
type ReturnsApi = Pick<typeof api, 'get' | 'post'>
type OrderLine = { id: number; sku: string; productName: string; unitOfMeasure: string; quantity: number; unitPrice: number }
type Order = { id: number; orderNumber: string; status: string; paymentStatus: string; items: OrderLine[] }
type ReturnItem = { id: number; sku: string; productName: string; requestedQuantity: number; approvedQuantity: number; restockQuantity: number; damagedQuantity: number; scrapQuantity: number }
type ReturnCase = { id: number; returnNumber: string; sourceNumber: string; status: string; reason: string; rejectionReason: string | null; refundAmount: number; currencyCode: string; items: ReturnItem[]; refund: null | { refundNumber: string; amount: number } }

export function CustomerReturnsPage({ client = api }: { client?: ReturnsApi } = {}) {
  const [orders, setOrders] = useState<Order[]>([])
  const [orderId, setOrderId] = useState('')
  const [order, setOrder] = useState<Order | null>(null)
  const [returns, setReturns] = useState<ReturnCase[]>([])
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function loadReturns() {
    const { data } = await client.get<Page<ReturnCase>>('/customer/returns', { params: { page: 0, size: 50 } })
    setReturns(data.content)
  }

  useEffect(() => {
    void Promise.all([
      client.get<Page<Order>>('/customer/orders', { params: { page: 0, size: 50 } }).then(({ data }) => setOrders(data.content)),
      loadReturns(),
    ]).catch(error => setMessage(error.response?.data?.message ?? 'Could not load your orders and returns.'))
  }, [])

  async function selectOrder(id: string) {
    setOrderId(id); setOrder(null); setQuantities({});
    if (!id) return
    try {
      const { data } = await client.get<Order>('/customer/orders/' + id)
      setOrder(data)
    } catch (error: any) { setMessage(error.response?.data?.message ?? 'Could not load that order.') }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!order) return
    const items = Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0)
      .map(([sourceItemId, quantity]) => ({ sourceItemId: Number(sourceItemId), quantity }))
    if (!items.length) { setMessage('Enter a quantity for at least one item.'); return }
    setBusy(true); setMessage('')
    try {
      await client.post('/customer/orders/' + order.id + '/returns', {
        idempotencyKey: crypto.randomUUID(), reason, notes: notes || null, items,
      })
      setMessage('Return request sent. Staff will review it before any refund or inventory update.')
      setQuantities({}); setReason(''); setNotes('')
      await loadReturns()
    } catch (error: any) { setMessage(error.response?.data?.message ?? 'Could not submit this return request.') }
    finally { setBusy(false) }
  }

  return <main className="customer-page">
    <header className="customer-page-header"><p className="eyebrow">Your account</p><h1>Returns</h1><p>Request a return for a delivered order and follow its review, inspection, and refund status.</p></header>
    {message && <p role="status">{message}</p>}
    <section className="customer-panel"><h2>Request a return</h2>
      <label>Order<select value={orderId} onChange={event => void selectOrder(event.target.value)}><option value="">Choose an eligible order</option>{orders.filter(item => ['DELIVERED', 'RETURNED'].includes(item.status) && ['PAID', 'PARTIALLY_REFUNDED'].includes(item.paymentStatus)).map(item => <option key={item.id} value={item.id}>{item.orderNumber} · {item.status}</option>)}</select></label>
      {order && <form onSubmit={event => void submit(event)}>
        <fieldset><legend>Items to return</legend>{order.items.map(item => <label key={item.id} className="return-line">
          <span><strong>{item.productName}</strong><small>{item.sku} · {item.quantity} {item.unitOfMeasure} purchased</small></span>
          <input aria-label={'Quantity to return ' + item.productName} type="number" min="0" max={item.quantity} step="0.001" value={quantities[item.id] ?? ''} onChange={event => setQuantities(current => ({ ...current, [item.id]: event.target.value }))} />
        </label>)}</fieldset>
        <label>Reason<input required maxLength={500} value={reason} onChange={event => setReason(event.target.value)} /></label>
        <label>Notes (optional)<textarea maxLength={1000} value={notes} onChange={event => setNotes(event.target.value)} /></label>
        <button disabled={busy || !reason.trim()}>{busy ? 'Sending…' : 'Submit return request'}</button>
      </form>}
    </section>
    <section className="customer-panel"><h2>Return history</h2>
      {returns.length === 0 ? <p>No return requests yet.</p> : <div className="customer-order-list">{returns.map(item => <article key={item.id} className="customer-order-card">
        <div><strong>{item.returnNumber}</strong><p>Order {item.sourceNumber} · {item.status}</p><p>{item.reason}</p></div>
        <div><ul>{item.items.map(line => <li key={line.id}>{line.productName} · requested {line.requestedQuantity}, approved {line.approvedQuantity}; restock {line.restockQuantity}, damaged {line.damagedQuantity}, scrap {line.scrapQuantity}</li>)}</ul>
          {item.rejectionReason && <p>Review note: {item.rejectionReason}</p>}{item.refund && <p>Refund {item.refund.refundNumber}: {item.currencyCode} {Number(item.refund.amount).toFixed(2)}</p>}
        </div>
      </article>)}</div>}
    </section>
  </main>
}
