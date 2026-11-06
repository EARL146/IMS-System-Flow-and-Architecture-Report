import { useEffect, useState, type FormEvent } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Check, ClipboardList, PackageCheck, Plus, Send, X } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './purchasing.css'

interface Page<T> { content: T[]; totalElements: number }
interface Supplier { id: number; code: string; name: string; active: boolean }
interface Warehouse { id: number; code: string; name: string; active: boolean }
interface Product { id: number; sku: string; name: string; costPrice: number; unitOfMeasure: string; active: boolean }
type OrderStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'ORDERED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED'
interface PurchaseOrderItem {
  id: number
  lineNumber?: number
  productId: number
  sku: string
  description: string
  orderedQuantity: number
  receivedQuantity: number
  outstandingQuantity: number
  unitCost: number
  lineTotal: number
}
interface PurchaseOrder {
  id: number
  orderNumber: string
  supplierId: number
  supplierName: string
  warehouseId: number
  warehouseName: string
  status: OrderStatus
  currencyCode: string
  expectedDate: string | null
  notes: string | null
  rejectionReason: string | null
  createdBy: string
  reviewedBy: string | null
  reviewedAt: string | null
  orderedAt: string | null
  createdAt: string
  total: number
  items: PurchaseOrderItem[]
}

const poLineSchema = z.object({
  productId: z.string().trim().min(1, 'Choose a product.'),
  quantity: z.string().trim().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,3})?$/, 'Enter a quantity with up to three decimal places.').refine((value) => Number(value) > 0, 'Quantity must be greater than zero.'),
  unitCost: z.string().trim().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/, 'Enter a cost with up to four decimal places.'),
})
const purchaseHeaderSchema = z.object({
  supplierId: z.string().trim().min(1, 'Choose a supplier.'),
  warehouseId: z.string().trim().min(1, 'Choose a warehouse.'),
  currencyCode: z.string().trim().regex(/^[A-Z]{3}$/, 'Use a three-letter currency code.'),
  expectedDate: z.string().optional(),
  notes: z.string().max(5000).optional(),
})
const createSchema = purchaseHeaderSchema.extend({
  items: z.array(poLineSchema).min(1, 'Add at least one product.'),
}).superRefine((values, context) => {
  const productIds = values.items.map((item) => item.productId)
  if (new Set(productIds).size !== productIds.length) {
    context.addIssue({ code: 'custom', message: 'Each product can appear only once on an order.', path: ['items'] })
  }
})
type CreateValues = z.infer<typeof createSchema>
type PurchaseHeaderValues = z.infer<typeof purchaseHeaderSchema>

const statusLabels: Record<OrderStatus, string> = {
  DRAFT: 'Draft', PENDING_APPROVAL: 'Pending approval', APPROVED: 'Approved', REJECTED: 'Rejected',
  ORDERED: 'Ordered', PARTIALLY_RECEIVED: 'Partially received', RECEIVED: 'Received', CANCELLED: 'Cancelled',
}

function money(value: number, currency: string) {
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) }
  catch { return currency + ' ' + value.toFixed(2) }
}

