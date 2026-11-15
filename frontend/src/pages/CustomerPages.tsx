import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowRight, BadgeCheck, Box, CircleUserRound, ClipboardList, LogOut, MapPin, PackageCheck, ShoppingBag, Store, Trash2 } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './customer.css'
import './customer-order-history.css'

interface PageResult<T> {
  content: T[]
  totalElements: number
}

interface CartLine {
  id: number
  productId: number
  sku: string
  name: string
  unitOfMeasure: string
  quantity: number
  unitPrice: number
  lineTotal: number
  active: boolean
}

interface Cart {
  items: CartLine[]
  subtotal: number
  totalQuantity: number
}

interface Address {
  id: number
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string | null
  city: string
  region: string | null
  postalCode: string | null
  countryCode: string
  defaultAddress: boolean
  createdAt: string
}

interface AddressInput {
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string
  city: string
  region: string
  postalCode: string
  countryCode: string
  defaultAddress: boolean
}

interface PublicWarehouse {
  id: number
  code: string
  name: string
}

type FulfillmentType = 'DELIVERY' | 'STORE_PICKUP'
type PaymentMethod = 'CASH_ON_DELIVERY' | 'MANUAL_TRANSFER'
type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'READY_FOR_PICKUP' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURN_REQUESTED' | 'RETURNED' | 'REFUNDED'
type PaymentStatus = 'UNPAID' | 'AWAITING_VERIFICATION' | 'PAID' | 'PARTIALLY_REFUNDED' | 'FAILED' | 'REFUNDED' | 'CANCELLED'

interface OrderItem {
  id: number
  lineNumber: number
  productId: number
  sku: string
  productName: string
  unitOfMeasure: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

interface CustomerOrder {
  id: number
  orderNumber: string
  warehouseId: number
  warehouseName: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  currencyCode: string
  subtotal: number
  taxAmount: number
  shippingAmount: number
  discountAmount: number
  total: number
  addressLabel: string
  recipientName: string
  recipientPhone: string
  addressLine1: string
  addressLine2: string | null
  city: string
  region: string | null
  postalCode: string | null
  countryCode: string
  notes: string | null
  cancelledAt: string | null
  createdAt: string
  items: OrderItem[]
}

interface OrderStatusHistory {
  id: number
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  changedBy: string
  note: string | null
  occurredAt: string
}

function money(value: number, currency = 'PHP') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
}

function friendly(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function CustomerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="customer-shell">
      <header className="customer-header">
        <Link className="brand-lockup" to="/" aria-label="Northstar Supply home">
          <span className="brand-mark"><Store size={20} /></span>
          <span><strong>Northstar</strong><small>SUPPLY CO.</small></span>
        </Link>
        <nav className="customer-nav" aria-label="Customer account navigation">
          <NavLink to="/">Shop</NavLink>
          <NavLink to="/cart">Cart</NavLink>
          <NavLink to="/account/addresses">Addresses</NavLink>
          <NavLink to="/account/orders">Orders</NavLink>
          <NavLink to="/account/returns">Returns</NavLink>
          <NavLink to="/account/support">Support</NavLink>
        </nav>
        <div className="customer-user-menu">
          <span className="customer-avatar"><CircleUserRound size={17} /></span>
          <span><strong>{user?.fullName}</strong><small>CUSTOMER ACCOUNT</small></span>
          <button className="secondary-button customer-signout" onClick={() => void signOut()}><LogOut size={15} /> Sign out</button>
        </div>
      </header>
      <main className="customer-main"><Outlet /></main>
      <footer className="customer-footer"><span>Northstar Supply Co.</span><span>Order totals and availability are confirmed by the store service.</span><Link to="/admin/login">Staff workspace</Link></footer>
    </div>
  )
}

