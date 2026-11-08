import { Navigate, Route, Routes } from 'react-router-dom'
import { ReturnsAdminPage } from './pages/ReturnsAdminPage'
import { CustomerReturnsPage } from './pages/CustomerReturnsPage'
import { useAuth } from './auth/AuthContext'
import { AdminLayout } from './pages/AdminLayout'
import { AdminLogin } from './pages/AdminLogin'
import { DashboardPage } from './pages/DashboardPage'
import { InventoryPage } from './pages/InventoryPage'
import { PurchaseOrdersPage } from './pages/PurchaseOrdersPage'
import { CustomerOrdersAdminPage } from './pages/CustomerOrdersAdminPage'
import { PosPage } from './pages/PosPage'
import { ProductsPage } from './pages/ProductsPage'
import { SuppliersPage } from './pages/SuppliersPage'
import { Storefront } from './pages/Storefront'
import { WarehousesPage } from './pages/WarehousesPage'
import { CustomerLoginPage, CustomerRegisterPage } from './pages/CustomerAuthPages'
import {
  CustomerAccountHome,
  CustomerAddressesPage,
  CustomerCartPage,
  CustomerLayout,
  CustomerOrderDetailPage,
  CustomerOrdersPage,
} from './pages/CustomerPages'

function RequireStaff({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-state">Restoring your session…</div>
  if (!user) return <Navigate to="/admin/login" replace />
  const canAccessStaffArea = user.roles.some((role) =>
    ['OWNER', 'ADMIN', 'INVENTORY_MANAGER', 'PURCHASING_OFFICER', 'WAREHOUSE_STAFF', 'CUSTOMER_SUPPORT', 'ACCOUNTANT', 'CASHIER'].includes(role),
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

function RequireCustomer({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-state">Restoring your session…</div>
  if (!user) return <Navigate to="/login" replace />
  if (!user.roles.includes('CUSTOMER')) return <div className="page-state">This account does not have customer access.</div>
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Storefront />} />
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/login" element={<CustomerLoginPage />} />
      <Route path="/register" element={<CustomerRegisterPage />} />
      <Route element={<RequireCustomer><CustomerLayout /></RequireCustomer>}>
        <Route path="cart" element={<CustomerCartPage />} />
        <Route path="account" element={<CustomerAccountHome />} />
        <Route path="account/addresses" element={<CustomerAddressesPage />} />
        <Route path="account/orders" element={<CustomerOrdersPage />} />
        <Route path="account/orders/:id" element={<CustomerOrderDetailPage />} />
        <Route path="/account/returns" element={<CustomerReturnsPage />} />
      </Route>
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
        <Route path="customer-orders" element={<RequireRoles roles={['OWNER', 'ADMIN', 'WAREHOUSE_STAFF', 'CUSTOMER_SUPPORT', 'ACCOUNTANT', 'CASHIER']}><CustomerOrdersAdminPage /></RequireRoles>} />
        <Route path="pos" element={<RequireRoles roles={['OWNER', 'ADMIN', 'CASHIER', 'ACCOUNTANT']}><PosPage /></RequireRoles>} />
        <Route path="returns" element={<RequireRoles roles={['OWNER', 'ADMIN', 'CUSTOMER_SUPPORT', 'WAREHOUSE_STAFF', 'INVENTORY_MANAGER', 'CASHIER', 'ACCOUNTANT']}><ReturnsAdminPage /></RequireRoles>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
