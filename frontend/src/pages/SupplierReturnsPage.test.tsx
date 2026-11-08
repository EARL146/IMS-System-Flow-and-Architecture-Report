import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { api } from '../api/client'
import { SupplierReturnsPage } from './SupplierReturnsPage'

const authState = vi.hoisted(() => ({ roles: ['PURCHASING_OFFICER'] as string[] }))

vi.mock('../api/client', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 9, email: 'buyer@example.test', fullName: 'Buyer User', roles: authState.roles } }),
}))

const order = { id: 21, orderNumber: 'PO-000021', supplierName: 'Harbor Supply', status: 'RECEIVED' }
const returnable = {
  purchaseOrderItemId: 41, productId: 15, sku: 'TAPE-01', description: 'Packing Tape',
  receivedQuantity: 10, returnedQuantity: 0, returnableQuantity: 10, unitCost: 125,
}
const supplierReturn = {
  id: 44, returnNumber: 'SR-000044', purchaseOrderId: 21, orderNumber: 'PO-000021', supplierName: 'Harbor Supply',
  warehouseName: 'Main Warehouse', status: 'PENDING_APPROVAL' as const, reason: 'Damaged packaging', notes: null,
  rejectionReason: null, currencyCode: 'PHP', expectedCreditAmount: 250, creditedAmount: 0,
  requestedBy: 'buyer@example.test', reviewedBy: null, dispatchedBy: null, requestedAt: '2026-10-01T08:00:00Z',
  reviewedAt: null, dispatchedAt: null, cancelledBy: null, cancelledAt: null,
  items: [{ id: 51, purchaseOrderItemId: 41, productId: 15, sku: 'TAPE-01', description: 'Packing Tape',
    quantity: 2, damagedQuantity: 1, unitCost: 125, lineTotal: 250 }],
  creditNotes: [],
}

describe('SupplierReturnsPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    authState.roles = ['PURCHASING_OFFICER']
    let created = false
    vi.mocked(api.get).mockImplementation((async (url: string) => {
      if (url === '/admin/supplier-returns') return { data: { content: created ? [supplierReturn] : [], totalElements: created ? 1 : 0 } }
      if (url === '/admin/supplier-returns/44') return { data: supplierReturn }
      if (url === '/admin/purchase-orders') return { data: { content: [order], totalElements: 1 } }
      if (url === '/admin/supplier-returns/returnable-items') return { data: [returnable] }
      return { data: { content: [], totalElements: 0 } }
    }) as never)
    vi.mocked(api.post).mockImplementation((async () => {
      created = true
      return { data: supplierReturn }
    }) as never)
  })

  it('creates a supplier return for received quantities and submits it for approval', async () => {
    const user = userEvent.setup()
    render(<SupplierReturnsPage />)
    await user.click(await screen.findByRole('button', { name: 'New supplier return' }))
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Purchase order' }), '21')
    await user.type(screen.getByRole('spinbutton', { name: 'Return quantity for TAPE-01' }), '2')
    await user.type(screen.getByRole('spinbutton', { name: 'Damaged quantity for TAPE-01' }), '1')
    await user.type(screen.getByRole('textbox', { name: 'Reason' }), 'Damaged packaging')
    await user.click(screen.getByRole('button', { name: 'Submit for approval' }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/supplier-returns', expect.objectContaining({
      purchaseOrderId: 21,
      reason: 'Damaged packaging',
      items: [{ purchaseOrderItemId: 41, quantity: 2, damagedQuantity: 1 }],
      idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/i),
    })))
    expect(await screen.findAllByText('SR-000044')).toHaveLength(2)
    expect(await screen.findAllByText('Pending approval')).toHaveLength(2)
  })
})
