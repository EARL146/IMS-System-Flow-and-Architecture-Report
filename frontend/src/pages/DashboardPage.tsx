import { useEffect, useState, type ReactNode } from 'react'
import { ArrowUpRight, Boxes, CircleDollarSign, PackageCheck, Warehouse } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'

interface Page<T> { content: T[]; totalElements: number }
interface WarehouseItem { id: number; code: string; name: string; active: boolean }
interface Movement { id: number; sku: string; productName: string; movementType: string; quantityDelta: number; reason: string }
interface StockSummary { totalOnHand: number; totalAvailable: number }

export function DashboardPage() {
  const [productCount, setProductCount] = useState<number | null>(null)
  const [warehouseItems, setWarehouseItems] = useState<WarehouseItem[]>([])
  const [stockUnits, setStockUnits] = useState<number | null>(null)
  const [movements, setMovements] = useState<Movement[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    let current = true
    async function load() {
      try {
        const [products, warehouses, summary] = await Promise.all([
          api.get<Page<unknown>>('/admin/products', { params: { size: 1 } }),
          api.get<WarehouseItem[]>('/admin/warehouses'),
          api.get<StockSummary>('/admin/inventory/summary'),
        ])
        if (!current) return
        setProductCount(products.data.totalElements)
        setWarehouseItems(warehouses.data)

        let recent: Movement[] = []
        for (const warehouse of warehouses.data) {
          const movementResult = await api.get<Page<Movement>>('/admin/inventory/warehouses/' + warehouse.id + '/movements', { params: { size: 6 } })
          recent = recent.concat(movementResult.data.content)
        }
        if (!current) return
        setStockUnits(summary.data.totalOnHand)
        setMovements(recent.sort((a, b) => b.id - a.id).slice(0, 6))
      } catch (requestError) {
        if (current) setError(getErrorMessage(requestError, 'Dashboard data could not be loaded.'))
      }
    }
    void load()
    return () => { current = false }
  }, [])

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">OPERATIONS</div><h1>Good day. Here’s your inventory snapshot.</h1><p>Live totals from the catalog, warehouses and stock ledger.</p></div>
        <Link to="/admin/products" className="primary-button"><Boxes size={17} /> Manage products</Link>
      </div>
      {error && <div className="notice notice-error">{error}</div>}
      <section className="metric-grid">
        <MetricCard icon={<Boxes size={18} />} label="Products" value={productCount?.toLocaleString() ?? '—'} note="Catalog records" tone="blue" />
        <MetricCard icon={<Warehouse size={18} />} label="Warehouses" value={warehouseItems.length.toLocaleString()} note="Active locations" tone="violet" />
        <MetricCard icon={<PackageCheck size={18} />} label="Units on hand" value={stockUnits?.toLocaleString() ?? '—'} note="Current ledger balance" tone="green" />
        <MetricCard icon={<CircleDollarSign size={18} />} label="Sales today" value="In progress" note="Sales endpoints are not implemented yet" tone="amber" />
      </section>

      <section className="dashboard-lower">
        <div className="panel movement-panel">
          <div className="panel-heading"><div><h2>Recent inventory movements</h2><p>Latest recorded ledger entries</p></div><Link to="/admin/inventory">View inventory <ArrowUpRight size={15} /></Link></div>
          {movements.length === 0 ? <div className="inline-empty">No inventory movements have been recorded.</div> : (
            <div className="table-wrap"><table><thead><tr><th>Product</th><th>Movement</th><th>Change</th><th>Reason</th></tr></thead><tbody>
              {movements.map((movement) => <tr key={movement.id}><td><strong>{movement.productName}</strong><small>{movement.sku}</small></td><td><span className="status-pill status-neutral">{movement.movementType}</span></td><td className={movement.quantityDelta >= 0 ? 'positive' : 'negative'}>{movement.quantityDelta > 0 ? '+' : ''}{movement.quantityDelta}</td><td>{movement.reason}</td></tr>)}
            </tbody></table></div>
          )}
        </div>
        <div className="panel phase-panel">
          <div className="eyebrow">BUILD STATUS</div>
          <h2>Live inventory tools</h2>
          <p>Product records and stock adjustments are connected to the API. Sales and procurement metrics will appear after those workflows are built.</p>
          <div className="phase-list"><span><i className="phase-dot live" /> Product catalog</span><span><i className="phase-dot live" /> Inventory ledger</span><span><i className="phase-dot pending" /> Sales &amp; POS</span><span><i className="phase-dot pending" /> Purchasing</span></div>
        </div>
      </section>
    </>
  )
}

function MetricCard({ icon, label, value, note, tone }: { icon: ReactNode; label: string; value: string; note: string; tone: string }) {
  return <article className="metric-card"><div className={'metric-icon ' + tone}>{icon}</div><span>{label}</span><strong>{value}</strong><small>{note}</small></article>
}