export function PurchaseOrdersPage() {
  const { user } = useAuth()
  const roles = user?.roles ?? []
  const canPurchase = roles.some((role) => ['OWNER', 'ADMIN', 'PURCHASING_OFFICER'].includes(role))
  const canApprove = roles.some((role) => ['OWNER', 'ADMIN'].includes(role))
  const canReceive = roles.some((role) => ['OWNER', 'ADMIN', 'WAREHOUSE_STAFF', 'INVENTORY_MANAGER'].includes(role))
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [selected, setSelected] = useState<PurchaseOrder | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [receiveOpen, setReceiveOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function loadOrders(selectedId = selected?.id, pageNumber = page) {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get<Page<PurchaseOrder>>('/admin/purchase-orders', { params: { page: pageNumber, size: 50 } })
      setOrders(data.content)
      setTotal(data.totalElements)
      if (selectedId) {
        const current = data.content.find((order) => order.id === selectedId)
        setSelected(current ?? null)
      }
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Purchase orders could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadOrders(undefined, 0)
      if (canPurchase) {
        void Promise.all([
          api.get<Page<Supplier>>('/admin/suppliers', { params: { page: 0, size: 100 } }),
          api.get<Warehouse[]>('/admin/warehouses'),
          api.get<Page<Product>>('/admin/products', { params: { page: 0, size: 100 } }),
        ]).then(([supplierResult, warehouseResult, productResult]) => {
          setSuppliers(supplierResult.data.content.filter((supplier) => supplier.active))
          setWarehouses(warehouseResult.data.filter((warehouse) => warehouse.active))
          setProducts(productResult.data.content.filter((product) => product.active))
        }).catch((requestError: unknown) => {
          setError(getErrorMessage(requestError, 'Purchase order options could not be loaded.'))
        })
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [canPurchase])

  async function performAction(action: 'submit' | 'approve' | 'reject' | 'send' | 'cancel') {
    if (!selected) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const path = '/admin/purchase-orders/' + selected.id + '/' + action
      const { data } = action === 'reject'
        ? await api.post<PurchaseOrder>(path, { reason: rejectReason.trim() })
        : await api.post<PurchaseOrder>(path, {})
      setSelected(data)
      setRejectReason('')
      setNotice('Purchase order ' + statusLabels[data.status].toLowerCase() + '.')
      await loadOrders(data.id)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Purchase order could not be updated.'))
    } finally {
      setBusy(false)
    }
  }

  async function receiveGoods(payload: { idempotencyKey: string; deliveryReference: string | null; notes: string | null; items: { purchaseOrderItemId: number; quantity: number }[] }) {
    if (!selected) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await api.post('/admin/purchase-orders/' + selected.id + '/receive', payload)
      const { data } = await api.get<PurchaseOrder>('/admin/purchase-orders/' + selected.id)
      setSelected(data)
      setReceiveOpen(false)
      setNotice('Goods receipt recorded and inventory updated.')
      await loadOrders(data.id)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Goods could not be received.'))
    } finally {
      setBusy(false)
    }
  }

  async function createOrder(payload: CreateValues) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const { data } = await api.post<PurchaseOrder>('/admin/purchase-orders', {
        supplierId: Number(payload.supplierId),
        warehouseId: Number(payload.warehouseId),
        currencyCode: payload.currencyCode.trim(),
        expectedDate: payload.expectedDate || null,
        notes: payload.notes?.trim() || null,
        items: payload.items.map((item) => ({ productId: Number(item.productId), quantity: Number(item.quantity), unitCost: Number(item.unitCost) })),
      })
      setSelected(data)
      setCreateOpen(false)
      setNotice('Draft purchase order created.')
      await loadOrders(data.id, 0)
      setPage(0)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Purchase order could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">PROCUREMENT WORKFLOW</div><h1>Purchase orders</h1><p>Create orders, route them for approval and receive stock against ordered quantities.</p></div>
        {canPurchase && <button className="primary-button" onClick={() => setCreateOpen(true)}><Plus size={17} /> New purchase order</button>}
      </div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <section className="panel">
        <div className="toolbar"><div className="toolbar-title"><ClipboardList size={18} /><strong>Purchase order history</strong><span>{total}</span></div></div>
        {loading ? <div className="inline-empty">Loading purchase orders…</div> : orders.length === 0 ? <div className="inline-empty">No purchase orders have been created yet.</div> : (
          <div className="table-wrap"><table><thead><tr><th>Order</th><th>Supplier</th><th>Warehouse</th><th>Expected</th><th>Total</th><th>Status</th><th></th></tr></thead>
            <tbody>{orders.map((order) => <tr key={order.id}>
              <td><strong>{order.orderNumber}</strong><small>{new Date(order.createdAt).toLocaleDateString()}</small></td>
              <td>{order.supplierName}</td><td>{order.warehouseName}</td>
              <td>{order.expectedDate ? new Date(order.expectedDate + 'T00:00:00').toLocaleDateString() : '—'}</td>
              <td>{money(order.total, order.currencyCode)}</td>
              <td><span className={'status-pill status-' + order.status.toLowerCase().replaceAll('_', '-')}>{statusLabels[order.status]}</span></td>
              <td><button className="secondary-button compact-button" onClick={() => setSelected(order)}>{selected?.id === order.id ? 'Open' : 'View'}</button></td>
            </tr>)}</tbody>
          </table></div>
        )}
        <div className="pagination-bar"><span>Page {page + 1} · {total} purchase orders</span><div><button className="secondary-button" disabled={page === 0 || loading} onClick={() => { const next = Math.max(0, page - 1); setPage(next); void loadOrders(undefined, next) }}>Previous</button><button className="secondary-button" disabled={(page + 1) * 50 >= total || loading} onClick={() => { const next = page + 1; setPage(next); void loadOrders(undefined, next) }}>Next</button></div></div>
      </section>
      {selected && <section className="panel purchase-detail">
        <div className="panel-heading"><div><div className="eyebrow">{selected.orderNumber}</div><h2>Order details</h2><p>{selected.supplierName} · {selected.warehouseName} · Created by {selected.createdBy}</p></div><span className={'status-pill status-' + selected.status.toLowerCase().replaceAll('_', '-')}>{statusLabels[selected.status]}</span></div>
        {selected.rejectionReason && <div className="notice notice-error purchase-notice">Rejected: {selected.rejectionReason}</div>}
        <div className="table-wrap"><table><thead><tr><th>Product</th><th>Ordered</th><th>Received</th><th>Outstanding</th><th>Unit cost</th><th>Line total</th></tr></thead>
          <tbody>{selected.items.map((item) => <tr key={item.id}><td><strong>{item.description}</strong><small>{item.sku}</small></td><td>{item.orderedQuantity}</td><td>{item.receivedQuantity}</td><td>{item.outstandingQuantity}</td><td>{money(item.unitCost, selected.currencyCode)}</td><td>{money(item.lineTotal, selected.currencyCode)}</td></tr>)}</tbody>
        </table></div>
        {selected.notes && <p className="purchase-notes">{selected.notes}</p>}
        <div className="purchase-actions">
          {selected.status === 'DRAFT' && canPurchase && <button className="primary-button" disabled={busy} onClick={() => void performAction('submit')}><Send size={15} /> Submit for approval</button>}
          {selected.status === 'PENDING_APPROVAL' && canApprove && <>
            <button className="primary-button" disabled={busy} onClick={() => void performAction('approve')}><Check size={15} /> Approve</button>
            <input aria-label="Rejection reason" placeholder="Reason if rejecting" maxLength={500} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} />
            <button className="secondary-button" disabled={busy || rejectReason.trim().length === 0} onClick={() => void performAction('reject')}>Reject</button>
          </>}
          {selected.status === 'APPROVED' && canPurchase && <button className="primary-button" disabled={busy} onClick={() => void performAction('send')}><Send size={15} /> Mark sent to supplier</button>}
          {['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'].includes(selected.status) && canApprove && <button className="secondary-button" disabled={busy} onClick={() => { if (window.confirm('Cancel this purchase order?')) void performAction('cancel') }}>Cancel order</button>}
          {['ORDERED', 'PARTIALLY_RECEIVED'].includes(selected.status) && canReceive && <button className="primary-button" disabled={busy} onClick={() => setReceiveOpen(true)}><PackageCheck size={16} /> Receive goods</button>}
        </div>
      </section>}
      {createOpen && <CreatePurchaseOrderForm suppliers={suppliers} warehouses={warehouses} products={products} busy={busy} onClose={() => setCreateOpen(false)} onCreate={createOrder} />}
      {receiveOpen && selected && <ReceiveGoodsForm order={selected} busy={busy} onClose={() => setReceiveOpen(false)} onReceive={receiveGoods} />}
    </>
  )
}

