import { useEffect, useState, type FormEvent } from 'react'
import { BadgeCheck, Download, FileSpreadsheet, HelpCircle, Lock, RefreshCw, ShieldAlert, UserPlus } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'

interface Page<T> { content: T[]; totalElements: number }

interface OverviewReport {
  sales: {
    posSalesCount: number
    posRevenue: number
    ecommerceOrderCount: number
    ecommerceRevenue: number
    storePickupOrderCount: number
    deliveryOrderCount: number
    totalRevenue: number
  }
  inventory: {
    totalBalanceRows: number
    totalOnHandUnits: number
    totalReservedUnits: number
    totalDamagedUnits: number
    totalAvailableUnits: number
    totalRetailValuation: number
    lowStockRows: number
    ledgerReconciled: boolean
    discrepancyCount: number
    discrepancies: Array<{
      productId: number
      sku: string
      warehouseId: number
      warehouseCode: string
      balanceOnHand: number
      ledgerExpectedOnHand: number
      variance: number
    }>
  }
  payables: {
    totalPayablesCount: number
    unsettledPayablesCount: number
    totalGrossAmount: number
    totalCreditApplied: number
    totalAmountPaid: number
    totalBalanceDue: number
  }
  supplierPerformance: Array<{
    supplierId: number
    supplierCode: string
    supplierName: string
    purchaseOrderCount: number
    totalOrderedValue: number
    totalReceivedValue: number
    supplierReturnCount: number
    totalReturnedValue: number
    totalCreditNotesValue: number
    receiptFulfillmentRatePercent: number
  }>
}

function money(value: number, currency = 'PHP') {
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) }
  catch { return currency + ' ' + value.toFixed(2) }
}

