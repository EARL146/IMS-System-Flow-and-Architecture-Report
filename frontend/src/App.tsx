import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AdminLayout } from './pages/AdminLayout'
import { AdminLogin } from './pages/AdminLogin'
import { DashboardPage } from './pages/DashboardPage'
import { InventoryPage } from './pages/InventoryPage'
import { ProductsPage } from './pages/ProductsPage'
import { Storefront } from './pages/Storefront'
import { WarehousesPage } from './pages/WarehousesPage'

function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-state">Restoring your session…</div>
  if (!user) return <Navigate to="/admin/login" replace />
  const canManage = user.roles.some((role) =>
    ['OWNER', 'ADMIN', 'INVENTORY_MANAGER'].includes(role),
  )
  if (!canManage) return <div className="page-state">This account does not have admin access.</div>
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
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="warehouses" element={<WarehousesPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
