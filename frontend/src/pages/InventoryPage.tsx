import { useEffect, useState, type FormEvent } from 'react'
import { ClipboardList, RotateCw, Send } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'

interface Page<T> { content: T[]; totalElements: number }
interface WarehouseItem { id: number; code: string; name: string; active: boolean }
interface ProductOption { id: number; sku: string; name: string }
interface ProductPage { content: ProductOption[] }
interface StockItem { productId: number; sku: string; productName: string; warehouseName: string; onHand: number; reserved: number; damaged: number; available: number }
interface Movement { id: number; sku: string; productName: string; movementType: string; quantityDelta: number; quantityBefore: number; quantityAfter: number; reason: string; occurredAt: string }

export function InventoryPage() {
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [products, setProducts] = useState<ProductOption[]>([])
  const [warehouseId, setWarehouseId] = useState('')
  const [stock, setStock] = useState<StockItem[]>([])
  const [movements, setMovements] = useState<Movement[]>([])
  const [productId, setProductId] = useState('')
  const [delta, setDelta] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let current = true
    Promise.all([
      api.get<WarehouseItem[]>('/admin/warehouses'),
      api.get<ProductPage>('/admin/products', { params: { size: 100 } }),
    ]).then(([warehouseResult, productResult]) => {
      if (!current) return
      setWarehouses(warehouseResult.data)
      setProducts(productResult.data.content)
      if (warehouseResult.data[0]) setWarehouseId(String(warehouseResult.data[0].id))
    }).catch((requestError: unknown) => {
      if (current) setError(getErrorMessage(requestError, 'Inventory setup could not be loaded.'))
    })
    return () => { current = false }
  }, [])

  useEffect(() => {
    if (!warehouseId) {
      setStock([])
      setMovements([])
      return
    }
    let current = true
    setLoading(true)
    Promise.all([
      api.get<Page<StockItem>>('/admin/inventory/warehouses/' + warehouseId + '/stock', { params: { page: 0, size: 100 } }),
      api.get<Page<Movement>>('/admin/inventory/warehouses/' + warehouseId + '/movements', { params: { page: 0, size: 100 } }),
    ]).then(([stockResult, movementResult]) => {
      if (!current) return
      setStock(stockResult.data.content)
      setMovements(movementResult.data.content)
      setError('')
    }).catch((requestError: unknown) => {
      if (current) setError(getErrorMessage(requestError, 'Inventory could not be loaded.'))
    }).finally(() => {
      if (current) setLoading(false)
    })
    return () => { current = false }
  }, [warehouseId, refreshKey])

  async function submitAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!warehouseId || !productId) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await api.post('/admin/inventory/adjustments', {
        productId: Number(productId),
        warehouseId: Number(warehouseId),
        quantityDelta: delta,
        idempotencyKey: crypto.randomUUID(),
        reason,
      })
      setNotice('Stock adjustment recorded in the inventory ledger.')
      setDelta('')
      setReason('')
      setRefreshKey((value) => value + 1)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Stock adjustment was rejected.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="page-title-row"><div><div className="eyebrow">STOCK CONTROL</div><h1>Inventory</h1><p>Every adjustment is recorded as a traceable ledger movement.</p></div><button className="secondary-button" onClick={() => setRefreshKey((value) => value + 1)}><RotateCw size={16} /> Refresh</button></div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <div className="inventory-toolbar"><label>Warehouse<select value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)}><option value="">Choose a warehouse</option>{warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} · {warehouse.code}</option>)}</select></label><span className="ledger-caption"><ClipboardList size={16} /> Stock values are read from the backend ledger.</span></div>
      <section className="panel inventory-adjust-panel"><div className="panel-heading"><div><h2>Record stock adjustment</h2><p>Use positive quantities to add stock and negative quantities for corrections.</p></div></div>
        {warehouses.length === 0 ? <div className="inline-empty">Create a warehouse before recording inventory.</div> : products.length === 0 ? <div className="inline-empty">Create a product before recording inventory.</div> : (
          <form className="adjustment-form" onSubmit={submitAdjustment}>
            <label>Product<select required value={productId} onChange={(event) => setProductId(event.target.value)}><option value="">Select a product</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>)}</select></label>
            <label>Quantity change<input required inputMode="decimal" type="number" step="0.001" value={delta} onChange={(event) => setDelta(event.target.value)} placeholder="e.g. 12 or -2" /></label>
            <label>Reason<input required maxLength={240} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Count correction, opening stock…" /></label>
            <button className="primary-button" disabled={saving || !warehouseId}><Send size={15} />{saving ? 'Recording…' : 'Record movement'}</button>
          </form>
        )}
      </section>
      <section className="panel inventory-table-panel"><div className="panel-heading"><div><h2>Stock overview</h2><p>{stock.length} tracked product locations</p></div></div>
        {loading ? <div className="inline-empty">Loading balances…</div> : stock.length === 0 ? <div className="inline-empty">No stock balances exist for this warehouse yet.</div> : (
          <div className="table-wrap"><table><thead><tr><th>Product</th><th>On hand</th><th>Reserved</th><th>Damaged</th><th>Available</th></tr></thead><tbody>
            {stock.map((item) => <tr key={item.productId}><td><strong>{item.productName}</strong><small>{item.sku}</small></td><td>{item.onHand}</td><td>{item.reserved}</td><td>{item.damaged}</td><td><strong>{item.available}</strong></td></tr>)}
          </tbody></table></div>
        )}
      </section>
      <section className="panel inventory-table-panel"><div className="panel-heading"><div><h2>Movement history</h2><p>Recent changes for the selected warehouse</p></div></div>
        {movements.length === 0 ? <div className="inline-empty">No movements have been recorded.</div> : <div className="table-wrap"><table><thead><tr><th>When</th><th>Product</th><th>Change</th><th>Balance</th><th>Reason</th></tr></thead><tbody>
          {movements.map((movement) => <tr key={movement.id}><td>{new Date(movement.occurredAt).toLocaleString()}</td><td><strong>{movement.productName}</strong><small>{movement.sku}</small></td><td className={movement.quantityDelta >= 0 ? 'positive' : 'negative'}>{movement.quantityDelta > 0 ? '+' : ''}{movement.quantityDelta}</td><td>{movement.quantityBefore} → {movement.quantityAfter}</td><td>{movement.reason}</td></tr>)}
        </tbody></table></div>}
      </section>
    </>
  )
}
