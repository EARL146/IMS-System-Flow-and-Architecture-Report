import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { CustomerReturnsPage } from './CustomerReturnsPage'
import { ReturnsAdminPage } from './ReturnsAdminPage'

const approvedReturn = {
  id: 5, returnNumber: 'RT-TEST-0001', sourceType: 'CUSTOMER_ORDER', sourceNumber: 'ORD-1001',
  customerName: 'Test Customer', customerEmail: 'customer@example.test', status: 'APPROVED',
  reason: 'Wrong size', notes: null, rejectionReason: null, currencyCode: 'PHP', refundAmount: 0,
  requestedAt: '2026-11-08T01:00:00Z', refund: null,
  items: [{ id: 12, sourceItemId: 7, sku: 'TEE-1', productName: 'Cotton Shirt', unitOfMeasure: 'piece',
    unitPrice: 250, requestedQuantity: 1, approvedQuantity: 1, restockQuantity: 0, damagedQuantity: 0, scrapQuantity: 0 }],
}

describe('returns pages', () => {
  it('submits a customer return request for selected order lines', async () => {
    const client = { get: vi.fn(async (url: string) => {
      if (url === '/customer/orders') return { data: { content: [{ id: 22, orderNumber: 'ORD-1001', status: 'DELIVERED', paymentStatus: 'PAID' }] } } as never
      if (url === '/customer/returns') return { data: { content: [] } } as never
      if (url === '/customer/orders/22') return { data: { id: 22, orderNumber: 'ORD-1001', status: 'DELIVERED', paymentStatus: 'PAID', items: [{ id: 7, sku: 'TEE-1', productName: 'Cotton Shirt', unitOfMeasure: 'piece', quantity: 3, unitPrice: 250 }] } } as never
      throw new Error('Unexpected GET ' + url)
    }), post: vi.fn().mockResolvedValue({ data: {} }) }

    render(<CustomerReturnsPage client={client as never} />)
    await screen.findByText('ORD-1001 · DELIVERED')
    fireEvent.change(screen.getByLabelText('Order'), { target: { value: '22' } })
    await screen.findByText('Cotton Shirt')
    fireEvent.change(screen.getByLabelText('Quantity to return Cotton Shirt'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Wrong size' } })
    fireEvent.click(screen.getByRole('button', { name: 'Submit return request' }))

    await waitFor(() => expect(client.post).toHaveBeenCalledWith('/customer/orders/22/returns', expect.objectContaining({
      reason: 'Wrong size', items: [{ sourceItemId: 7, quantity: '1' }],
    })))
  })

  it('records inspected quantities by disposition for an approved return', async () => {
    const client = { get: vi.fn(async (url: string) => {
      if (url === '/admin/returns') return { data: { content: [approvedReturn] } } as never
      if (url === '/admin/returns/5') return { data: approvedReturn } as never
      throw new Error('Unexpected GET ' + url)
    }), post: vi.fn().mockResolvedValue({ data: approvedReturn }) }

    render(<ReturnsAdminPage client={client as never} />)
    await screen.findAllByText('RT-TEST-0001')
    fireEvent.change(screen.getByLabelText('damaged quantity Cotton Shirt'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('restock quantity Cotton Shirt'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save inspection' }))

    await waitFor(() => expect(client.post).toHaveBeenCalledWith('/admin/returns/5/inspect', {
      items: [{ returnItemId: 12, restockQuantity: '0', damagedQuantity: '1', scrapQuantity: '0' }],
    }))
  })
})