export function CustomerCartPage() {
  const [cart, setCart] = useState<Cart | null>(null)
  const [addresses, setAddresses] = useState<Address[]>([])
  const [warehouses, setWarehouses] = useState<PublicWarehouse[]>([])
  const [selectedAddress, setSelectedAddress] = useState('')
  const [selectedWarehouse, setSelectedWarehouse] = useState('')
  const [fulfillmentType, setFulfillmentType] = useState<FulfillmentType>('DELIVERY')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH_ON_DELIVERY')
  const [notes, setNotes] = useState('')
  const [drafts, setDrafts] = useState<Record<number, string>>({})
  const [order, setOrder] = useState<CustomerOrder | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  async function load() {
    try {
      const [cartResult, addressesResult, warehousesResult] = await Promise.all([
        api.get<Cart>('/customer/cart'),
        api.get<Address[]>('/customer/addresses'),
        api.get<PublicWarehouse[]>('/catalog/warehouses'),
      ])
      setCart(cartResult.data)
      setAddresses(addressesResult.data)
      setWarehouses(warehousesResult.data)
      setError('')
      setSelectedAddress((current) => current || String(addressesResult.data.find((address) => address.defaultAddress)?.id ?? addressesResult.data[0]?.id ?? ''))
      setSelectedWarehouse((current) => current || String(warehousesResult.data[0]?.id ?? ''))
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Your cart could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true
    void Promise.resolve().then(() => { if (mounted) return load() })
    return () => { mounted = false }
  }, [])

  async function updateItem(line: CartLine, quantity: number) {
    setError('')
    try {
      if (quantity <= 0) {
        const { data } = await api.delete<Cart>('/customer/cart/items/' + line.id)
        setCart(data)
      } else {
        const { data } = await api.put<Cart>('/customer/cart/items/' + line.id, { quantity })
        setCart(data)
      }
      setDrafts((current) => { const next = { ...current }; delete next[line.id]; return next })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'The cart could not be updated.'))
      setDrafts((current) => { const next = { ...current }; delete next[line.id]; return next })
    }
  }

  function pendingCheckoutKey(payload: Omit<CustomerCheckoutRequest, 'idempotencyKey'>) {
    const storageKey = 'ims.customer.checkout.pending'
    try {
      const previous = sessionStorage.getItem(storageKey)
      if (previous) {
        const saved = JSON.parse(previous) as CustomerCheckoutRequest
        if (saved.addressId === payload.addressId && saved.warehouseId === payload.warehouseId
          && saved.fulfillmentType === payload.fulfillmentType
          && saved.paymentMethod === payload.paymentMethod && saved.notes === payload.notes) {
          return saved.idempotencyKey
        }
      }
      const idempotencyKey = crypto.randomUUID()
      sessionStorage.setItem(storageKey, JSON.stringify({ ...payload, idempotencyKey }))
      return idempotencyKey
    } catch {
      return crypto.randomUUID()
    }
  }

  async function checkout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if ((fulfillmentType === 'DELIVERY' && !selectedAddress) || !selectedWarehouse) return
    setSubmitting(true)
    setError('')
    const checkoutDetails: Omit<CustomerCheckoutRequest, 'idempotencyKey'> = {
      addressId: selectedAddress ? Number(selectedAddress) : null,
      warehouseId: Number(selectedWarehouse),
      paymentMethod,
      notes: notes.trim() || null,
      ...(fulfillmentType === 'STORE_PICKUP' ? { fulfillmentType } : {}),
    }
    const payload = { ...checkoutDetails, idempotencyKey: pendingCheckoutKey(checkoutDetails) }
    try {
      const { data } = await api.post<CustomerOrder>('/customer/checkout', payload)
      setOrder(data)
      sessionStorage.removeItem('ims.customer.checkout.pending')
      setCart({ items: [], subtotal: 0, totalQuantity: 0 })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Checkout could not be completed. Your cart is still saved.'))
    } finally {
      setSubmitting(false)
    }
  }

  const hasProducts = Boolean(cart?.items.length)

  if (loading) return <div className="customer-state">Loading your cart…</div>
  if (order) {
    return (
      <section className="order-success-card">
        <span className="order-success-icon"><BadgeCheck size={27} /></span>
        <div className="eyebrow">ORDER PLACED</div>
        <h1>Thank you. We have your order.</h1>
        <p>Order <strong>{order.orderNumber}</strong> is {friendly(order.status)}. The order total was calculated by the store service.</p>
        <div className="order-success-total"><span>Order total</span><strong>{money(order.total, order.currencyCode)}</strong></div>
        <div className="payment-callout"><strong>{friendly(order.paymentStatus)}</strong><span>{paymentText(order.paymentMethod, order.paymentStatus)}</span></div>
        <div className="customer-page-actions"><Link to="/account/orders" className="primary-button">View my orders <ArrowRight size={16} /></Link><Link to="/" className="secondary-button">Continue shopping</Link></div>
      </section>
    )
  }

  return (
    <section className="customer-page">
      <div className="page-title-row customer-title-row">
        <div><div className="eyebrow">YOUR SHOPPING BAG</div><h1>Cart and checkout</h1><p>Prices are refreshed at checkout. Stock is reserved only when an order is created.</p></div>
        <Link to="/" className="secondary-button"><Store size={15} /> Browse catalog</Link>
      </div>
      {error && <div className="notice notice-error customer-notice" role="alert">{error}<button onClick={() => void load()}>Refresh</button></div>}
      {!hasProducts ? (
        <section className="customer-empty panel"><span className="empty-bag"><ShoppingBag size={25} /></span><h2>Your cart is empty</h2><p>Browse the catalog to add products to your order.</p><Link to="/" className="primary-button">Explore products <ArrowRight size={15} /></Link></section>
      ) : (
        <div className="cart-layout">
          <section className="panel cart-items-panel">
            <div className="panel-heading"><div><h2>Items in your cart</h2><p>{cart?.items.length} product{cart?.items.length === 1 ? '' : 's'}</p></div></div>
            <div className="cart-line-list">
              {cart?.items.map((line) => (
                <article className="cart-line" key={line.id}>
                  <div className="cart-product-icon"><Box size={20} /></div>
                  <div className="cart-product-info"><strong>{line.name}</strong><span>SKU {line.sku} · {line.unitOfMeasure}{!line.active && <em>Unavailable</em>}</span></div>
                  <div className="cart-quantity"><label htmlFor={'qty-' + line.id}>Qty</label><input id={'qty-' + line.id} aria-label={'Quantity for ' + line.name} type="number" min="0.001" step="0.001" value={drafts[line.id] ?? String(line.quantity)} onChange={(event) => setDrafts((current) => ({ ...current, [line.id]: event.target.value }))} onBlur={() => { const value = Number(drafts[line.id]); if (drafts[line.id] !== undefined && Number.isFinite(value) && value > 0 && value !== line.quantity) void updateItem(line, value) }} /></div>
                  <div className="cart-line-total"><small>Line total</small><strong>{money(line.lineTotal)}</strong></div>
                  <button className="icon-button cart-remove" aria-label={'Remove ' + line.name} onClick={() => void updateItem(line, 0)}><Trash2 size={16} /></button>
                </article>
              ))}
            </div>
            <div className="cart-subtotal-row"><span>Cart subtotal estimate</span><strong>{money(cart?.subtotal ?? 0)}</strong></div>
            <p className="cart-price-note">Final prices, tax, and delivery charges are confirmed by the server when you place your order.</p>
          </section>

          <form className="panel checkout-panel" onSubmit={(event) => void checkout(event)}>
            <div className="panel-heading"><div><h2>Delivery and payment</h2><p>Choose where and how this order is fulfilled.</p></div></div>
            <div className="checkout-fields">
              <label>Fulfillment method<select value={fulfillmentType} onChange={(event) => setFulfillmentType(event.target.value as FulfillmentType)}><option value="DELIVERY">Home delivery</option><option value="STORE_PICKUP">Store pickup (no shipping fee)</option></select></label>
              {fulfillmentType === 'DELIVERY' && (addresses.length ? (
                <label>Delivery address<select required value={selectedAddress} onChange={(event) => setSelectedAddress(event.target.value)}>{addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.addressLine1}, {address.city}</option>)}</select></label>
              ) : (
                <div className="checkout-empty-hint"><MapPin size={17} /><span>Add a delivery address before checkout, or choose Store pickup.</span><Link to="/account/addresses">Manage addresses</Link></div>
              ))}
              {warehouses.length ? (
                <label>Fulfillment location<select required value={selectedWarehouse} onChange={(event) => setSelectedWarehouse(event.target.value)}>{warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>)}</select></label>
              ) : <div className="notice notice-error">No active fulfillment locations are available.</div>}
              <label>Payment method<select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}><option value="CASH_ON_DELIVERY">{fulfillmentType === 'STORE_PICKUP' ? 'Pay at pickup (Cash)' : 'Cash on delivery'}</option><option value="MANUAL_TRANSFER">Manual bank transfer</option></select></label>
              <label>Order notes <span className="optional-label">Optional</span><textarea rows={3} maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Delivery or pickup instructions" /></label>
              <div className="checkout-total-row"><span>Current cart subtotal</span><strong>{money(cart?.subtotal ?? 0)}</strong></div>
              <p className="checkout-payment-note">{paymentMethod === 'MANUAL_TRANSFER' ? 'Transfer instructions will need to be confirmed by the store. This order will not be marked paid automatically.' : fulfillmentType === 'STORE_PICKUP' ? 'Payment will be collected when you pick up your order at the selected warehouse.' : 'Cash will be collected when your order is delivered.'}</p>
              <button className="primary-button full-button" type="submit" disabled={submitting || !hasProducts || (fulfillmentType === 'DELIVERY' && addresses.length === 0) || warehouses.length === 0}>
                {submitting ? 'Placing order…' : 'Place order'}
              </button>
              <small className="checkout-stock-note">Available stock is checked again and reserved during checkout.</small>
            </div>
          </form>
        </div>
      )}
    </section>
  )
}

