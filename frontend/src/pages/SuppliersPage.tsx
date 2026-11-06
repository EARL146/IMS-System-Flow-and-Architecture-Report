import { useEffect, useRef, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Archive, Building2, Pencil, Plus, Search, Truck, X } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, getErrorMessage } from '../api/client'

interface Supplier {
  id: number
  code: string
  name: string
  contactName: string | null
  email: string | null
  phone: string | null
  taxIdentifier: string | null
  addressLine1: string | null
  addressLine2: string | null
  city: string | null
  region: string | null
  postalCode: string | null
  countryCode: string | null
  notes: string | null
  active: boolean
}
interface SupplierPage { content: Supplier[]; totalElements: number }

const supplierSchema = z.object({
  code: z.string().trim().min(1, 'Supplier code is required').max(48),
  name: z.string().trim().min(1, 'Supplier name is required').max(180),
  contactName: z.string().max(140).optional(),
  email: z.union([z.literal(''), z.email('Enter a valid email address.')]).optional(),
  phone: z.string().max(40).optional(),
  taxIdentifier: z.string().max(80).optional(),
  addressLine1: z.string().max(180).optional(),
  addressLine2: z.string().max(180).optional(),
  city: z.string().max(100).optional(),
  region: z.string().max(100).optional(),
  postalCode: z.string().max(24).optional(),
  countryCode: z.union([z.literal(''), z.string().regex(/^[A-Za-z]{2}$/, 'Use a two-letter country code.')]).optional(),
  notes: z.string().max(5000).optional(),
})
type SupplierFormValues = z.infer<typeof supplierSchema>

const blankSupplier: SupplierFormValues = {
  code: '', name: '', contactName: '', email: '', phone: '', taxIdentifier: '',
  addressLine1: '', addressLine2: '', city: '', region: '', postalCode: '', countryCode: '', notes: '',
}

