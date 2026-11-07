import { Boxes, ChartNoAxesCombined, ClipboardList, LayoutDashboard, LogOut, PackageCheck, ShoppingBag, ShoppingCart, Truck, Warehouse } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const navItems = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true, roles: ['OWNER', 'ADMIN'] },
  { to: '/admin/products', label: 'Products', icon: Boxes, roles: ['OWNER', 'ADMIN'] },
  { to: '/admin/inventory', label: 'Inventory', icon: ClipboardList, roles: ['OWNER', 'ADMIN', 'INVENTORY_MANAGER'] },
  { to: '/admin/warehouses', label: 'Warehouses', icon: Warehouse, roles: ['OWNER', 'ADMIN'] },
  { to: '/admin/suppliers', label: 'Suppliers', icon: Truck, roles: ['OWNER', 'ADMIN', 'PURCHASING_OFFICER'] },
  { to: '/admin/purchasing', label: 'Purchasing', icon: PackageCheck, roles: ['OWNER', 'ADMIN', 'PURCHASING_OFFICER', 'WAREHOUSE_STAFF', 'INVENTORY_MANAGER'] },
  { to: '/admin/customer-orders', label: 'Customer orders', icon: ShoppingBag, roles: ['OWNER', 'ADMIN', 'WAREHOUSE_STAFF', 'CUSTOMER_SUPPORT', 'ACCOUNTANT', 'CASHIER'] },
  { to: '/admin/pos', label: 'Sales and POS', icon: ShoppingCart, roles: ['OWNER', 'ADMIN', 'CASHIER', 'ACCOUNTANT'] },
]

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <NavLink to="/" className="admin-brand">
          <span className="brand-mark"><Warehouse size={18} /></span>
          <span><strong>Northstar</strong><small>OPERATIONS</small></span>
        </NavLink>
        <div className="sidebar-label">WORKSPACE</div>
        <nav className="admin-nav" aria-label="Admin navigation">
          {navItems.filter(({ roles }) => user?.roles.some((role) => roles.includes(role))).map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => 'admin-nav-link ' + (isActive ? 'active' : '')}>
              <Icon size={18} strokeWidth={1.8} /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-pending">
          <span className="pending-icon"><ChartNoAxesCombined size={17} /></span>
          <strong>Purchasing is live</strong>
          <p>Record supplier orders, approvals and partial goods receipts.</p>
        </div>
        <button className="sidebar-signout" onClick={() => void signOut()}><LogOut size={17} /> Sign out</button>
      </aside>

      <div className="admin-main">
        <header className="admin-topbar">
          <div className="topbar-breadcrumb">Northstar <span>/</span> Operations</div>
          <div className="topbar-account"><span className="avatar">{user?.fullName?.slice(0, 1).toUpperCase() ?? 'U'}</span><span><strong>{user?.fullName}</strong><small>{user?.roles?.[0]?.replaceAll('_', ' ')}</small></span></div>
        </header>
        <main className="admin-content"><Outlet /></main>
      </div>
    </div>
  )
}