interface CustomerCheckoutRequest {
  addressId: number | null
  warehouseId: number
  fulfillmentType?: FulfillmentType
  paymentMethod: PaymentMethod
  notes: string | null
  idempotencyKey: string
}

function emptyAddress(): AddressInput {
  return { label: '', recipientName: '', phone: '', addressLine1: '', addressLine2: '', city: '', region: '', postalCode: '', countryCode: 'PH', defaultAddress: false }
}

const addressFields: Array<{ key: keyof AddressInput; label: string; required?: boolean; maxLength?: number }> = [
  { key: 'label', label: 'Address label', required: true, maxLength: 80 },
  { key: 'recipientName', label: 'Recipient name', required: true, maxLength: 160 },
  { key: 'phone', label: 'Contact phone', required: true, maxLength: 40 },
  { key: 'addressLine1', label: 'Address line 1', required: true, maxLength: 180 },
  { key: 'addressLine2', label: 'Address line 2', maxLength: 180 },
  { key: 'city', label: 'City or municipality', required: true, maxLength: 100 },
  { key: 'region', label: 'Region or province', maxLength: 100 },
  { key: 'postalCode', label: 'Postal code', maxLength: 24 },
  { key: 'countryCode', label: 'Country code', required: true, maxLength: 2 },
]

