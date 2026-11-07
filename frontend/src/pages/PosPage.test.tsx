import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { api } from '../api/client'
import { PosPage } from './PosPage'

const authState = vi.hoisted(() => ({ roles: ['CASHIER'] as string[] }))

vi.mock('../api/client', () => ({
  api: { get: vi.fn(), post: vi.fn() },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 9, email: 'cashier@example.test', fullName: 'Casey Cashier', roles: authState.roles } }),
}))

const product = {
  id: 12, sku: 'TAPE-01', barcode: '123456', name: 'Packing Tape', categoryName: 'Packing',
  unitOfMeasure: 'roll', trackingType: 'QUANTITY', sellingPrice: 100, availableQuantity: 10,
}

const emptyPage = { content: [], totalElements: 0, totalPages: 0 }

function receipt(paymentMethod: 'CASH' | 'CARD_TERMINAL', amountTendered: number | null, paymentReference: string | null) {
  return {
    id: 61, saleNumber: 'POS-000061', status: 'COMPLETED', createdAt: '2026-11-07T08:00:00Z',
    warehouseId: 3, warehouseName: 'Main Warehouse', cashierName: 'Casey Cashier', customerId: null,
    customerEmail: null, customerName: null, paymentMethod, paymentStatus: 'PAID', paymentReference,
    currencyCode: 'PHP', subtotal: 100, discountAmount: 0, taxAmount: 12, total: 112,
    amountTendered, changeGiven: amountTendered === null ? 0 : amountTendered - 112,
    items: [{ id: 71, lineNumber: 1, productId: 12, sku: 'TAPE-01', productName: 'Packing Tape', unitOfMeasure: 'roll', quantity: 1, unitPrice: 100, lineTotal: 100 }],
  }
}

describe('Point of sale page', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    authState.roles = ['CASHIER']
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url === '/catalog/warehouses') return { data: [{ id: 3, code: 'WH-01', name: 'Main Warehouse' }] }
      if (url === '/admin/pos/config') return { data: { taxRate: 0.12, currencyCode: 'PHP' } }
      if (url === '/admin/pos/products') return { data: { content: [product], totalElements: 1, totalPages: 1 } }
      return { data: emptyPage }
    }) as never)
  })

  it('completes a cash sale using the server priced cart and prints its receipt', async () => {
    const user = userEvent.setup()
    const sale = receipt('CASH', 120, null)
    vi.mocked(api.post).mockResolvedValue({ data: sale } as never)
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    render(<PosPage />)

    await user.click(await screen.findByRole('button', { name: 'Add Packing Tape' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Cash received' }), '120')
    await user.click(screen.getByRole('button', { name: 'Complete sale' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/pos/sales', expect.objectContaining({
      idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/i),
      warehouseId: 3,
      items: [{ productId: 12, quantity: 1 }],
      discountAmount: 0,
      paymentMethod: 'CASH',
      amountTendered: 120,
      paymentReference: null,
    })))
    expect(await screen.findAllByText('POS-000061')).toHaveLength(2)
    expect(screen.getByText('Thank you for your purchase.')).toBeInTheDocument()
    expect(screen.getByText('Change')).toBeInTheDocument()
    expect(screen.getAllByText(/8\.00/).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Print receipt' }))
    expect(print).toHaveBeenCalledOnce()
  })

  it('records external terminal approval without sending card or tender details', async () => {
    const user = userEvent.setup()
    vi.mocked(api.post).mockResolvedValue({ data: receipt('CARD_TERMINAL', null, 'TERM-912') } as never)
    render(<PosPage />)

    await user.click(await screen.findByRole('button', { name: 'Add Packing Tape' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Payment method' }), 'CARD_TERMINAL')
    await user.type(screen.getByRole('textbox', { name: 'Approved terminal reference' }), 'TERM-912')
    await user.click(screen.getByRole('button', { name: 'Complete sale' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/pos/sales', expect.objectContaining({
      paymentMethod: 'CARD_TERMINAL',
      amountTendered: null,
      paymentReference: 'TERM-912',
    })))
    const payload = vi.mocked(api.post).mock.calls[0][1] as Record<string, unknown>
    expect(payload).not.toHaveProperty('cardNumber')
    expect(payload).not.toHaveProperty('cardToken')
    expect(await screen.findAllByText('POS-000061')).toHaveLength(2)
  })

  it('shows accountants sale history and receipts without checkout controls', async () => {
    const user = userEvent.setup()
    authState.roles = ['ACCOUNTANT']
    const sale = receipt('CASH', 120, null)
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url === '/catalog/warehouses') return { data: [] }
      if (url === '/admin/pos/config') return { data: { taxRate: 0.12, currencyCode: 'PHP' } }
      if (url === '/admin/pos/sales') return { data: { content: [sale], totalElements: 1, totalPages: 1 } }
      return { data: emptyPage }
    }) as never)
    render(<PosPage />)

    expect(await screen.findByRole('heading', { name: 'Sales history' })).toBeInTheDocument()
    expect(await screen.findByText('POS-000061')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Complete sale' })).not.toBeInTheDocument()
    expect(api.get).not.toHaveBeenCalledWith('/admin/pos/products', expect.anything())
    await user.click(screen.getByRole('button', { name: 'Receipt' }))
    expect(await screen.findByText('Thank you for your purchase.')).toBeInTheDocument()
  })
})
