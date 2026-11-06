import { useEffect, useState, type FormEvent } from 'react'
import { Plus, Warehouse } from 'lucide-react'
import { api, getErrorMessage } from '../api/client'

interface WarehouseItem { id: number; code: string; name: string; active: boolean }

export function WarehousesPage() {
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)

  async function load() {
    try {
      const { data } = await api.get<WarehouseItem[]>('/admin/warehouses')
      setWarehouses(data)
      setError('')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Warehouses could not be loaded.'))
    }
  }

  useEffect(() => { void load() }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await api.post('/admin/warehouses', { code, name })
      setCode('')
      setName('')
      setNotice('Warehouse created.')
      await load()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Warehouse could not be created.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="page-title-row"><div><div className="eyebrow">LOCATIONS</div><h1>Warehouses</h1><p>Create and review stock locations.</p></div></div>
      {notice && <div className="notice notice-success">{notice}</div>}
      {error && <div className="notice notice-error">{error}</div>}
      <div className="warehouse-layout">
        <section className="panel"><div className="panel-heading"><div><h2>Locations</h2><p>{warehouses.length} active warehouses</p></div></div>
          {warehouses.length === 0 ? <div className="inline-empty">No warehouses have been created yet.</div> : <div className="warehouse-list">{warehouses.map((warehouse) => <article key={warehouse.id} className="warehouse-row"><span className="warehouse-icon"><Warehouse size={19} /></span><span><strong>{warehouse.name}</strong><small>{warehouse.code}</small></span><span className="status-pill status-live">Active</span></article>)}</div>}
        </section>
        <section className="panel"><div className="panel-heading"><div><h2>Add warehouse</h2><p>Warehouse codes must be unique.</p></div></div>
          <form className="form-stack warehouse-form" onSubmit={submit}><label>Warehouse name<input required maxLength={160} value={name} onChange={(event) => setName(event.target.value)} /></label><label>Code<input required maxLength={48} value={code} onChange={(event) => setCode(event.target.value)} /></label><button className="primary-button" disabled={saving}><Plus size={16} />{saving ? 'Saving…' : 'Create warehouse'}</button></form>
        </section>
      </div>
    </>
  )
}
