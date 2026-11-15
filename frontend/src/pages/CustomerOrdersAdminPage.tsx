import { useCallback, useEffect, useMemo, useState } from 'react'
import { BadgeCheck, ClipboardList, Clock3, PackageCheck, RefreshCw, Truck } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './customer-orders-admin.css'

type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'READY_FOR_PICKUP' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
type FulfillmentType = 'DELIVERY' | 'STORE_PICKUP'
type PaymentMethod = 'CASH_ON_DELIVERY' | 'MANUAL_TRANSFER'
type PaymentStatus = 'UNPAID' | 'AWAITING_VERIFICATION' | 'PAID' | 'FAILED' | 'REFUNDED' | 'CANCELLED'

interface Page<T> { content: T[]; totalElements: number; totalPages: number }
interface OrderItem { id: number; sku: string; productName: string; unitOfMeasure: string; quantity: number; unitPrice: number; lineTotal: number }
interface CustomerOrder {
  id: number; orderNumber: string; customerEmail: string; warehouseName: string; fulfillmentType?: FulfillmentType; status: OrderStatus
  paymentMethod: PaymentMethod; paymentStatus: PaymentStatus; currencyCode: string; total: number
  recipientName: string; recipientPhone: string; addressLine1: string; addressLine2: string | null
  city: string; region: string | null; postalCode: string | null; countryCode: string; notes: string | null
  createdAt: string; items: OrderItem[]
}
interface StatusHistory { id: number; fromStatus: OrderStatus | null; toStatus: OrderStatus; changedBy: string; note: string | null; occurredAt: string }

const filters: Array<{ label: string; value: OrderStatus | '' }> = [
  { label: 'All orders', value: '' }, { label: 'Pending', value: 'PENDING' },
  { label: 'Confirmed', value: 'CONFIRMED' }, { label: 'Processing', value: 'PROCESSING' },
  { label: 'Ready for pickup', value: 'READY_FOR_PICKUP' }, { label: 'Shipped', value: 'SHIPPED' },
  { label: 'Delivered', value: 'DELIVERED' }, { label: 'Cancelled', value: 'CANCELLED' },
]

const nextStatuses: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING: ['CONFIRMED'], CONFIRMED: ['PROCESSING'],
  PROCESSING: ['SHIPPED', 'READY_FOR_PICKUP'], READY_FOR_PICKUP: ['DELIVERED'], SHIPPED: ['DELIVERED'],
}

function label(value: string) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()) }
function money(value: number, currency: string) {
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) }
  catch { return currency + ' ' + value.toFixed(2) }
}
function createKey() {
  return globalThis.crypto?.randomUUID?.() ?? 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0
    return (char === 'x' ? random : (random & 0x3 | 0x8)).toString(16)
  })
}