export function SuppliersPage() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Supplier | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const loadSequence = useRef(0)

  async function load(query = filter, pageNumber = page) {
    const requestId = ++loadSequence.current
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get<SupplierPage>('/admin/suppliers', {
        params: { page: pageNumber, size: 50, q: query.trim() || undefined },
      })
      if (requestId === loadSequence.current) {
        setSuppliers(data.content)
        setTotal(data.totalElements)
      }
    } catch (requestError) {
      if (requestId === loadSequence.current) setError(getErrorMessage(requestError, 'Suppliers could not be loaded.'))
    } finally {
      if (requestId === loadSequence.current) setLoading(false)
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(filter, page) }, 220)
    return () => window.clearTimeout(timer)
  }, [filter, page])

  async function archiveSupplier(supplier: Supplier) {
    if (!window.confirm('Archive “' + supplier.name + '”? It will no longer be available for new purchase orders.')) return
    setError('')
    setNotice('')
    try {
      await api.delete('/admin/suppliers/' + supplier.id)
      setNotice('Supplier archived.')
      await load()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Supplier could not be archived.'))
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div><div className="eyebrow">VENDOR DIRECTORY</div><h1>Suppliers</h1><p>Maintain supplier contacts and keep inactive vendors out of new orders.</p></div>
        <button className="primary-button" onClick={() => { setEditing(null); setFormOpen(true) }}><Plus size={17} /> Add supplier</button>
      </div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <section className="panel">
        <div className="toolbar">
          <div className="toolbar-title"><Truck size={18} /><strong>Supplier records</strong><span>{total}</span></div>
          <label className="table-search"><Search size={16} /><input aria-label="Search suppliers" placeholder="Search name, code or contact" value={filter} onChange={(event) => { setPage(0); setFilter(event.target.value) }} /></label>
        </div>
        {loading ? <div className="inline-empty">Loading supplier records…</div> : suppliers.length === 0 ? (
          <div className="inline-empty">{filter ? 'No suppliers match this search.' : 'No suppliers have been added yet.'}</div>
        ) : (
          <div className="table-wrap"><table><thead><tr><th>Supplier</th><th>Contact</th><th>Location</th><th>Status</th><th></th></tr></thead>
            <tbody>{suppliers.map((supplier) => <tr key={supplier.id}>
              <td><strong>{supplier.name}</strong><small>{supplier.code}{supplier.taxIdentifier ? ' · ' + supplier.taxIdentifier : ''}</small></td>
              <td>{supplier.contactName || '—'}<small>{supplier.email || supplier.phone || 'No contact details'}</small></td>
              <td>{[supplier.city, supplier.region, supplier.countryCode].filter(Boolean).join(', ') || '—'}</td>
              <td><span className={'status-pill ' + (supplier.active ? 'status-live' : 'status-muted')}>{supplier.active ? 'Active' : 'Archived'}</span></td>
              <td className="table-actions">{supplier.active && <>
                <button className="icon-button" aria-label={'Edit ' + supplier.name} title="Edit supplier" onClick={() => { setEditing(supplier); setFormOpen(true) }}><Pencil size={16} /></button>
                <button className="icon-button" aria-label={'Archive ' + supplier.name} title="Archive supplier" onClick={() => void archiveSupplier(supplier)}><Archive size={16} /></button>
              </>}</td>
            </tr>)}</tbody>
          </table></div>
        )}
        <div className="pagination-bar"><span>Page {page + 1} · {total} suppliers</span><div><button className="secondary-button" disabled={page === 0 || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button><button className="secondary-button" disabled={(page + 1) * 50 >= total || loading} onClick={() => setPage((current) => current + 1)}>Next</button></div></div>
      </section>
      {formOpen && <SupplierForm
        supplier={editing}
        onClose={() => { setFormOpen(false); setEditing(null) }}
        onSaved={async () => { setFormOpen(false); setNotice(editing ? 'Supplier updated.' : 'Supplier created.'); setEditing(null); setPage(0); await load(filter, 0) }}
      />}
    </>
  )
}

function SupplierForm({ supplier, onClose, onSaved }: { supplier: Supplier | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [error, setError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<SupplierFormValues>({
    resolver: zodResolver(supplierSchema),
    defaultValues: supplier ? {
      code: supplier.code, name: supplier.name, contactName: supplier.contactName ?? '',
      email: supplier.email ?? '', phone: supplier.phone ?? '', taxIdentifier: supplier.taxIdentifier ?? '',
      addressLine1: supplier.addressLine1 ?? '', addressLine2: supplier.addressLine2 ?? '',
      city: supplier.city ?? '', region: supplier.region ?? '', postalCode: supplier.postalCode ?? '',
      countryCode: supplier.countryCode ?? '', notes: supplier.notes ?? '',
    } : blankSupplier,
  })

  async function submit(values: SupplierFormValues) {
    setError('')
    const payload = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value?.trim() || null]))
    try {
      if (supplier) await api.put('/admin/suppliers/' + supplier.id, payload)
      else await api.post('/admin/suppliers', payload)
      await onSaved()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Supplier could not be saved.'))
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="product-modal supplier-modal" role="dialog" aria-modal="true" aria-labelledby="supplier-form-title">
        <div className="modal-heading"><div><div className="eyebrow">SUPPLIER PROFILE</div><h2 id="supplier-form-title">{supplier ? 'Edit supplier' : 'Add a supplier'}</h2></div><button className="icon-button" aria-label="Close" onClick={onClose}><X size={18} /></button></div>
        <form onSubmit={handleSubmit(submit)} className="product-form">
          <div className="form-grid">
            <label>Supplier name<input aria-label="Supplier name" {...register('name')} />{errors.name && <small className="field-error">{errors.name.message}</small>}</label>
            <label>Supplier code<input aria-label="Supplier code" {...register('code')} />{errors.code && <small className="field-error">{errors.code.message}</small>}</label>
            <label>Contact person<input {...register('contactName')} /></label>
            <label>Email<input type="email" {...register('email')} />{errors.email && <small className="field-error">{errors.email.message}</small>}</label>
            <label>Phone<input {...register('phone')} /></label>
            <label>Tax identifier<input {...register('taxIdentifier')} /></label>
            <label>Address line 1<input {...register('addressLine1')} /></label>
            <label>Address line 2<input {...register('addressLine2')} /></label>
            <label>City<input {...register('city')} /></label>
            <label>Region or province<input {...register('region')} /></label>
            <label>Postal code<input {...register('postalCode')} /></label>
            <label>Country code <span className="optional-label">2 letters</span><input {...register('countryCode')} /></label>
          </div>
          <label>Notes<textarea rows={3} {...register('notes')} /></label>
          {error && <div className="notice notice-error">{error}</div>}
          <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={isSubmitting}><Building2 size={15} />{isSubmitting ? 'Saving…' : supplier ? 'Update supplier' : 'Save supplier'}</button></div>
        </form>
      </section>
    </div>
  )
}
