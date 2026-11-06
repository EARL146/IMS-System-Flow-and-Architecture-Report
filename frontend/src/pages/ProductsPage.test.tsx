import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '@testing-library/jest-dom/vitest'
import { api } from '../api/client'
import { ProductsPage } from './ProductsPage'

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  getErrorMessage: (_error: unknown, fallback: string) => fallback,
}))

const product = {
  id: 1,
  sku: 'LAMP-01',
  barcode: null,
  name: 'Desk Lamp',
  description: 'Adjustable task lighting',
  categoryId: 3,
  categoryName: 'Lighting',
  unitOfMeasure: 'each',
  trackingType: 'QUANTITY' as const,
  costPrice: 12.5,
  sellingPrice: 24.99,
  reorderLevel: 3,
  active: true,
}

describe('ProductsPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.get).mockImplementation((async (url: string) => ({
      data: url.endsWith('/admin/categories')
        ? [{ id: 3, name: 'Lighting', active: true }]
        : { content: [product], totalElements: 1 },
    })) as never)
  })

  it('shows API products and filters them by name or SKU', async () => {
    const user = userEvent.setup()
    render(<ProductsPage />)

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('LAMP-01')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Filter products' }), 'missing')
    expect(screen.getByText('No products match this filter.')).toBeInTheDocument()
  })

  it('validates required product fields before submitting', async () => {
    const user = userEvent.setup()
    render(<ProductsPage />)

    await screen.findByText('Desk Lamp')
    await user.click(screen.getByRole('button', { name: 'Add product' }))
    await user.click(screen.getByRole('button', { name: 'Save product' }))

    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('SKU is required')).toBeInTheDocument()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('loads the existing values and updates a selected product', async () => {
    const user = userEvent.setup()
    render(<ProductsPage />)

    await screen.findByText('Desk Lamp')
    await user.click(screen.getByRole('button', { name: 'Edit Desk Lamp' }))
    expect(screen.getByRole('textbox', { name: 'Product name' })).toHaveValue('Desk Lamp')
    expect(screen.getByRole('combobox', { name: 'Category' })).toHaveValue('3')

    const name = screen.getByRole('textbox', { name: 'Product name' })
    await user.clear(name)
    await user.type(name, 'Desk Lamp Pro')
    await user.click(screen.getByRole('button', { name: 'Update product' }))

    expect(api.put).toHaveBeenCalledWith('/admin/products/1', expect.objectContaining({
      name: 'Desk Lamp Pro',
      categoryId: 3,
    }))
  })
})
