import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Archive, Boxes, Plus, Search, X } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'

interface Product {
  id: number
  sku: string
  barcode: string | null
  name: string
  categoryName: string | null
  unitOfMeasure: string
  costPrice: number
  sellingPrice: number
  reorderLevel: number
  active: boolean
}
interface ProductPage { content: Product[]; totalElements: number }
interface Category { id: number; name: string; active: boolean }

const decimal = z.string().trim().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/, 'Enter a non-negative amount with up to four decimal places.')
const productSchema = z.object({
  sku: z.string().trim().min(1, 'SKU is required').max(80),
  barcode: z.string().trim().max(80).optional(),
  name: z.string().trim().min(1, 'Name is required').max(180),
  description: z.string().max(5000).optional(),
  categoryId: z.string().optional(),
  unitOfMeasure: z.string().trim().min(1).max(32),
  trackingType: z.enum(['QUANTITY', 'NONE']),
  costPrice: decimal,
  sellingPrice: decimal,
  reorderLevel: decimal,
})
type ProductFormValues = z.infer<typeof productSchema>

const blankProduct: ProductFormValues = {
  sku: '', barcode: '', name: '', description: '', categoryId: '',
  unitOfMeasure: 'each', trackingType: 'QUANTITY', costPrice: '0.00',
  sellingPrice: '0.00', reorderLevel: '0',
}

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
}

export function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [formOpen, setFormOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [filter, setFilter] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [productResult, categoryResult] = await Promise.all([
        api.get<ProductPage>('/admin/products', { params: { page: 0, size: 100 } }),
        api.get<Category[]>('/admin/categories'),
      ])
      setProducts(productResult.data.content)
      setTotal(productResult.data.totalElements)
      setCategories(categoryResult.data)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Products could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const visibleProducts = products.filter((product) => {
    const value = filter.toLowerCase()
    return !value || product.name.toLowerCase().includes(value) || product.sku.toLowerCase().includes(value)
  })

  async function archiveProduct(product: Product) {
    if (!window.confirm('Archive “' + product.name + '” from the catalog?')) return
    setError('')
    setNotice('')
    try {
      await api.delete('/admin/products/' + product.id)
      setNotice('Product archived.')
      await load()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Product could not be archived.'))
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">PRODUCT MANAGEMENT</div><h1>Products</h1><p>Manage catalog details, prices and reorder thresholds.</p></div>
        <button className="primary-button" onClick={() => setFormOpen(true)}><Plus size={17} /> Add product</button>
      </div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}

      <section className="panel">
        <div className="toolbar">
          <div className="toolbar-title"><Boxes size={18} /><strong>All products</strong><span>{total}</span></div>
          <label className="table-search"><Search size={16} /><input aria-label="Filter products" placeholder="Filter by name or SKU" value={filter} onChange={(event) => setFilter(event.target.value)} /></label>
        </div>
        {loading ? <div className="inline-empty">Loading product records…</div> : visibleProducts.length === 0 ? (
          <div className="inline-empty">{products.length === 0 ? 'No products are in the catalog yet.' : 'No products match this filter.'}</div>
        ) : (
          <div className="table-wrap"><table><thead><tr><th>Product</th><th>Category</th><th>Cost</th><th>Selling price</th><th>Reorder at</th><th>Status</th><th></th></tr></thead>
            <tbody>{visibleProducts.map((product) => <tr key={product.id}>
              <td><strong>{product.name}</strong><small>{product.sku}{product.barcode ? ' · ' + product.barcode : ''}</small></td>
              <td>{product.categoryName || 'Uncategorized'}</td>
              <td>{formatMoney(product.costPrice)}</td><td><strong>{formatMoney(product.sellingPrice)}</strong></td>
              <td>{product.reorderLevel} {product.unitOfMeasure}</td>
              <td><span className={'status-pill ' + (product.active ? 'status-live' : 'status-muted')}>{product.active ? 'Active' : 'Archived'}</span></td>
              <td>{product.active && <button className="icon-button" aria-label={'Archive ' + product.name} title="Archive product" onClick={() => void archiveProduct(product)}><Archive size={16} /></button>}</td>
            </tr>)}</tbody>
          </table></div>
        )}
      </section>

      {formOpen && <ProductForm categories={categories} onClose={() => setFormOpen(false)} onCreated={async () => { setFormOpen(false); setNotice('Product created.'); await load() }} />}
    </>
  )
}

function ProductForm({ categories, onClose, onCreated }: { categories: Category[]; onClose: () => void; onCreated: () => Promise<void> }) {
  const [error, setError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: blankProduct,
  })

  async function submit(values: ProductFormValues) {
    setError('')
    try {
      await api.post('/admin/products', {
        ...values,
        barcode: values.barcode?.trim() || null,
        description: values.description?.trim() || null,
        categoryId: values.categoryId ? Number(values.categoryId) : null,
      })
      await onCreated()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Product could not be saved.'))
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="product-modal" role="dialog" aria-modal="true" aria-labelledby="product-form-title">
        <div className="modal-heading"><div><div className="eyebrow">CATALOG RECORD</div><h2 id="product-form-title">Add a product</h2></div><button className="icon-button" aria-label="Close" onClick={onClose}><X size={18} /></button></div>
        <form onSubmit={handleSubmit(submit)} className="product-form">
          <div className="form-grid">
            <label>Product name<input {...register('name')} />{errors.name && <small className="field-error">{errors.name.message}</small>}</label>
            <label>SKU<input {...register('sku')} />{errors.sku && <small className="field-error">{errors.sku.message}</small>}</label>
            <label>Barcode <span className="optional-label">Optional</span><input {...register('barcode')} /></label>
            <label>Category<select {...register('categoryId')}><option value="">Uncategorized</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
            <label>Unit of measure<input {...register('unitOfMeasure')} /></label>
            <label>Tracking<select {...register('trackingType')}><option value="QUANTITY">Quantity</option><option value="NONE">No quantity tracking</option></select></label>
            <label>Cost price<input inputMode="decimal" {...register('costPrice')} />{errors.costPrice && <small className="field-error">{errors.costPrice.message}</small>}</label>
            <label>Selling price<input inputMode="decimal" {...register('sellingPrice')} />{errors.sellingPrice && <small className="field-error">{errors.sellingPrice.message}</small>}</label>
            <label>Reorder threshold<input inputMode="decimal" {...register('reorderLevel')} />{errors.reorderLevel && <small className="field-error">{errors.reorderLevel.message}</small>}</label>
          </div>
          <label>Description<textarea rows={3} {...register('description')} /></label>
          {error && <div className="notice notice-error">{error}</div>}
          <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save product'}</button></div>
        </form>
      </section>
    </div>
  )
}