function CreatePurchaseOrderForm({ suppliers, warehouses, products, busy, onClose, onCreate }: {
  suppliers: Supplier[]; warehouses: Warehouse[]; products: Product[]; busy: boolean; onClose: () => void; onCreate: (payload: CreateValues) => Promise<void>
}) {
  const [formError, setFormError] = useState('')
  const [lines, setLines] = useState<CreateValues['items']>([{ productId: '', quantity: '1', unitCost: '0.00' }])
  const { register, handleSubmit, formState: { errors } } = useForm<PurchaseHeaderValues>({
    resolver: zodResolver(purchaseHeaderSchema),
    defaultValues: { supplierId: '', warehouseId: '', currencyCode: 'PHP', expectedDate: '', notes: '' },
  })
  async function submit(values: PurchaseHeaderValues) {
    setFormError('')
    const result = createSchema.safeParse({ ...values, items: lines })
    if (!result.success) {
      setFormError(result.error.issues[0]?.message ?? 'Check the order details.')
      return
    }
    await onCreate(result.data)
  }

  function setLine(index: number, field: 'productId' | 'quantity' | 'unitCost', value: string) {
    setLines((current) => current.map((line, row) => row === index ? { ...line, [field]: value } : line))
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="product-modal purchase-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-form-title">
        <div className="modal-heading"><div><div className="eyebrow">PROCUREMENT</div><h2 id="purchase-form-title">Create purchase order</h2></div><button className="icon-button" aria-label="Close" onClick={onClose}><X size={18} /></button></div>
        <form onSubmit={handleSubmit(submit)} className="product-form">
          <div className="form-grid">
            <label>Supplier<select aria-label="Supplier" {...register('supplierId')}><option value="">Select a supplier</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name} · {supplier.code}</option>)}</select>{errors.supplierId && <small className="field-error">{errors.supplierId.message}</small>}</label>
            <label>Deliver to<select aria-label="Warehouse" {...register('warehouseId')}><option value="">Select a warehouse</option>{warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} · {warehouse.code}</option>)}</select>{errors.warehouseId && <small className="field-error">{errors.warehouseId.message}</small>}</label>
            <label>Currency<input aria-label="Currency code" maxLength={3} {...register('currencyCode')} /></label>
            <label>Expected delivery date<input aria-label="Expected delivery date" type="date" {...register('expectedDate')} /></label>
          </div>
          <div className="purchase-line-editor"><div className="purchase-line-heading"><strong>Order lines</strong><button type="button" className="secondary-button" onClick={() => setLines((current) => [...current, { productId: '', quantity: '1', unitCost: '0.00' }])}><Plus size={14} /> Add line</button></div>
            {lines.map((line, index) => <div className="purchase-line" key={index}>
              <label>Product<select aria-label={'Product ' + (index + 1)} value={line.productId} onChange={(event) => {
                const product = products.find((item) => String(item.id) === event.target.value)
                setLine(index, 'productId', event.target.value)
                if (product) setLine(index, 'unitCost', String(product.costPrice))
              }}><option value="">Select a product</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>)}</select></label>
              <label>Quantity<input aria-label={'Quantity ' + (index + 1)} type="number" min="0.001" step="0.001" value={line.quantity} onChange={(event) => setLine(index, 'quantity', event.target.value)} /></label>
              <label>Unit cost<input aria-label={'Unit cost ' + (index + 1)} type="number" min="0" step="0.0001" value={line.unitCost} onChange={(event) => setLine(index, 'unitCost', event.target.value)} /></label>
              <button type="button" className="icon-button" aria-label={'Remove line ' + (index + 1)} disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, row) => row !== index))}><X size={16} /></button>
            </div>)}
          </div>
          <label>Order notes<textarea rows={3} {...register('notes')} /></label>
          {formError && <div className="notice notice-error">{formError}</div>}
          {(suppliers.length === 0 || warehouses.length === 0 || products.length === 0) && <div className="notice notice-error">Add an active supplier, warehouse and product before creating an order.</div>}
          <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Close</button><button className="primary-button" disabled={busy || suppliers.length === 0 || warehouses.length === 0 || products.length === 0}>{busy ? 'Creating…' : 'Create draft order'}</button></div>
        </form>
      </section>
    </div>
  )
}