export function CustomerAddressesPage() {
  const [addresses, setAddresses] = useState<Address[]>([])
  const [form, setForm] = useState<AddressInput>(emptyAddress)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  async function load() {
    setError('')
    try {
      const { data } = await api.get<Address[]>('/customer/addresses')
      setAddresses(data)
      setError('')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Your delivery addresses could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true
    void Promise.resolve().then(() => { if (mounted) return load() })
    return () => { mounted = false }
  }, [])

  function edit(address: Address) {
    setEditingId(address.id)
    setForm({
      label: address.label, recipientName: address.recipientName, phone: address.phone,
      addressLine1: address.addressLine1, addressLine2: address.addressLine2 ?? '',
      city: address.city, region: address.region ?? '', postalCode: address.postalCode ?? '',
      countryCode: address.countryCode, defaultAddress: address.defaultAddress,
    })
  }

  function reset() {
    setEditingId(null)
    setForm(emptyAddress())
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = { ...form, label: form.label.trim(), recipientName: form.recipientName.trim(), phone: form.phone.trim(), addressLine1: form.addressLine1.trim(), addressLine2: form.addressLine2.trim() || null, city: form.city.trim(), region: form.region.trim() || null, postalCode: form.postalCode.trim() || null, countryCode: form.countryCode.trim().toUpperCase() }
      if (editingId) await api.put('/customer/addresses/' + editingId, payload)
      else await api.post('/customer/addresses', payload)
      reset()
      await load()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'The address could not be saved.'))
    } finally {
      setSaving(false)
    }
  }

  async function remove(address: Address) {
    if (!window.confirm('Remove the “' + address.label + '” delivery address?')) return
    try {
      await api.delete('/customer/addresses/' + address.id)
      await load()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'The address could not be removed.'))
    }
  }

  return (
    <section className="customer-page">
      <div className="page-title-row customer-title-row"><div><div className="eyebrow">DELIVERY DETAILS</div><h1>Saved addresses</h1><p>Keep up to ten addresses for faster checkout.</p></div><Link to="/cart" className="secondary-button"><ShoppingBag size={15} /> Back to cart</Link></div>
      {error && <div className="notice notice-error customer-notice" role="alert">{error}<button onClick={() => void load()}>Refresh</button></div>}
      <div className="address-layout">
        <section className="panel address-list-panel">
          <div className="panel-heading"><div><h2>Your delivery addresses</h2><p>{addresses.length} of 10 saved</p></div></div>
          {loading ? <div className="inline-empty">Loading addresses…</div> : addresses.length ? (
            <div className="address-card-list">{addresses.map((address) => (
              <article className="address-card" key={address.id}>
                <div className="address-card-icon"><MapPin size={18} /></div>
                <div className="address-card-info"><div><strong>{address.label}</strong>{address.defaultAddress && <span className="status-pill status-live">Default</span>}</div><span>{address.recipientName} · {address.phone}</span><span>{address.addressLine1}{address.addressLine2 ? ', ' + address.addressLine2 : ''}</span><span>{[address.city, address.region, address.postalCode, address.countryCode].filter(Boolean).join(', ')}</span></div>
                <div className="address-card-actions"><button className="secondary-button" onClick={() => edit(address)}>Edit</button><button className="icon-button" aria-label={'Remove ' + address.label} onClick={() => void remove(address)}><Trash2 size={15} /></button></div>
              </article>
            ))}</div>
          ) : <div className="inline-empty">No delivery addresses yet. Add one to check out.</div>}
        </section>
        <form className="panel address-form-panel" onSubmit={(event) => void save(event)}>
          <div className="panel-heading"><div><h2>{editingId ? 'Edit address' : 'Add an address'}</h2><p>Only you can view and manage your saved addresses.</p></div></div>
          <div className="address-form-fields">
            {addressFields.map(({ key, label, required, maxLength }) => <label key={key}>{label}<input required={required} maxLength={maxLength} value={String(form[key])} onChange={(event) => setForm((current) => ({ ...current, [key]: event.target.value }))} /></label>)}
            <label className="checkbox-row"><input type="checkbox" checked={form.defaultAddress} onChange={(event) => setForm((current) => ({ ...current, defaultAddress: event.target.checked }))} /> Set as default address</label>
            <div className="address-form-actions"><button className="primary-button" type="submit" disabled={saving || (addresses.length >= 10 && !editingId)}>{saving ? 'Saving…' : editingId ? 'Save changes' : 'Save address'}</button>{editingId && <button className="secondary-button" type="button" onClick={reset}>Cancel edit</button>}</div>
            {addresses.length >= 10 && !editingId && <small className="checkout-stock-note">Remove an address before adding another.</small>}
          </div>
        </form>
      </div>
    </section>
  )
}

