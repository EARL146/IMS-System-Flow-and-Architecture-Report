import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { api } from '../api/client'
import { CustomerOrdersAdminPage } from './CustomerOrdersAdminPage'

const authState = vi.hoisted(() => ({ roles: ['WAREHOUSE_STAFF'] as string[] }))

vi.mock('../api/client', () => ({
  api: { get: vi.fn(), post: vi.fn() },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 9, email: 'staff@example.test', fullName: 'Staff User', roles: authState.roles } }),
}))

function makeOrder(overrides: Record<string, unknown> = {}) {
  return {
    id: 35, orderNumber: 'SO-A102', customerEmail: 'customer@example.test', warehouseName: 'Main Warehouse',
    status: 'PROCESSING', paymentMethod: 'CASH_ON_DELIVERY', paymentStatus: 'UNPAID', currencyCode: 'PHP', total: 1250,
    recipientName: 'Customer Name', recipientPhone: '+63 912 000 0000', addressLine1: '12 Example Street',
    addressLine2: null, city: 'Manila', region: 'NCR', postalCode: '1000', countryCode: 'PH', notes: null,
    createdAt: '2026-11-01T08:00:00Z', items: [{ id: 1, sku: 'ITEM-01', productName: 'Packing tape', unitOfMeasure: 'roll', quantity: 2, unitPrice: 625, lineTotal: 1250 }],
    ...overrides,
  }
}

let order = makeOrder()

describe('Customer order management page', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    order = makeOrder()
    authState.roles = ['WAREHOUSE_STAFF']
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url.endsWith('/history')) return { data: [{ id: 1, fromStatus: null, toStatus: 'PENDING', changedBy: 'Customer', note: 'Order placed', occurredAt: '2026-11-01T08:00:00Z' }] }
      return { data: { content: [order], totalElements: 1, totalPages: 1 } }
    }) as never)
    vi.mocked(api.post).mockImplementation((async (url: string, payload: { status?: string }) => {
      if (url.endsWith('/status')) order = { ...order, status: payload.status! }
      if (url.endsWith('/payments/verify')) order = { ...order, paymentStatus: 'PAID' }
      return { data: order }
    }) as never)
  })

  it('lets warehouse staff advance an order through the allowed fulfillment transition', async () => {
    const user = userEvent.setup()
    render(<CustomerOrdersAdminPage />)
    expect(await screen.findAllByText('SO-A102')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Shipped' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/customer-orders/35/status', {
      status: 'SHIPPED', note: null,
    }))
    expect(await screen.findByText('Order moved to Shipped.')).toBeInTheDocument()
  })

  it('lets accounting staff record a full manual transfer verification', async () => {
    authState.roles = ['ACCOUNTANT']
    order = makeOrder({ status: 'PENDING', paymentMethod: 'MANUAL_TRANSFER', paymentStatus: 'AWAITING_VERIFICATION' })
    const user = userEvent.setup()
    render(<CustomerOrdersAdminPage />)
    await user.click(await screen.findByRole('button', { name: 'Verify full payment' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/customer-orders/35/payments/verify', expect.objectContaining({
      amount: 1250,
      reference: null,
      notes: null,
      idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/i),
    })))
  })
})
