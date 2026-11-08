import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { api } from '../api/client'
import { PurchaseOrdersPage } from './PurchaseOrdersPage'
import { SuppliersPage } from './SuppliersPage'

const authState = vi.hoisted(() => ({ roles: ['WAREHOUSE_STAFF'] as string[] }))

vi.mock('../api/client', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 9, email: 'staff@example.test', fullName: 'Staff User', roles: authState.roles } }),
}))

const supplier = {
  id: 1, code: 'SUP-01', name: 'Harbor Supply', contactName: 'Mina', email: 'mina@example.test',
  phone: '+63 2 555 0100', taxIdentifier: null, addressLine1: null, addressLine2: null,
  city: 'Manila', region: 'NCR', postalCode: null, countryCode: 'PH', notes: null, active: true,
}
const product = { id: 15, sku: 'TAPE-01', name: 'Packing Tape', costPrice: 125, unitOfMeasure: 'each', active: true }

const makeOrder = () => ({
  id: 21, orderNumber: 'PO-000021', supplierId: 1, supplierName: 'Harbor Supply',
  warehouseId: 3, warehouseName: 'Main Warehouse', status: 'ORDERED' as 'ORDERED' | 'PARTIALLY_RECEIVED', currencyCode: 'PHP',
  expectedDate: null, notes: null, rejectionReason: null, createdBy: 'buyer@example.test', reviewedBy: 'owner@example.test',
  reviewedAt: null, orderedAt: '2026-10-01T08:00:00Z', createdAt: '2026-09-30T08:00:00Z', total: 1250,
  items: [{ id: 41, productId: 15, sku: 'TAPE-01', description: 'Packing Tape', orderedQuantity: 10, receivedQuantity: 0, outstandingQuantity: 10, unitCost: 125, lineTotal: 1250 }],
})

let currentOrder = makeOrder()

describe('Purchasing pages', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    authState.roles = ['WAREHOUSE_STAFF']
    currentOrder = makeOrder()
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url.endsWith('/admin/purchase-orders/21')) return { data: currentOrder }
      if (url.endsWith('/admin/purchase-orders')) return { data: { content: [currentOrder], totalElements: 1 } }
      if (url.endsWith('/admin/suppliers')) return { data: { content: [supplier], totalElements: 1 } }
      if (url.endsWith('/admin/warehouses')) return { data: [{ id: 3, code: 'WH-01', name: 'Main Warehouse', active: true }] }
      if (url.endsWith('/admin/products')) return { data: { content: [product], totalElements: 1 } }
      return { data: { content: [supplier], totalElements: 1 } }
    }) as never)
    vi.mocked(api.post).mockImplementation((async (url: string) => {
      if (url.endsWith('/receive')) {
        currentOrder = { ...currentOrder, status: 'PARTIALLY_RECEIVED', items: [{ ...currentOrder.items[0], receivedQuantity: 3, outstandingQuantity: 7 }] }
      }
      return { data: currentOrder }
    }) as never)
  })

  it('creates a supplier profile after validating required fields', async () => {
    const user = userEvent.setup()
    render(<SuppliersPage />)
    expect(await screen.findByText('Harbor Supply')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add supplier' }))
    await user.click(screen.getByRole('button', { name: 'Save supplier' }))
    expect(await screen.findByText('Supplier name is required')).toBeInTheDocument()
    expect(api.post).not.toHaveBeenCalled()

    await user.type(screen.getByRole('textbox', { name: 'Supplier name' }), 'North Imports')
    await user.type(screen.getByRole('textbox', { name: 'Supplier code' }), 'NORTH-1')
    await user.click(screen.getByRole('button', { name: 'Save supplier' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/suppliers', expect.objectContaining({
      name: 'North Imports', code: 'NORTH-1', countryCode: null,
    })))
  })

  it('records a partial receipt with an idempotency key as warehouse staff', async () => {
    const user = userEvent.setup()
    render(<PurchaseOrdersPage />)
    expect(await screen.findByText('PO-000021')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'View' }))
    expect(await screen.findByText('Order details')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Receive goods' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Receive quantity for TAPE-01' }), '3')
    await user.click(screen.getByRole('button', { name: 'Record receipt' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/purchase-orders/21/receive', expect.objectContaining({
      items: [{ purchaseOrderItemId: 41, quantity: 3 }],
      idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/i),
    })))
    expect(await screen.findAllByText('Partially received')).toHaveLength(2)
  })

  it('requires a reason to cancel unreceived quantities after an order is sent', async () => {
    authState.roles = ['ADMIN']
    const user = userEvent.setup()
    render(<PurchaseOrdersPage />)
    await user.click(await screen.findByRole('button', { name: 'View' }))
    await user.type(screen.getByRole('textbox', { name: 'Cancellation reason' }), 'Supplier cancellation agreed')
    await user.click(screen.getByRole('button', { name: 'Cancel unreceived quantities' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/purchase-orders/21/cancel', {
      reason: 'Supplier cancellation agreed',
    }))
  })

  it('creates a draft order from active supplier, warehouse and product data', async () => {
    authState.roles = ['PURCHASING_OFFICER']
    const user = userEvent.setup()
    render(<PurchaseOrdersPage />)
    await user.click(await screen.findByRole('button', { name: 'New purchase order' }))
    await screen.findByRole('option', { name: 'Harbor Supply · SUP-01' })
    await screen.findByRole('option', { name: 'Main Warehouse · WH-01' })
    await screen.findByRole('option', { name: 'Packing Tape · TAPE-01' })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Supplier' }), '1')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Warehouse' }), '3')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Product 1' }), '15')
    await user.click(screen.getByRole('button', { name: 'Create draft order' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/purchase-orders', expect.objectContaining({
      supplierId: 1,
      warehouseId: 3,
      currencyCode: 'PHP',
      items: [{ productId: 15, quantity: 1, unitCost: 125 }],
    })))
  })
})