function ReceiveGoodsForm({ order, busy, onClose, onReceive }: {
  order: PurchaseOrder; busy: boolean; onClose: () => void; onReceive: (payload: { idempotencyKey: string; deliveryReference: string | null; notes: string | null; items: { purchaseOrderItemId: number; quantity: number }[] }) => Promise<void>
}) {
  const [key] = useState(() => crypto.randomUUID())
  const [deliveryReference, setDeliveryReference] = useState('')
  const [notes, setNotes] = useState('')
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [error, setError] = useState('')
  const outstanding = order.items.filter((item) => item.outstandingQuantity > 0)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const items = outstanding.flatMap((item) => {
      const quantity = Number(quantities[item.id] || 0)
      return quantity > 0 ? [{ purchaseOrderItemId: item.id, quantity }] : []
    })
    if (items.length === 0) { setError('Enter a quantity for at least one line.'); return }
    if (items.some((item) => item.quantity > (order.items.find((line) => line.id === item.purchaseOrderItemId)?.outstandingQuantity ?? 0))) {
      setError('A received quantity cannot exceed the outstanding amount.')
      return
    }
    setError('')
    void onReceive({ idempotencyKey: key, deliveryReference: deliveryReference.trim() || null, notes: notes.trim() || null, items })
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="product-modal purchase-modal" role="dialog" aria-modal="true" aria-labelledby="receive-form-title">
        <div className="modal-heading"><div><div className="eyebrow">{order.orderNumber}</div><h2 id="receive-form-title">Receive goods</h2></div><button className="icon-button" aria-label="Close" onClick={onClose}><X size={18} /></button></div>
        <form onSubmit={submit} className="product-form">
          <label>Delivery reference <span className="optional-label">Optional</span><input maxLength={100} value={deliveryReference} onChange={(event) => setDeliveryReference(event.target.value)} /></label>
          <div className="receipt-line-list"><div className="receipt-line receipt-line-header"><span>Product</span><span>Outstanding</span><span>Receive now</span></div>
            {outstanding.map((item) => <div className="receipt-line" key={item.id}><span><strong>{item.description}</strong><small>{item.sku}</small></span><span>{item.outstandingQuantity}</span><input aria-label={'Receive quantity for ' + item.sku} type="number" min="0" max={item.outstandingQuantity} step="0.001" value={quantities[item.id] ?? ''} onChange={(event) => setQuantities((current) => ({ ...current, [item.id]: event.target.value }))} /></div>)}
          </div>
          <label>Receipt notes <span className="optional-label">Optional</span><textarea rows={3} maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} /></label>
          <p className="idempotency-hint">This receipt uses a unique request key, so retrying this form after a connection error won’t add the same stock twice.</p>
          {error && <div className="notice notice-error">{error}</div>}
          <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Close</button><button className="primary-button" disabled={busy}><PackageCheck size={15} />{busy ? 'Recording…' : 'Record receipt'}</button></div>
        </form>
      </section>
    </div>
  )
}
