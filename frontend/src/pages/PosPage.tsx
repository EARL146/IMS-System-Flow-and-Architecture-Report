import { useEffect, useMemo, useState } from 'react'
import { Barcode, Check, CircleUserRound, Minus, Plus, Printer, ReceiptText, Search, ShoppingCart, Trash2 } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './pos.css'

type PaymentMethod = 'CASH' | 'CARD_TERMINAL'
type TrackingType = 'QUANTITY' | 'SERIAL' | 'LOT' | 'NONE'
interface Page<T> { content: T[]; totalElements: number; totalPages: number }
interface Warehouse { id: number; code: string; name: string }
interface PosConfig { taxRate: number; currencyCode: string }
interface PosProduct { id: number; sku: string; barcode: string | null; name: string; categoryName: string | null; unitOfMeasure: string; trackingType: TrackingType; sellingPrice: number; availableQuantity: number | null }
interface CustomerOption { id: number; email: string; fullName: string }
interface CartLine { product: PosProduct; quantity: number }
interface SaleItem { id: number; lineNumber: number; productId: number; sku: string; productName: string; unitOfMeasure: string; quantity: number; unitPrice: number; lineTotal: number }
interface Sale {
  id: number; saleNumber: string; status: string; createdAt: string; warehouseId: number; warehouseName: string
  cashierName: string; customerId: number | null; customerEmail: string | null; customerName: string | null
  paymentMethod: PaymentMethod; paymentStatus: string; paymentReference: string | null; currencyCode: string
  subtotal: number; discountAmount: number; taxAmount: number; total: number; amountTendered: number | null
  changeGiven: number; items: SaleItem[]
}

function createKey() {
  return globalThis.crypto?.randomUUID?.() ?? 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0
    return (char === 'x' ? random : (random & 0x3 | 0x8)).toString(16)
  })
}

function money(value: number, currency: string) {
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) }
  catch { return currency + ' ' + value.toFixed(2) }
}