export function CustomerOrdersPage() {
  const [orders, setOrders] = useState<CustomerOrder[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<number | null>(null)
  const navigate = useNavigate()

  async function load() {
    setError('')
    try {
      const { data } = await api.get<PageResult<CustomerOrder>>('/customer/orders', { params: { page: 0, size: 50 } })
      setOrders(data.content)
      setError('')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Your order history could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true
    void Promise.resolve().then(() => { if (mounted) return load() })
    return () => { mounted = false }
  }, [])

  async function cancel(order: CustomerOrder) {
    if (!window.confirm('Cancel order ' + order.orderNumber + '? Reserved stock will be released.')) return
    setWorkingId(order.id)
    setError('')
    try {
      const { data } = await api.post<CustomerOrder>('/customer/orders/' + order.id + '/cancel', {})
      setOrders((current) => current.map((item) => item.id === data.id ? data : item))
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'This order could not be cancelled.'))
    } finally {
      setWorkingId(null)
    }
  }

  const itemCount = useMemo(() => orders.reduce((count, order) => count + order.items.length, 0), [orders])

  return (
    <section className="customer-page">
      <div className="page-title-row customer-title-row"><div><div className="eyebrow">PURCHASE HISTORY</div><h1>Your orders</h1><p>{orders.length} order{orders.length === 1 ? '' : 's'} · {itemCount} line items</p></div><Link to="/" className="primary-button"><Store size={15} /> Shop catalog</Link></div>
      {error && <div className="notice notice-error customer-notice" role="alert">{error}<button onClick={() => void load()}>Refresh</button></div>}
      {loading ? <div className="customer-state">Loading your orders…</div> : orders.length ? (
        <div className="customer-order-list">{orders.map((order) => (
          <article className="panel customer-order-card" key={order.id}>
            <header className="customer-order-header"><div><span className="eyebrow">{new Date(order.createdAt).toLocaleDateString()}</span><h2>{order.orderNumber}</h2><span className="order-location"><PackageCheck size={14} /> {order.warehouseName}</span></div><div className="order-status-group"><span className={'status-pill ' + (order.status === 'CANCELLED' ? 'status-muted' : 'status-live')}>{friendly(order.status)}</span><span className="status-pill status-neutral">{friendly(order.paymentStatus)}</span></div></header>
            <div className="customer-order-lines">{order.items.map((item) => <div className="customer-order-line" key={item.id}><span><strong>{item.productName}</strong><small>SKU {item.sku} · {item.quantity} {item.unitOfMeasure} × {money(item.unitPrice, order.currencyCode)}</small></span><strong>{money(item.lineTotal, order.currencyCode)}</strong></div>)}</div>
            <div className="customer-order-footer"><span>{order.addressLabel} · {order.city}</span><strong>{money(order.total, order.currencyCode)}</strong><div className="customer-order-actions">{order.status === 'PENDING' && <button className="secondary-button" disabled={workingId === order.id} onClick={() => void cancel(order)}>{workingId === order.id ? 'Cancelling…' : 'Cancel order'}</button>}<button className="secondary-button" onClick={() => navigate('/account/orders/' + order.id)}>Order details <ArrowRight size={14} /></button></div></div>
          </article>
        ))}</div>
      ) : <section className="customer-empty panel"><span className="empty-bag"><ClipboardList size={25} /></span><h2>No orders yet</h2><p>Your completed checkouts will appear here.</p><Link to="/" className="primary-button">Browse the catalog <ArrowRight size={15} /></Link></section>}
    </section>
  )
}

