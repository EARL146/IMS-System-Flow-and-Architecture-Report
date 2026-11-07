import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { api } from '../api/client'
import { CustomerCartPage, CustomerOrderDetailPage } from './CustomerPages'
import { Storefront } from './Storefront'

const authState = vi.hoisted(() => ({ roles: ['CUSTOMER'] as string[] }))

vi.mock('../api/client', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 7, email: 'buyer@example.test', fullName: 'Buyer User', roles: authState.roles } }),
}))

const cart = {
  items: [{ id: 3, productId: 8, sku: 'TAPE-08', name: 'Packing Tape', unitOfMeasure: 'each', quantity: 2, unitPrice: 25, lineTotal: 50, active: true }],
  subtotal: 50,
  totalQuantity: 2,
}

const address = {
  id: 11, label: 'Home', recipientName: 'Buyer User', phone: '+63 912 000 0000',
  addressLine1: '12 Example Street', addressLine2: null, city: 'Manila', region: 'NCR',
  postalCode: '1000', countryCode: 'PH', defaultAddress: true, createdAt: '2026-10-01T00:00:00Z',
}

const order = {
  id: 22, orderNumber: 'SO-0000000000000022', warehouseId: 5, warehouseName: 'Main Warehouse',
  status: 'PENDING', paymentMethod: 'CASH_ON_DELIVERY', paymentStatus: 'UNPAID', currencyCode: 'PHP',
  subtotal: 50, taxAmount: 0, shippingAmount: 0, discountAmount: 0, total: 50,
  addressLabel: 'Home', recipientName: 'Buyer User', recipientPhone: '+63 912 000 0000',
  addressLine1: '12 Example Street', addressLine2: null, city: 'Manila', region: 'NCR',
  postalCode: '1000', countryCode: 'PH', notes: null, cancelledAt: null, createdAt: '2026-11-06T00:00:00Z',
  items: [{ id: 31, lineNumber: 1, productId: 8, sku: 'TAPE-08', productName: 'Packing Tape', unitOfMeasure: 'each', quantity: 2, unitPrice: 25, lineTotal: 50 }],
}

describe('customer portal', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    authState.roles = ['CUSTOMER']
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('5f9b703a-63f0-4a3d-85aa-30918c69fb81')
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url.endsWith('/customer/cart')) return { data: cart }
      if (url.endsWith('/customer/addresses')) return { data: [address] }
      if (url.endsWith('/catalog/warehouses')) return { data: [{ id: 5, code: 'WH-05', name: 'Main Warehouse' }] }
      return { data: { content: [], totalElements: 0 } }
    }) as never)
    vi.mocked(api.post).mockImplementation((async (_url: string, payload?: { paymentMethod?: string }) => ({
      data: payload?.paymentMethod === 'MANUAL_TRANSFER'
        ? { ...order, paymentMethod: 'MANUAL_TRANSFER', paymentStatus: 'AWAITING_VERIFICATION' }
        : order,
    })) as never)
  })

  it('places checkout with the selected fulfillment details and an idempotency key', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><CustomerCartPage /></MemoryRouter>)

    expect(await screen.findByText('Packing Tape')).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Payment method' }), 'MANUAL_TRANSFER')
    await user.type(screen.getByRole('textbox', { name: 'Order notes Optional' }), 'Leave at the front desk')
    await user.click(screen.getByRole('button', { name: 'Place order' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/customer/checkout', {
      addressId: 11,
      warehouseId: 5,
      paymentMethod: 'MANUAL_TRANSFER',
      notes: 'Leave at the front desk',
      idempotencyKey: '5f9b703a-63f0-4a3d-85aa-30918c69fb81',
    }))
    expect(await screen.findByText('SO-0000000000000022')).toBeInTheDocument()
    expect(screen.getByText(/not marked paid/i)).toBeInTheDocument()
  })

  it('adds a current catalog product to the authenticated customer cart', async () => {
    const user = userEvent.setup()
    vi.mocked(api.get).mockResolvedValue({ data: {
      content: [{ id: 8, sku: 'TAPE-08', name: 'Packing Tape', description: 'Strong carton tape', categoryName: 'Packing', unitOfMeasure: 'each', sellingPrice: 25, active: true }],
      totalElements: 1,
    } } as never)
    render(<MemoryRouter><Storefront /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/customer/cart/items', { productId: 8, quantity: 1 }))
    expect(await screen.findByRole('status')).toHaveTextContent('Added to cart')
  })

  it('shows the status timeline on the customer order detail page', async () => {
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url.endsWith('/customer/orders/22/history')) return { data: [
        { id: 1, fromStatus: null, toStatus: 'PENDING', changedBy: 'Buyer User', note: 'Order placed', occurredAt: '2026-11-06T00:00:00Z' },
      ] }
      if (url.endsWith('/customer/orders/22')) return { data: order }
      return { data: [] }
    }) as never)

    render(<MemoryRouter initialEntries={['/account/orders/22']}><Routes><Route path="/account/orders/:id" element={<CustomerOrderDetailPage />} /></Routes></MemoryRouter>)

    expect(await screen.findByText('Order progress')).toBeInTheDocument()
    expect(await screen.findAllByText('Order placed')).toHaveLength(2)
  })
})
