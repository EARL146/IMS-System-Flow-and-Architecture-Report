import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AdminLayout } from './pages/AdminLayout'
import { AdminLogin } from './pages/AdminLogin'
import { DashboardPage } from './pages/DashboardPage'
import { InventoryPage } from './pages/InventoryPage'
import { PurchaseOrdersPage } from './pages/PurchaseOrdersPage'
import { ProductsPage } from './pages/ProductsPage'
import { SuppliersPage } from './pages/SuppliersPage'
import { Storefront } from './pages/Storefront'
import { WarehousesPage } from './pages/WarehousesPage'

function RequireStaff({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-state">Restoring your session…</div>
  if (!user) return <Navigate to="/admin/login" replace />
  const canAccessStaffArea = user.roles.some((role) =>
    ['OWNER', 'ADMIN', 'INVENTORY_MANAGER', 'PURCHASING_OFFICER', 'WAREHOUSE_STAFF'].includes(role),
  )
  if (!canAccessStaffArea) return <div className="page-state">This account does not have staff access.</div>
  return children
}

function RequireRoles({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const { user } = useAuth()
  if (!user?.roles.some((role) => roles.includes(role))) {
    return <div className="page-state">This account does not have permission to view this page.</div>
  }
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Storefront />} />
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route
        path="/admin"
        element={
          <RequireStaff>
            <AdminLayout />
          </RequireStaff>
        }
      >
        <Route index element={<RequireRoles roles={['OWNER', 'ADMIN']}><DashboardPage /></RequireRoles>} />
        <Route path="products" element={<RequireRoles roles={['OWNER', 'ADMIN']}><ProductsPage /></RequireRoles>} />
        <Route path="inventory" element={<RequireRoles roles={['OWNER', 'ADMIN', 'INVENTORY_MANAGER']}><InventoryPage /></RequireRoles>} />
        <Route path="warehouses" element={<RequireRoles roles={['OWNER', 'ADMIN']}><WarehousesPage /></RequireRoles>} />
        <Route path="suppliers" element={<RequireRoles roles={['OWNER', 'ADMIN', 'PURCHASING_OFFICER']}><SuppliersPage /></RequireRoles>} />
        <Route path="purchasing" element={<RequireRoles roles={['OWNER', 'ADMIN', 'PURCHASING_OFFICER', 'WAREHOUSE_STAFF', 'INVENTORY_MANAGER']}><PurchaseOrdersPage /></RequireRoles>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