export function CustomerOrderDetailPage() {
  const [order, setOrder] = useState<CustomerOrder | null>(null)
  const [history, setHistory] = useState<OrderStatusHistory[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const { id: orderId } = useParams()

  useEffect(() => {
    let mounted = true
    Promise.all([
      api.get<CustomerOrder>('/customer/orders/' + orderId),
      api.get<OrderStatusHistory[]>('/customer/orders/' + orderId + '/history'),
    ])
      .then(([orderResult, historyResult]) => {
        if (mounted) {
          setOrder(orderResult.data)
          setHistory(historyResult.data)
        }
      })
      .catch((requestError: unknown) => { if (mounted) setError(getErrorMessage(requestError, 'The order could not be loaded.')) })
      .finally(() => { if (mounted) setLoading(false) })
    return () => { mounted = false }
  }, [orderId])

  if (loading) return <div className="customer-state">Loading order details…</div>
  if (error || !order) return <section className="customer-page"><div className="notice notice-error customer-notice">{error || 'Order not found.'}</div><Link to="/account/orders" className="secondary-button">Back to orders</Link></section>
  return <><p><Link to="/account/returns">Request or view a return</Link></p><OrderDetails order={order} history={history} /></>
}

function OrderDetails({ order, history }: { order: CustomerOrder; history: OrderStatusHistory[] }) {
  const breakdown: Array<[string, number]> = [['Subtotal', order.subtotal], ['Tax', order.taxAmount], ['Delivery', order.shippingAmount], ['Discount', -order.discountAmount]]
  return (
    <section className="customer-page">
      <div className="page-title-row customer-title-row"><div><div className="eyebrow">ORDER DETAILS</div><h1>{order.orderNumber}</h1><p>Placed {new Date(order.createdAt).toLocaleString()}</p></div><Link to="/account/orders" className="secondary-button"><ClipboardList size={15} /> All orders</Link></div>
      <div className="order-detail-layout">
        <section className="panel order-detail-panel"><div className="panel-heading"><div><h2>Items</h2><p>{friendly(order.status)} · {friendly(order.paymentStatus)}</p></div></div><div className="customer-order-lines">{order.items.map((item) => <div className="customer-order-line" key={item.id}><span><strong>{item.productName}</strong><small>SKU {item.sku} · {item.quantity} {item.unitOfMeasure} × {money(item.unitPrice, order.currencyCode)}</small></span><strong>{money(item.lineTotal, order.currencyCode)}</strong></div>)}</div><div className="order-breakdown">{breakdown.map(([label, value]) => <div key={label}><span>{label}</span><span>{money(value, order.currencyCode)}</span></div>)}<div className="order-grand-total"><strong>Total</strong><strong>{money(order.total, order.currencyCode)}</strong></div></div></section>
        <div className="order-detail-side"><section className="panel order-address-panel"><div className="panel-heading"><div><h2>Delivery address</h2><p>{order.addressLabel}</p></div></div><div className="order-address-copy"><strong>{order.recipientName}</strong><span>{order.recipientPhone}</span><span>{order.addressLine1}{order.addressLine2 ? ', ' + order.addressLine2 : ''}</span><span>{[order.city, order.region, order.postalCode, order.countryCode].filter(Boolean).join(', ')}</span></div></section><section className="panel order-address-panel"><div className="panel-heading"><div><h2>Payment and fulfillment</h2><p>{friendly(order.paymentMethod)}</p></div></div><div className="order-address-copy"><strong>{order.warehouseName}</strong><span>{paymentText(order.paymentMethod, order.paymentStatus)}</span>{order.notes && <span>Note: {order.notes}</span>}</div></section><section className="panel order-address-panel customer-order-history-panel"><div className="panel-heading"><div><h2>Order progress</h2><p>Status updates from the store</p></div></div><ol className="customer-order-timeline">{history.map((event) => <li key={event.id}><span className="customer-history-dot" /><div><strong>{event.fromStatus ? friendly(event.toStatus) : 'Order placed'}</strong><small>{new Date(event.occurredAt).toLocaleString()}</small>{event.note && <p>{event.note}</p>}</div></li>)}</ol></section></div>
      </div>
    </section>
  )
}

function paymentText(method: PaymentMethod, status: PaymentStatus) {
  if (status === 'PAID') return 'Payment has been verified by the store.'
  if (status === 'AWAITING_VERIFICATION') return 'Manual transfer is awaiting store verification. This order is not marked paid.'
  if (status === 'CANCELLED') return 'The order was cancelled and no payment is due.'
  if (method === 'CASH_ON_DELIVERY') return 'Payment is due when the order is delivered.'
  return 'Payment has not been confirmed.'
}

export function CustomerAccountHome() {
  const [notifications, setNotifications] = useState<Array<{ id: number; title: string; message: string; read: boolean; createdAt: string }>>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [pwMessage, setPwMessage] = useState('')
  const [pwError, setPwError] = useState(false)

  useEffect(() => {
    let active = true
    api.get<{ unreadCount: number; items: Array<{ id: number; title: string; message: string; read: boolean; createdAt: string }> }>('/notifications')
      .then(({ data }) => {
        if (active) {
          setNotifications(data.items)
          setUnreadCount(data.unreadCount)
        }
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [])

  async function markAllRead() {
    try {
      await api.post('/notifications/read-all')
      setUnreadCount(0)
      setNotifications((curr) => curr.map((item) => ({ ...item, read: true })))
    } catch {
      // ignore
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPwMessage('')
    setPwError(false)
    try {
      await api.post('/auth/change-password', { currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setPwMessage('Password updated and other sessions revoked.')
    } catch (error) {
      setPwError(true)
      setPwMessage(getErrorMessage(error, 'Password could not be updated.'))
    }
  }

  return (
    <div className="customer-page">
      <div className="page-title-row customer-title-row"><div><div className="eyebrow">CUSTOMER ACCOUNT</div><h1>Your account</h1><p>Manage your orders, delivery addresses, notifications, and support requests.</p></div></div>
      <div className="account-shortcuts">
        <Link to="/account/orders" className="panel account-shortcut"><ClipboardList size={22} /><strong>Order history</strong><span>Review status, payment, and delivery details.</span><ArrowRight size={16} /></Link>
        <Link to="/account/addresses" className="panel account-shortcut"><MapPin size={22} /><strong>Saved addresses</strong><span>Update your delivery information.</span><ArrowRight size={16} /></Link>
        <Link to="/cart" className="panel account-shortcut"><ShoppingBag size={22} /><strong>Your cart</strong><span>Review items and place an order.</span><ArrowRight size={16} /></Link>
        <Link to="/account/support" className="panel account-shortcut"><PackageCheck size={22} /><strong>Customer support</strong><span>Open or reply to support tickets.</span><ArrowRight size={16} /></Link>
      </div>
      <div className="address-layout" style={{ marginTop: '1.5rem' }}>
        <section className="panel address-list-panel">
          <div className="panel-heading">
            <div><h2>Notifications</h2><p>{unreadCount} unread</p></div>
            {unreadCount > 0 && <button className="secondary-button" onClick={() => void markAllRead()}>Mark all read</button>}
          </div>
          {notifications.length === 0 ? <div className="inline-empty">No notifications yet.</div> : (
            <div className="address-card-list">
              {notifications.map((n) => (
                <article className="address-card" key={n.id}>
                  <div className="address-card-info">
                    <div><strong>{n.title}</strong>{!n.read && <span className="status-pill status-live">New</span>}</div>
                    <span>{n.message}</span>
                    <small>{new Date(n.createdAt).toLocaleString()}</small>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
        <form className="panel address-form-panel" onSubmit={(event) => void changePassword(event)}>
          <div className="panel-heading"><div><h2>Change password</h2><p>Updating your password revokes other active sessions.</p></div></div>
          {pwMessage && <div className={'notice ' + (pwError ? 'notice-error' : 'notice-success')}>{pwMessage}</div>}
          <div className="address-form-fields">
            <label>Current password<input type="password" required minLength={8} value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} /></label>
            <label>New password<input type="password" required minLength={10} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="At least 10 characters" /></label>
            <button className="primary-button" type="submit">Update password</button>
          </div>
        </form>
      </div>
    </div>
  )
}

interface SupportMessage { id: number; authorEmail: string; authorName: string; authorRole: string; body: string; createdAt: string }
interface SupportTicket {
  id: number; ticketNumber: string; customerOrderId: number | null; customerOrderNumber: string | null
  subject: string; category: string; priority: string; status: string; resolutionNotes: string | null
  createdAt: string; messages: SupportMessage[]
}

export function CustomerSupportPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [selected, setSelected] = useState<SupportTicket | null>(null)
  const [subject, setSubject] = useState('')
  const [category, setCategory] = useState('ORDER_INQUIRY')
  const [priority, setPriority] = useState('NORMAL')
  const [message, setMessage] = useState('')
  const [reply, setReply] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function load(preferredId?: number) {
    try {
      const { data } = await api.get<PageResult<SupportTicket>>('/customer/support-tickets', { params: { page: 0, size: 50 } })
      setTickets(data.content)
      setSelected((curr) => data.content.find((t) => t.id === (preferredId ?? curr?.id)) ?? data.content[0] ?? null)
    } catch (err) {
      setError(getErrorMessage(err, 'Support tickets could not be loaded.'))
    }
  }

  useEffect(() => { void load() }, [])

  async function createTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<SupportTicket>('/customer/support-tickets', {
        subject, category, priority, message,
      })
      setSubject(''); setMessage('')
      setNotice('Support ticket ' + data.ticketNumber + ' created.')
      await load(data.id)
    } catch (err) {
      setError(getErrorMessage(err, 'Support ticket could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  async function sendReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected || !reply.trim()) return
    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<SupportTicket>('/customer/support-tickets/' + selected.id + '/messages', {
        message: reply.trim(),
      })
      setReply('')
      setNotice('Reply posted.')
      await load(data.id)
    } catch (err) {
      setError(getErrorMessage(err, 'Reply could not be sent.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="customer-page">
      <div className="page-title-row customer-title-row"><div><div className="eyebrow">CUSTOMER CARE</div><h1>Support tickets</h1><p>Need help with an order, pickup, or return? Open a ticket and track store responses.</p></div></div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <div className="address-layout">
        <section className="panel address-list-panel">
          <div className="panel-heading"><div><h2>Your tickets</h2><p>{tickets.length} total</p></div></div>
          {tickets.length === 0 ? <div className="inline-empty">No support tickets yet.</div> : (
            <div className="address-card-list">
              {tickets.map((t) => (
                <article className="address-card" key={t.id} onClick={() => setSelected(t)} style={{ cursor: 'pointer' }}>
                  <div className="address-card-info">
                    <div><strong>{t.ticketNumber} · {t.subject}</strong> <span className="status-pill status-neutral">{friendly(t.status)}</span></div>
                    <span>{friendly(t.category)} · Priority: {t.priority}</span>
                    {t.resolutionNotes && <small>Resolution: {t.resolutionNotes}</small>}
                  </div>
                </article>
              ))}
            </div>
          )}
          {selected && (
            <div style={{ marginTop: '1rem', borderTop: '1px solid var(--line, #e2e8f0)', paddingTop: '1rem' }}>
              <h3>Conversation: {selected.ticketNumber}</h3>
              <div className="address-card-list" style={{ marginTop: '0.5rem' }}>
                {selected.messages.map((m) => (
                  <div key={m.id} className="address-card">
                    <div className="address-card-info">
                      <strong>{m.authorName} ({m.authorRole})</strong>
                      <span>{m.body}</span>
                      <small>{new Date(m.createdAt).toLocaleString()}</small>
                    </div>
                  </div>
                ))}
              </div>
              {selected.status !== 'CLOSED' && (
                <form onSubmit={(event) => void sendReply(event)} className="address-form-fields" style={{ marginTop: '0.75rem' }}>
                  <label>Add reply<textarea required rows={2} value={reply} onChange={(e) => setReply(e.target.value)} /></label>
                  <button className="secondary-button" type="submit" disabled={busy}>Send reply</button>
                </form>
              )}
            </div>
          )}
        </section>
        <form className="panel address-form-panel" onSubmit={(event) => void createTicket(event)}>
          <div className="panel-heading"><div><h2>Open a new support ticket</h2><p>Our operations and customer support team will respond here.</p></div></div>
          <div className="address-form-fields">
            <label>Category<select value={category} onChange={(e) => setCategory(e.target.value)}><option value="ORDER_INQUIRY">Order inquiry</option><option value="STORE_PICKUP">Store pickup</option><option value="PAYMENT_VERIFICATION">Payment verification</option><option value="RETURN_OR_REFUND">Return or refund</option><option value="OTHER">Other</option></select></label>
            <label>Priority<select value={priority} onChange={(e) => setPriority(e.target.value)}><option value="LOW">Low</option><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="URGENT">Urgent</option></select></label>
            <label>Subject<input required maxLength={200} value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Brief summary of your issue" /></label>
            <label>Message<textarea required rows={4} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Include your order number or details…" /></label>
            <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Submitting…' : 'Open ticket'}</button>
          </div>
        </form>
      </div>
    </section>
  )
}