export function ReportsAdminPage() {
  const [report, setReport] = useState<OverviewReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true); setError('')
    try {
      const { data } = await api.get<OverviewReport>('/admin/reports/overview')
      setReport(data)
    } catch (err) {
      setError(getErrorMessage(err, 'Operations reports could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  async function downloadCsv(type: 'inventory' | 'sales' | 'suppliers' | 'audit') {
    try {
      const response = await api.get<string>('/admin/reports/export/' + type, { responseType: 'text' as never })
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `ims-${type}-report.csv`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(getErrorMessage(err, 'CSV export failed.'))
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div>
          <div className="eyebrow">ANALYTICS & LEDGER RECONCILIATION</div>
          <h1>Reports & CSV Exports</h1>
          <p>Cross-channel revenue, inventory valuation, ledger-vs-balance verification, supplier performance, and accounts payable.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button className="secondary-button" onClick={() => void downloadCsv('inventory')}><Download size={15} /> Inventory CSV</button>
          <button className="secondary-button" onClick={() => void downloadCsv('sales')}><Download size={15} /> Sales CSV</button>
          <button className="secondary-button" onClick={() => void downloadCsv('suppliers')}><Download size={15} /> Suppliers CSV</button>
          <button className="secondary-button" onClick={() => void downloadCsv('audit')}><FileSpreadsheet size={15} /> Audit CSV</button>
          <button className="secondary-button" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button>
        </div>
      </div>
      {error && <div className="notice notice-error">{error}</div>}
      {loading || !report ? <div className="inline-empty">Loading operational reports…</div> : (
        <>
          <section className="panel" style={{ marginBottom: '1.25rem' }}>
            <div className="panel-heading"><div><h2>Sales & Fulfillment Summary</h2><p>Combined POS and e-commerce revenue</p></div></div>
            <div className="customer-admin-summary">
              <span><small>Total Revenue</small><strong>{money(report.sales.totalRevenue)}</strong></span>
              <span><small>POS Sales ({report.sales.posSalesCount})</small><strong>{money(report.sales.posRevenue)}</strong></span>
              <span><small>Online Orders ({report.sales.ecommerceOrderCount})</small><strong>{money(report.sales.ecommerceRevenue)}</strong></span>
              <span><small>Delivery vs Store Pickup</small><strong>{report.sales.deliveryOrderCount} delivery · {report.sales.storePickupOrderCount} pickup</strong></span>
            </div>
          </section>

          <section className="panel" style={{ marginBottom: '1.25rem' }}>
            <div className="panel-heading">
              <div><h2>Inventory Valuation & Ledger Reconciliation</h2><p>Compares live inventory balances against cumulative ledger movements</p></div>
              <span className={'status-pill ' + (report.inventory.ledgerReconciled ? 'status-live' : 'status-error')}>
                {report.inventory.ledgerReconciled ? 'Ledger 100% Reconciled' : `${report.inventory.discrepancyCount} Discrepancies`}
              </span>
            </div>
            <div className="customer-admin-summary">
              <span><small>Retail Valuation</small><strong>{money(report.inventory.totalRetailValuation)}</strong></span>
              <span><small>On Hand / Available</small><strong>{report.inventory.totalOnHandUnits} / {report.inventory.totalAvailableUnits}</strong></span>
              <span><small>Reserved / Damaged</small><strong>{report.inventory.totalReservedUnits} / {report.inventory.totalDamagedUnits}</strong></span>
              <span><small>Low Stock Alerts</small><strong>{report.inventory.lowStockRows} SKU locations</strong></span>
            </div>
          </section>

          <section className="panel" style={{ marginBottom: '1.25rem' }}>
            <div className="panel-heading"><div><h2>Supplier Accounts Payable</h2><p>Received PO liabilities minus supplier return credit notes</p></div></div>
            <div className="customer-admin-summary">
              <span><small>Gross Payables ({report.payables.totalPayablesCount})</small><strong>{money(report.payables.totalGrossAmount)}</strong></span>
              <span><small>Return Credits Applied</small><strong>{money(report.payables.totalCreditApplied)}</strong></span>
              <span><small>Amount Settled</small><strong>{money(report.payables.totalAmountPaid)}</strong></span>
              <span><small>Balance Due ({report.payables.unsettledPayablesCount} open)</small><strong>{money(report.payables.totalBalanceDue)}</strong></span>
            </div>
          </section>

          <section className="panel">
            <div className="panel-heading"><div><h2>Supplier Performance</h2><p>Fulfillment rate, return volume, and credit recovery by supplier</p></div></div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Supplier</th>
                    <th>POs</th>
                    <th>Ordered Value</th>
                    <th>Received Value</th>
                    <th>Fulfillment %</th>
                    <th>Returns</th>
                    <th>Return Value</th>
                    <th>Credits Issued</th>
                  </tr>
                </thead>
                <tbody>
                  {report.supplierPerformance.map((row) => (
                    <tr key={row.supplierId}>
                      <td><strong>{row.supplierName}</strong><small>{row.supplierCode}</small></td>
                      <td>{row.purchaseOrderCount}</td>
                      <td>{money(row.totalOrderedValue)}</td>
                      <td>{money(row.totalReceivedValue)}</td>
                      <td><strong>{row.receiptFulfillmentRatePercent}%</strong></td>
                      <td>{row.supplierReturnCount}</td>
                      <td>{money(row.totalReturnedValue)}</td>
                      <td>{money(row.totalCreditNotesValue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </>
  )
}

interface AdminSupportTicket {
  id: number
  ticketNumber: string
  customerEmail: string
  customerName: string
  customerOrderNumber: string | null
  subject: string
  category: string
  priority: string
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
  assignedToEmail: string | null
  resolutionNotes: string | null
  createdAt: string
  messages: Array<{ id: number; authorEmail: string; authorName: string; authorRole: string; body: string; createdAt: string }>
}

export function SupportTicketsAdminPage() {
  const [tickets, setTickets] = useState<AdminSupportTicket[]>([])
  const [selected, setSelected] = useState<AdminSupportTicket | null>(null)
  const [reply, setReply] = useState('')
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function load(preferredId?: number) {
    try {
      const { data } = await api.get<Page<AdminSupportTicket>>('/admin/support-tickets', { params: { page: 0, size: 50 } })
      setTickets(data.content)
      setSelected((curr) => data.content.find((t) => t.id === (preferredId ?? curr?.id)) ?? data.content[0] ?? null)
    } catch (err) {
      setError(getErrorMessage(err, 'Support tickets could not be loaded.'))
    }
  }

  useEffect(() => { void load() }, [])

  async function sendReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected || !reply.trim()) return
    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<AdminSupportTicket>('/admin/support-tickets/' + selected.id + '/messages', {
        message: reply.trim(),
      })
      setReply('')
      setNotice('Reply sent to customer.')
      await load(data.id)
    } catch (err) {
      setError(getErrorMessage(err, 'Reply could not be sent.'))
    } finally {
      setBusy(false)
    }
  }

  async function updateStatus(status: AdminSupportTicket['status']) {
    if (!selected) return
    setBusy(true); setError(''); setNotice('')
    try {
      const { data } = await api.post<AdminSupportTicket>('/admin/support-tickets/' + selected.id + '/status', {
        status,
        resolutionNotes: resolutionNotes.trim() || null,
      })
      setNotice('Ticket status updated to ' + status + '.')
      await load(data.id)
    } catch (err) {
      setError(getErrorMessage(err, 'Status could not be updated.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">CUSTOMER CARE</div><h1>Support Tickets</h1><p>Respond to customer inquiries, store pickup coordination, and post-purchase issues.</p></div>
        <button className="secondary-button" onClick={() => void load(selected?.id)}><RefreshCw size={15} /> Refresh</button>
      </div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <div className="customer-admin-layout">
        <section className="panel customer-admin-list-panel">
          <div className="panel-heading"><div><h2>Ticket queue</h2><p>{tickets.length} tickets</p></div></div>
          {tickets.length === 0 ? <div className="inline-empty">No customer support tickets yet.</div> : (
            <div className="customer-admin-queue">
              {tickets.map((t) => (
                <button key={t.id} className={'customer-admin-order-row' + (selected?.id === t.id ? ' selected' : '')} onClick={() => setSelected(t)}>
                  <span className="customer-admin-order-icon"><HelpCircle size={17} /></span>
                  <span className="customer-admin-order-copy"><strong>{t.ticketNumber} · {t.subject}</strong><small>{t.customerEmail} · {t.priority}</small></span>
                  <span className="customer-admin-order-total"><small>{t.status}</small></span>
                </button>
              ))}
            </div>
          )}
        </section>
        {selected ? (
          <section className="panel customer-admin-detail">
            <header className="customer-admin-detail-header">
              <div><div className="eyebrow">{selected.ticketNumber} · {selected.category}</div><h2>{selected.subject}</h2><p>{selected.customerName} ({selected.customerEmail})</p></div>
              <span className="status-pill status-live">{selected.status}</span>
            </header>
            <div className="customer-admin-section">
              <h3>Conversation</h3>
              <div className="customer-admin-lines">
                {selected.messages.map((m) => (
                  <div className="customer-admin-line" key={m.id}>
                    <span><strong>{m.authorName} ({m.authorRole})</strong><small>{m.body}</small></span>
                    <small>{new Date(m.createdAt).toLocaleString()}</small>
                  </div>
                ))}
              </div>
            </div>
            <form className="customer-admin-section" onSubmit={(e) => void sendReply(e)}>
              <h3>Reply to customer</h3>
              <label>Message<textarea required rows={3} value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Type your response…" /></label>
              <button className="primary-button" type="submit" disabled={busy} style={{ marginTop: '0.5rem' }}>Send reply</button>
            </form>
            <div className="customer-admin-section">
              <h3>Resolution & Status</h3>
              <label>Resolution notes<input value={resolutionNotes} onChange={(e) => setResolutionNotes(e.target.value)} placeholder="Optional summary of resolution" /></label>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                <button type="button" className="secondary-button" disabled={busy} onClick={() => void updateStatus('IN_PROGRESS')}>In Progress</button>
                <button type="button" className="primary-button" disabled={busy} onClick={() => void updateStatus('RESOLVED')}><BadgeCheck size={15} /> Resolve</button>
                <button type="button" className="secondary-button" disabled={busy} onClick={() => void updateStatus('CLOSED')}>Close</button>
              </div>
            </div>
          </section>
        ) : <section className="panel customer-admin-no-selection"><HelpCircle size={22} /><strong>Select a support ticket</strong></section>}
      </div>
    </>
  )
}

interface StaffUserRow {
  id: number
  email: string
  fullName: string
  enabled: boolean
  failedLoginAttempts: number
  lockedUntil: string | null
  roles: string[]
  assignedWarehouseIds: number[]
  createdAt: string
}

interface AuditEventRow {
  id: number
  eventType: string
  actorUserId: number | null
  actorEmail: string | null
  targetType: string | null
  targetId: string | null
  summary: string
  occurredAt: string
}

export function UsersAndAuditAdminPage() {
  const [users, setUsers] = useState<StaffUserRow[]>([])
  const [auditEvents, setAuditEvents] = useState<AuditEventRow[]>([])
  const [warehouses, setWarehouses] = useState<Array<{ id: number; code: string; name: string }>>([])
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('INVENTORY_MANAGER')
  const [warehouseScopeId, setWarehouseScopeId] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function load() {
    try {
      const [usersRes, auditRes, whRes] = await Promise.all([
        api.get<Page<StaffUserRow>>('/admin/users', { params: { page: 0, size: 50 } }),
        api.get<Page<AuditEventRow>>('/admin/audit-events', { params: { page: 0, size: 50 } }),
        api.get<Array<{ id: number; code: string; name: string }>>('/admin/warehouses'),
      ])
      setUsers(usersRes.data.content)
      setAuditEvents(auditRes.data.content)
      setWarehouses(whRes.data)
    } catch (err) {
      setError(getErrorMessage(err, 'Security administration data could not be loaded.'))
    }
  }

  useEffect(() => { void load() }, [])

  async function createStaffUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post('/admin/users', {
        email: email.trim(),
        fullName: fullName.trim(),
        password,
        roles: [role],
        warehouseIds: warehouseScopeId ? [Number(warehouseScopeId)] : [],
      })
      setEmail(''); setFullName(''); setPassword(''); setWarehouseScopeId('')
      setNotice('User account created with role and warehouse scope.')
      await load()
    } catch (err) {
      setError(getErrorMessage(err, 'User account could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  async function unlockOrToggleUser(u: StaffUserRow, unlockOnly: boolean) {
    setBusy(true); setError(''); setNotice('')
    try {
      await api.put('/admin/users/' + u.id, {
        fullName: u.fullName,
        enabled: unlockOnly ? u.enabled : !u.enabled,
        unlockAccount: true,
        roles: u.roles,
        warehouseIds: u.assignedWarehouseIds,
      })
      setNotice(unlockOnly ? 'Account unlocked.' : 'User status updated.')
      await load()
    } catch (err) {
      setError(getErrorMessage(err, 'User account could not be updated.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">IDENTITY, SCOPES & AUDIT</div><h1>Users & Security Audit</h1><p>Manage staff accounts, role assignments, warehouse object scopes, lockout recovery, and immutable security audit events.</p></div>
        <button className="secondary-button" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button>
      </div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}

      <section className="panel inventory-adjust-panel" style={{ marginBottom: '1.25rem' }}>
        <div className="panel-heading"><div><h2>Provision Staff Account</h2><p>Assign least-privilege roles and optional warehouse scopes.</p></div></div>
        <form className="adjustment-form" onSubmit={(e) => void createStaffUser(e)}>
          <label>Email<input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="staff@northstar.test" /></label>
          <label>Full name<input required value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Alex Rivera" /></label>
          <label>Initial password<input required type="password" minLength={10} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Min 10 chars" /></label>
          <label>Role<select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="ADMIN">ADMIN</option>
            <option value="INVENTORY_MANAGER">INVENTORY_MANAGER</option>
            <option value="PURCHASING_OFFICER">PURCHASING_OFFICER</option>
            <option value="WAREHOUSE_STAFF">WAREHOUSE_STAFF</option>
            <option value="ACCOUNTANT">ACCOUNTANT</option>
            <option value="CASHIER">CASHIER</option>
            <option value="CUSTOMER_SUPPORT">CUSTOMER_SUPPORT</option>
          </select></label>
          <label>Warehouse Scope<select value={warehouseScopeId} onChange={(e) => setWarehouseScopeId(e.target.value)}>
            <option value="">All Warehouses (Unrestricted)</option>
            {warehouses.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
          </select></label>
          <button className="primary-button" type="submit" disabled={busy}><UserPlus size={15} /> Create user</button>
        </form>
      </section>

      <section className="panel inventory-table-panel" style={{ marginBottom: '1.25rem' }}>
        <div className="panel-heading"><div><h2>Accounts & Access Scopes</h2><p>{users.length} accounts</p></div></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Roles</th>
                <th>Warehouse Scope</th>
                <th>Security Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.fullName}</strong><small>{u.email}</small></td>
                  <td>{u.roles.join(', ')}</td>
                  <td>{u.assignedWarehouseIds.length === 0 ? 'All warehouses' : `Warehouse IDs: ${u.assignedWarehouseIds.join(', ')}`}</td>
                  <td>
                    <span className={'status-pill ' + (u.enabled ? 'status-live' : 'status-muted')}>{u.enabled ? 'Active' : 'Disabled'}</span>
                    {u.lockedUntil && <span className="status-pill status-error" style={{ marginLeft: '0.4rem' }}>Locked ({u.failedLoginAttempts} fails)</span>}
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.4rem' }}>
                      {(u.lockedUntil || u.failedLoginAttempts > 0) && (
                        <button type="button" className="secondary-button" disabled={busy} onClick={() => void unlockOrToggleUser(u, true)}><Lock size={14} /> Unlock</button>
                      )}
                      <button type="button" className="secondary-button" disabled={busy} onClick={() => void unlockOrToggleUser(u, false)}>
                        {u.enabled ? 'Disable' : 'Enable'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel inventory-table-panel">
        <div className="panel-heading"><div><h2><ShieldAlert size={18} style={{ verticalAlign: 'middle', marginRight: '0.4rem' }} />Security & Operational Audit Log</h2><p>Immutable record of authentication, access changes, transfers, and financial verifications</p></div></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Event Type</th>
                <th>Actor</th>
                <th>Target</th>
                <th>Summary</th>
              </tr>
            </thead>
            <tbody>
              {auditEvents.map((ev) => (
                <tr key={ev.id}>
                  <td>{new Date(ev.occurredAt).toLocaleString()}</td>
                  <td><strong>{ev.eventType}</strong></td>
                  <td>{ev.actorEmail ?? 'System'}</td>
                  <td>{ev.targetType ? `${ev.targetType}:${ev.targetId ?? ''}` : '—'}</td>
                  <td>{ev.summary}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