export function CustomerOrdersAdminPage() {
  const { user } = useAuth()
  const roles = user?.roles ?? []
  const canTransition = roles.some((role) => ['OWNER', 'ADMIN', 'WAREHOUSE_STAFF'].includes(role))
  const canVerifyPayment = roles.some((role) => ['OWNER', 'ADMIN', 'ACCOUNTANT', 'CASHIER'].includes(role))
  const [orders, setOrders] = useState<CustomerOrder[]>([])
  const [selected, setSelected] = useState<CustomerOrder | null>(null)
  const [history, setHistory] = useState<StatusHistory[]>([])
  const [historyOrderId, setHistoryOrderId] = useState<number | null>(null)
  const [historyRevision, setHistoryRevision] = useState(0)
  const [filter, setFilter] = useState<OrderStatus | ''>('')
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [statusNote, setStatusNote] = useState('')
  const [paymentAmount, setPaymentAmount] = useState('')
  const [paymentReference, setPaymentReference] = useState('')
  const [paymentNotes, setPaymentNotes] = useState('')
  const [paymentKey, setPaymentKey] = useState(createKey)

  const loadOrders = useCallback(async (preferredId?: number, pageNumber = page) => {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get<Page<CustomerOrder>>('/admin/customer-orders', {
        params: { page: pageNumber, size: 25, ...(filter ? { status: filter } : {}) },
      })
      setOrders(data.content)
      setTotal(data.totalElements)
      setTotalPages(data.totalPages)
      setSelected((current) => data.content.find((order) => order.id === (preferredId ?? current?.id)) ?? data.content[0] ?? null)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Customer orders could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [filter, page])

  useEffect(() => {
    let active = true
    void Promise.resolve().then(() => { if (active) return loadOrders() })
    return () => { active = false }
  }, [loadOrders])

  const selectedId = selected?.id
  useEffect(() => {
    if (!selectedId) return
    let active = true
    api.get<StatusHistory[]>('/admin/customer-orders/' + selectedId + '/history')
      .then(({ data }) => { if (active) { setHistory(data); setHistoryOrderId(selectedId) } })
      .catch((requestError: unknown) => {
        if (active) {
          setHistory([])
          setHistoryOrderId(selectedId)
          setError(getErrorMessage(requestError, 'Order history could not be loaded.'))
        }
      })
    return () => { active = false }
  }, [selectedId, historyRevision])

  const transitions = useMemo(() => {
    if (!selected) return []
    if (selected.status === 'PROCESSING' && selected.fulfillmentType === 'STORE_PICKUP') {
      return ['READY_FOR_PICKUP'] as OrderStatus[]
    }
    return nextStatuses[selected.status] ?? []
  }, [selected])
  const canRecordPayment = Boolean(selected && selected.paymentStatus !== 'PAID' && selected.status !== 'CANCELLED'
    && ((selected.paymentMethod === 'MANUAL_TRANSFER' && selected.paymentStatus === 'AWAITING_VERIFICATION')
      || (selected.paymentMethod === 'CASH_ON_DELIVERY' && (selected.status === 'DELIVERED' || selected.status === 'READY_FOR_PICKUP'))))
  const paymentAmountValue = paymentAmount || (selected ? String(selected.total) : '')
  const selectedHistory = historyOrderId === selectedId ? history : []

  async function changeStatus(status: OrderStatus) {
    if (!selected) return
    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<CustomerOrder>('/admin/customer-orders/' + selected.id + '/status', {
        status, note: statusNote.trim() || null,
      })
      setSelected(data)
      setOrders((current) => current.map((order) => order.id === data.id ? data : order))
      setStatusNote('')
      setNotice('Order moved to ' + label(data.status) + '.')
      setHistoryRevision((current) => current + 1)
      await loadOrders(data.id)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Order status could not be updated.'))
    } finally { setBusy(false) }
  }

  async function verifyPayment() {
    if (!selected) return
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post('/admin/customer-orders/' + selected.id + '/payments/verify', {
        idempotencyKey: paymentKey, amount: Number(paymentAmountValue),
        reference: paymentReference.trim() || null, notes: paymentNotes.trim() || null,
      })
      setPaymentKey(createKey())
      setPaymentReference(''); setPaymentNotes('')
      setNotice('Payment verification recorded. The order is now marked paid.')
      await loadOrders(selected.id)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Payment verification could not be recorded.'))
    } finally { setBusy(false) }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">CUSTOMER COMMERCE</div><h1>Customer orders</h1><p>Review order progress, fulfillment and manually verified payments.</p></div>
        <button className="secondary-button" onClick={() => void loadOrders(selected?.id)} disabled={loading}><RefreshCw size={15} /> Refresh</button>
      </div>
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {error && <div className="notice notice-error" role="alert">{error}</div>}
      <div className="customer-admin-layout">
        <section className="panel customer-admin-list-panel">
          <div className="toolbar customer-admin-toolbar">
            <div className="toolbar-title"><ClipboardList size={18} /><strong>Order queue</strong><span>{total}</span></div>
            <label className="customer-order-filter">Status <select aria-label="Filter orders by status" value={filter} onChange={(event) => { setPaymentAmount(''); setPaymentReference(''); setPaymentNotes(''); setPage(0); setFilter(event.target.value as OrderStatus | '') }}>{filters.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}</select></label>
          </div>
          {loading ? <div className="inline-empty">Loading customer orders…</div> : orders.length === 0 ? <div className="inline-empty">No customer orders match this status.</div> : (
            <div className="customer-admin-queue">
              {orders.map((order) => <button className={'customer-admin-order-row' + (selected?.id === order.id ? ' selected' : '')} key={order.id} onClick={() => { setSelected(order); setPaymentAmount(''); setPaymentReference(''); setPaymentNotes('') }} aria-pressed={selected?.id === order.id}>
                <span className="customer-admin-order-icon"><PackageCheck size={17} /></span>
                <span className="customer-admin-order-copy"><strong>{order.orderNumber}</strong><small>{order.customerEmail} · {new Date(order.createdAt).toLocaleDateString()}</small></span>
                <span className="customer-admin-order-total">{money(order.total, order.currencyCode)}<small>{label(order.status)}</small></span>
              </button>)}
            </div>
          )}
          {!loading && totalPages > 1 && <div className="pagination-bar"><span>Page {page + 1} of {totalPages} · {total} orders</span><div><button className="secondary-button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button><button className="secondary-button" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}>Next</button></div></div>}
        </section>

        {selected ? <section className="panel customer-admin-detail" aria-label="Selected order details">
          <header className="customer-admin-detail-header"><div><div className="eyebrow">{selected.orderNumber}</div><h2>{selected.customerEmail}</h2><p>Placed {new Date(selected.createdAt).toLocaleString()}</p></div><div className="customer-admin-pills"><span className="status-pill status-live">{label(selected.status)}</span><span className="status-pill status-neutral">{label(selected.paymentStatus)}</span></div></header>
          <div className="customer-admin-summary"><span><small>Order total</small><strong>{money(selected.total, selected.currencyCode)}</strong></span><span><small>Payment method</small><strong>{label(selected.paymentMethod)}</strong></span><span><small>Fulfillment location</small><strong>{selected.warehouseName}</strong></span></div>
          <div className="customer-admin-section"><h3>Items</h3><div className="customer-admin-lines">{selected.items.map((item) => <div className="customer-admin-line" key={item.id}><span><strong>{item.productName}</strong><small>{item.sku} · {item.quantity} {item.unitOfMeasure} × {money(item.unitPrice, selected.currencyCode)}</small></span><strong>{money(item.lineTotal, selected.currencyCode)}</strong></div>)}</div></div>
          <div className="customer-admin-section"><h3>Delivery details</h3><div className="customer-admin-address"><strong>{selected.recipientName}</strong><span>{selected.recipientPhone}</span><span>{selected.addressLine1}{selected.addressLine2 ? ', ' + selected.addressLine2 : ''}</span><span>{[selected.city, selected.region, selected.postalCode, selected.countryCode].filter(Boolean).join(', ')}</span>{selected.notes && <small>Customer note: {selected.notes}</small>}</div></div>

          {canTransition && transitions.length > 0 && <div className="customer-admin-section customer-admin-actions"><h3>Update fulfillment</h3><label>Staff note <textarea maxLength={500} rows={2} value={statusNote} onChange={(event) => setStatusNote(event.target.value)} placeholder="Optional note for the order history" /></label><div className="customer-admin-action-buttons">{transitions.map((status) => <button className="primary-button" key={status} disabled={busy} onClick={() => void changeStatus(status)}>{status === 'SHIPPED' ? <Truck size={15} /> : <PackageCheck size={15} />}{busy ? 'Saving…' : label(status)}</button>)}</div>{selected.paymentMethod === 'MANUAL_TRANSFER' && selected.paymentStatus !== 'PAID' && <small className="customer-admin-hint">Manual transfer must be verified before shipping.</small>}</div>}

          {canVerifyPayment && canRecordPayment && <div className="customer-admin-section customer-admin-payment"><h3><BadgeCheck size={16} /> Record payment verification</h3><p>{selected.paymentMethod === 'MANUAL_TRANSFER' ? 'Confirm the transfer against your bank or payment records before marking this order paid.' : 'Record the cash collected after delivery.'}</p><div className="customer-admin-payment-fields"><label>Amount ({selected.currencyCode})<input type="number" min="0.0001" step="0.0001" value={paymentAmountValue} onChange={(event) => setPaymentAmount(event.target.value)} /></label><label>Reference <span>Optional</span><input maxLength={100} value={paymentReference} onChange={(event) => setPaymentReference(event.target.value)} /></label><label className="payment-notes-field">Verification notes <span>Optional</span><textarea maxLength={500} rows={2} value={paymentNotes} onChange={(event) => setPaymentNotes(event.target.value)} /></label></div><button className="primary-button" disabled={busy || !paymentAmountValue || Number(paymentAmountValue) <= 0} onClick={() => void verifyPayment()}><BadgeCheck size={15} />{busy ? 'Recording…' : 'Verify full payment'}</button><small className="customer-admin-hint">The amount must exactly match the order total. The verification is retained in the audit record.</small></div>}

          <div className="customer-admin-section customer-admin-history"><h3><Clock3 size={15} /> Status history</h3>{selectedHistory.length ? <ol>{selectedHistory.map((event) => <li key={event.id}><span className="history-marker" /><div><strong>{event.fromStatus ? label(event.toStatus) : 'Order placed'}</strong><small>{event.changedBy} · {new Date(event.occurredAt).toLocaleString()}</small>{event.note && <p>{event.note}</p>}</div></li>)}</ol> : <p className="customer-admin-hint">{historyOrderId === selectedId ? 'No status history is available.' : 'Loading order history…'}</p>}</div>
        </section> : <section className="panel customer-admin-no-selection"><PackageCheck size={23} /><strong>Select an order to review its details.</strong></section>}
      </div>
    </>
  )
}