export function PosPage() {
  const { user } = useAuth()
  const canSell = Boolean(user?.roles.some((role) => ['OWNER', 'ADMIN', 'CASHIER'].includes(role)))
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [warehouseId, setWarehouseId] = useState('')
  const [config, setConfig] = useState<PosConfig>({ taxRate: 0, currencyCode: 'PHP' })
  const [products, setProducts] = useState<PosProduct[]>([])
  const [productQuery, setProductQuery] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [productsLoading, setProductsLoading] = useState(false)
  const [cart, setCart] = useState<CartLine[]>([])
  const [discount, setDiscount] = useState('0')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [amountTendered, setAmountTendered] = useState('')
  const [paymentReference, setPaymentReference] = useState('')
  const [customerQuery, setCustomerQuery] = useState('')
  const [customers, setCustomers] = useState<CustomerOption[]>([])
  const [customer, setCustomer] = useState<CustomerOption | null>(null)
  const [sales, setSales] = useState<Sale[]>([])
  const [selectedSale, setSelectedSale] = useState<Sale | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(createKey)

  const subtotal = useMemo(() => cart.reduce((sum, line) => sum + line.product.sellingPrice * line.quantity, 0), [cart])
  const discountAmount = Math.max(0, Number(discount) || 0)
  const taxableAmount = Math.max(0, subtotal - discountAmount)
  const taxAmount = taxableAmount * config.taxRate
  const total = taxableAmount + taxAmount
  const changeDue = Math.max(0, (Number(amountTendered) || 0) - total)
  const categories = useMemo(() => [...new Set(products.map((product) => product.categoryName).filter((name): name is string => Boolean(name)))].sort(), [products])
  const filteredProducts = products.filter((product) => !categoryFilter || product.categoryName === categoryFilter)

  useEffect(() => {
    let active = true
    void Promise.all([
      api.get<Warehouse[]>('/catalog/warehouses'),
      api.get<PosConfig>('/admin/pos/config'),
      api.get<Page<Sale>>('/admin/pos/sales', { params: { page: 0, size: 20 } }),
    ]).then(([warehouseResult, configResult, salesResult]) => {
      if (!active) return
      setWarehouses(warehouseResult.data)
      setWarehouseId(warehouseResult.data[0] ? String(warehouseResult.data[0].id) : '')
      setConfig(configResult.data)
      setSales(salesResult.data.content)
    }).catch((requestError: unknown) => {
      if (active) setError(getErrorMessage(requestError, 'POS data could not be loaded.'))
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!canSell || !warehouseId) return
    let active = true
    const timer = window.setTimeout(() => {
      setProductsLoading(true)
      api.get<Page<PosProduct>>('/admin/pos/products', { params: { warehouseId, query: productQuery.trim(), page: 0, size: 60 } })
        .then(({ data }) => { if (active) setProducts(data.content) })
        .catch((requestError: unknown) => { if (active) setError(getErrorMessage(requestError, 'Products could not be searched.')) })
        .finally(() => { if (active) setProductsLoading(false) })
    }, 220)
    return () => { active = false; window.clearTimeout(timer) }
  }, [canSell, warehouseId, productQuery])

  useEffect(() => {
    const query = customerQuery.trim()
    if (!canSell || query.length < 2 || customer) return
    let active = true
    const timer = window.setTimeout(() => {
      api.get<Page<CustomerOption>>('/admin/pos/customers', { params: { query, page: 0, size: 8 } })
        .then(({ data }) => { if (active) setCustomers(data.content) })
        .catch(() => { if (active) setCustomers([]) })
    }, 250)
    return () => { active = false; window.clearTimeout(timer) }
  }, [canSell, customerQuery, customer])

  function changeCart(next: CartLine[]) {
    setCart(next)
    setIdempotencyKey(createKey())
    setError('')
  }

  function addProduct(product: PosProduct) {
    const existing = cart.find((line) => line.product.id === product.id)
    const nextQuantity = (existing?.quantity ?? 0) + 1
    if (product.availableQuantity !== null && nextQuantity > product.availableQuantity) {
      setError('There is not enough available stock for ' + product.name + '.')
      return
    }
    changeCart(existing
      ? cart.map((line) => line.product.id === product.id ? { ...line, quantity: nextQuantity } : line)
      : [...cart, { product, quantity: 1 }])
  }

  function updateQuantity(productId: number, quantity: number) {
    const line = cart.find((item) => item.product.id === productId)
    if (!line) return
    if (quantity <= 0) { changeCart(cart.filter((item) => item.product.id !== productId)); return }
    if (line.product.availableQuantity !== null && quantity > line.product.availableQuantity) {
      setError('Entered quantity exceeds the available stock for ' + line.product.name + '.')
      return
    }
    changeCart(cart.map((item) => item.product.id === productId ? { ...item, quantity } : item))
  }

  async function loadSales() {
    try {
      const { data } = await api.get<Page<Sale>>('/admin/pos/sales', { params: { page: 0, size: 20 } })
      setSales(data.content)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Sales history could not be refreshed.'))
    }
  }

  async function completeSale() {
    if (!warehouseId || cart.length === 0) {
      setError('Choose a warehouse and add at least one product.')
      return
    }
    if (discountAmount >= subtotal || Number(discount) < 0) {
      setError('Discount must be less than the subtotal.')
      return
    }
    if (paymentMethod === 'CASH' && Number(amountTendered) < total) {
      setError('Cash received must cover the sale total.')
      return
    }
    if (paymentMethod === 'CARD_TERMINAL' && !paymentReference.trim()) {
      setError('Enter the approved external terminal reference.')
      return
    }

    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<Sale>('/admin/pos/sales', {
        idempotencyKey,
        warehouseId: Number(warehouseId),
        customerEmail: customer?.email ?? null,
        items: cart.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
        discountAmount,
        paymentMethod,
        amountTendered: paymentMethod === 'CASH' ? Number(amountTendered) : null,
        paymentReference: paymentMethod === 'CARD_TERMINAL' ? paymentReference.trim() : null,
      })
      setSelectedSale(data)
      setCart([]); setDiscount('0'); setAmountTendered(''); setPaymentReference('')
      setCustomer(null); setCustomerQuery(''); setIdempotencyKey(createKey())
      setNotice('Sale completed and the receipt is ready to print.')
      await loadSales()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'The sale could not be completed.'))
    } finally { setBusy(false) }
  }

  return (
    <div className="pos-page">
      <div className="page-title-row pos-title-row">
        <div><div className="eyebrow">SALES OPERATIONS</div><h1>{canSell ? 'Point of sale' : 'Sales history'}</h1><p>{canSell ? 'Search live products, confirm tender and complete a stock-checked sale.' : 'Review completed sales and print receipts.'}</p></div>
        {canSell && <label className="pos-warehouse-select">Selling from<select aria-label="Selling warehouse" value={warehouseId} onChange={(event) => { setWarehouseId(event.target.value); setProducts([]); setCategoryFilter(''); setIdempotencyKey(createKey()) }} disabled={loading || warehouses.length === 0}>{warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} · {warehouse.code}</option>)}</select></label>}
      </div>
      {notice && <div className="notice notice-success" role="status"><Check size={15} />{notice}</div>}
      {error && <div className="notice notice-error" role="alert">{error}</div>}
      {canSell && (loading ? <div className="inline-empty">Loading point-of-sale options…</div> : warehouses.length === 0 ? <div className="notice notice-error">Create an active warehouse before using the POS.</div> : (
        <div className="pos-workspace">
          <section className="panel pos-catalog-panel">
            <div className="pos-catalog-toolbar">
              <label className="pos-product-search"><Search size={16} /><input aria-label="Search products by name, SKU, or barcode" value={productQuery} onChange={(event) => setProductQuery(event.target.value)} placeholder="Search name, SKU, or scan barcode" /><Barcode size={16} /></label>
              {categories.length > 0 && <label className="pos-category-filter"><span>Category</span><select aria-label="Filter by category" value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="">All categories</option>{categories.map((category) => <option key={category}>{category}</option>)}</select></label>}
            </div>
            {productsLoading ? <div className="inline-empty pos-products-loading">Searching products…</div> : filteredProducts.length === 0 ? <div className="inline-empty pos-products-loading">{productQuery ? 'No products match this search.' : 'No active products found.'}</div> : (
              <div className="pos-product-grid">{filteredProducts.map((product) => {
                const quantityInCart = cart.find((line) => line.product.id === product.id)?.quantity ?? 0
                const unavailable = product.availableQuantity !== null && quantityInCart >= product.availableQuantity
                return <article className="pos-product-card" key={product.id}>
                  <div className="pos-product-card-top"><span className="pos-product-symbol"><ShoppingCart size={17} /></span><span className="pos-product-category">{product.categoryName ?? 'General'}</span></div>
                  <strong>{product.name}</strong><small className="pos-product-sku">{product.sku}{product.barcode ? ' · ' + product.barcode : ''}</small>
                  <div className="pos-product-card-footer"><span><strong>{money(product.sellingPrice, config.currencyCode)}</strong><small>{product.availableQuantity === null ? 'Stock not tracked' : product.availableQuantity + ' ' + product.unitOfMeasure + ' available'}</small></span><button aria-label={'Add ' + product.name} className="pos-add-button" disabled={unavailable} onClick={() => addProduct(product)}><Plus size={16} /></button></div>
                </article>
              })}</div>
            )}
          </section>

          <aside className="panel pos-checkout-panel">
            <div className="panel-heading"><div><h2><ShoppingCart size={16} /> Current sale</h2><p>{cart.length} product{cart.length === 1 ? '' : 's'} · {config.currencyCode}</p></div><button className="text-button" disabled={cart.length === 0 || busy} onClick={() => changeCart([])}>Clear</button></div>
            {cart.length ? <div className="pos-cart-lines">{cart.map((line) => <div className="pos-cart-line" key={line.product.id}>
              <div className="pos-cart-line-info"><strong>{line.product.name}</strong><small>{line.product.sku} · {money(line.product.sellingPrice, config.currencyCode)}</small></div>
              <div className="pos-quantity-controls"><button aria-label={'Decrease ' + line.product.name} onClick={() => updateQuantity(line.product.id, line.quantity - 1)}><Minus size={13} /></button><input aria-label={'Quantity for ' + line.product.name} type="number" min="0.001" step="0.001" value={line.quantity} onChange={(event) => updateQuantity(line.product.id, Number(event.target.value))} /><button aria-label={'Increase ' + line.product.name} onClick={() => updateQuantity(line.product.id, line.quantity + 1)}><Plus size={13} /></button></div>
              <strong className="pos-line-total">{money(line.product.sellingPrice * line.quantity, config.currencyCode)}</strong><button aria-label={'Remove ' + line.product.name} className="pos-remove-line" onClick={() => updateQuantity(line.product.id, 0)}><Trash2 size={14} /></button>
            </div>)}</div> : <div className="pos-cart-empty"><ShoppingCart size={21} /><span>Add products to start a sale.</span></div>}
            <div className="pos-sale-fields">
              <div className="pos-customer-field"><label htmlFor="pos-customer">Customer <span>Optional</span></label>{customer ? <div className="pos-selected-customer"><CircleUserRound size={15} /><span><strong>{customer.fullName}</strong><small>{customer.email}</small></span><button className="text-button" onClick={() => { setCustomer(null); setCustomerQuery(''); setCustomers([]); setIdempotencyKey(createKey()) }}>Remove</button></div> : <><input id="pos-customer" value={customerQuery} onChange={(event) => { setCustomerQuery(event.target.value); setCustomer(null); setCustomers([]); setIdempotencyKey(createKey()) }} placeholder="Search customer name or email" />{customers.length > 0 && <div className="pos-customer-options">{customers.map((option) => <button key={option.id} onClick={() => { setCustomer(option); setCustomerQuery(option.email); setCustomers([]); setIdempotencyKey(createKey()) }}><strong>{option.fullName}</strong><small>{option.email}</small></button>)}</div>}</>}</div>
              <label>Discount amount<input type="number" min="0" max={subtotal} step="0.0001" value={discount} onChange={(event) => { setDiscount(event.target.value); setIdempotencyKey(createKey()) }} /></label>
              <label>Payment method<select value={paymentMethod} onChange={(event) => { setPaymentMethod(event.target.value as PaymentMethod); setAmountTendered(''); setPaymentReference(''); setIdempotencyKey(createKey()) }}><option value="CASH">Cash</option><option value="CARD_TERMINAL">External card terminal</option></select></label>
              {paymentMethod === 'CASH' ? <label>Cash received<input type="number" min={total.toFixed(4)} step="0.0001" value={amountTendered} onChange={(event) => { setAmountTendered(event.target.value); setIdempotencyKey(createKey()) }} placeholder={total.toFixed(2)} /></label> : <label>Approved terminal reference<input maxLength={100} value={paymentReference} onChange={(event) => { setPaymentReference(event.target.value); setIdempotencyKey(createKey()) }} placeholder="Authorization or terminal reference" /></label>}
              <div className="pos-total-breakdown"><div><span>Subtotal</span><span>{money(subtotal, config.currencyCode)}</span></div><div><span>Discount</span><span>−{money(discountAmount, config.currencyCode)}</span></div><div><span>Tax ({(config.taxRate * 100).toFixed(2)}%)</span><span>{money(taxAmount, config.currencyCode)}</span></div><div className="pos-grand-total"><strong>Total due</strong><strong>{money(total, config.currencyCode)}</strong></div>{paymentMethod === 'CASH' && Number(amountTendered) >= total && total > 0 && <div className="pos-change-line"><strong>Change due</strong><strong>{money(changeDue, config.currencyCode)}</strong></div>}</div>
              {paymentMethod === 'CARD_TERMINAL' && <small className="pos-payment-hint">Confirm approval in the external terminal first. The POS stores only your reference; it does not process card details.</small>}
              <button className="primary-button pos-complete-button" disabled={busy || cart.length === 0 || (paymentMethod === 'CASH' && Number(amountTendered) < total) || (paymentMethod === 'CARD_TERMINAL' && !paymentReference.trim())} onClick={() => void completeSale()}>{busy ? 'Completing…' : 'Complete sale'}<ReceiptText size={16} /></button>
            </div>
          </aside>
        </div>
      ))}

      {selectedSale && <section className="panel pos-receipt-panel" aria-label="Sale receipt"><div className="pos-receipt-toolbar"><div><div className="eyebrow">COMPLETED SALE</div><h2>{selectedSale.saleNumber}</h2></div><button className="secondary-button" onClick={() => window.print()}><Printer size={15} /> Print receipt</button></div><Receipt sale={selectedSale} /></section>}

      <section className="panel pos-history-panel"><div className="toolbar"><div className="toolbar-title"><ReceiptText size={17} /><strong>Recent sales</strong><span>{sales.length}</span></div></div>{sales.length ? <div className="table-wrap"><table><thead><tr><th>Receipt</th><th>Time</th><th>Customer</th><th>Cashier</th><th>Payment</th><th>Total</th><th></th></tr></thead><tbody>{sales.map((sale) => <tr key={sale.id}><td><strong>{sale.saleNumber}</strong></td><td>{new Date(sale.createdAt).toLocaleString()}</td><td>{sale.customerName ?? 'Walk-in'}</td><td>{sale.cashierName}</td><td>{sale.paymentMethod === 'CASH' ? 'Cash' : 'External terminal'}</td><td>{money(sale.total, sale.currencyCode)}</td><td><button className="secondary-button pos-view-receipt" onClick={() => setSelectedSale(sale)}>Receipt</button></td></tr>)}</tbody></table></div> : <div className="inline-empty">Completed sales will appear here.</div>}</section>
    </div>
  )
}

function Receipt({ sale }: { sale: Sale }) {
  return <div className="pos-print-target"><div className="pos-receipt-header"><strong>Northstar Supply Co.</strong><small>Sales receipt</small></div><div className="pos-receipt-meta"><span>Receipt <strong>{sale.saleNumber}</strong></span><span>Date <strong>{new Date(sale.createdAt).toLocaleString()}</strong></span><span>Cashier <strong>{sale.cashierName}</strong></span><span>Warehouse <strong>{sale.warehouseName}</strong></span>{sale.customerName && <span>Customer <strong>{sale.customerName}</strong></span>}</div><div className="pos-receipt-items">{sale.items.map((item) => <div key={item.id}><span><strong>{item.productName}</strong><small>{item.sku} · {item.quantity} {item.unitOfMeasure} × {money(item.unitPrice, sale.currencyCode)}</small></span><strong>{money(item.lineTotal, sale.currencyCode)}</strong></div>)}</div><div className="pos-receipt-totals"><div><span>Subtotal</span><span>{money(sale.subtotal, sale.currencyCode)}</span></div><div><span>Discount</span><span>−{money(sale.discountAmount, sale.currencyCode)}</span></div><div><span>Tax</span><span>{money(sale.taxAmount, sale.currencyCode)}</span></div><div className="pos-receipt-grand"><strong>Total</strong><strong>{money(sale.total, sale.currencyCode)}</strong></div>{sale.amountTendered !== null && <><div><span>Cash received</span><span>{money(sale.amountTendered, sale.currencyCode)}</span></div><div><span>Change</span><span>{money(sale.changeGiven, sale.currencyCode)}</span></div></>}{sale.paymentReference && <div><span>Terminal reference</span><span>{sale.paymentReference}</span></div>}</div><p className="pos-receipt-thanks">Thank you for your purchase.</p></div>
}
