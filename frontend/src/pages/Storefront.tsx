import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, CircleUserRound, Search, ShieldCheck, ShoppingBag, Store } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'

interface CatalogProduct {
  id: number
  sku: string
  name: string
  description: string | null
  categoryName: string | null
  unitOfMeasure: string
  sellingPrice: number
  active: boolean
}

interface ProductPage {
  content: CatalogProduct[]
  totalElements: number
}

function money(value: number) {
  return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
}

export function Storefront() {
  const [products, setProducts] = useState<CatalogProduct[]>([])
  const [total, setTotal] = useState(0)
  const [searchValue, setSearchValue] = useState('')
  const [query, setQuery] = useState('')
  const [retry, setRetry] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let current = true
    setLoading(true)
    setError('')
    api
      .get<ProductPage>('/catalog/products', { params: { q: query || undefined, page: 0, size: 24 } })
      .then(({ data }) => {
        if (!current) return
        setProducts(data.content)
        setTotal(data.totalElements)
      })
      .catch((requestError: unknown) => {
        if (current) setError(getErrorMessage(requestError, 'The catalog could not be loaded.'))
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [query, retry])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery(searchValue.trim())
  }

  return (
    <div className="storefront">
      <div className="announcement">A clearer view of the products your business needs.</div>
      <header className="store-header">
        <Link className="brand-lockup" to="/" aria-label="Northstar Supply home">
          <span className="brand-mark"><Store size={20} /></span>
          <span><strong>Northstar</strong><small>SUPPLY CO.</small></span>
        </Link>
        <form className="store-search" onSubmit={submitSearch}>
          <Search size={18} aria-hidden="true" />
          <input aria-label="Search products" placeholder="Search the catalog" value={searchValue} onChange={(event) => setSearchValue(event.target.value)} />
          <button type="submit">Search</button>
        </form>
        <nav className="store-actions" aria-label="Staff access">
          <Link to="/admin/login" className="quiet-link"><CircleUserRound size={19} /> Staff sign in</Link>
          <button className="cart-link" aria-label="Customer purchasing is not available yet" title="Customer purchasing is in progress" disabled><ShoppingBag size={19} /></button>
        </nav>
      </header>

      <main>
        <section className="store-hero">
          <div className="hero-copy">
            <div className="eyebrow"><span className="eyebrow-dot" /> BUSINESS SUPPLIES, IN ONE PLACE</div>
            <h1>Good work starts with the right stock.</h1>
            <p>Explore the live product catalog and find what your team needs to keep moving.</p>
            <form className="hero-search" onSubmit={submitSearch}>
              <Search size={19} aria-hidden="true" />
              <input aria-label="Search products" placeholder="Try a product name or SKU" value={searchValue} onChange={(event) => setSearchValue(event.target.value)} />
              <button type="submit">Explore <ArrowRight size={16} /></button>
            </form>
            <div className="hero-trust"><ShieldCheck size={16} /> Catalog prices are confirmed by the store service</div>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="art-orbit orbit-one" />
            <div className="art-orbit orbit-two" />
            <div className="art-box box-back" />
            <div className="art-box box-front"><span>NORTHSTAR</span><Store size={34} /></div>
            <div className="art-spark spark-one" />
            <div className="art-spark spark-two" />
            <div className="art-label">READY FOR<br />WHAT'S NEXT</div>
          </div>
        </section>

        <section className="catalog-section">
          <div className="section-heading">
            <div>
              <div className="eyebrow">THE CATALOG</div>
              <h2>{query ? 'Results for “' + query + '”' : 'Browse products'}</h2>
              <p>{total ? total + ' products in the catalog' : 'Current products published by the store'}</p>
            </div>
            {query && <button className="text-button" onClick={() => { setSearchValue(''); setQuery('') }}>Clear search</button>}
          </div>

          {loading && <div className="catalog-message">Loading the live catalog…</div>}
          {!loading && error && <div className="notice notice-error">{error}<button onClick={() => setRetry((value) => value + 1)}>Try again</button></div>}
          {!loading && !error && products.length === 0 && (
            <div className="catalog-empty"><ShoppingBag size={28} /><strong>No products found</strong><span>Try another product name or SKU.</span></div>
          )}
          {!loading && !error && products.length > 0 && (
            <div className="product-grid">
              {products.map((product) => (
                <article className="product-card" key={product.id}>
                  <div className="product-image-placeholder">
                    <div className="product-image-glow" />
                    <ShoppingBag size={30} strokeWidth={1.35} />
                    <span>IMAGE PENDING</span>
                  </div>
                  <div className="product-card-content">
                    <div className="product-meta"><span>{product.categoryName ?? 'Uncategorized'}</span><span>SKU {product.sku}</span></div>
                    <h3>{product.name}</h3>
                    <p>{product.description || 'Product details will be available soon.'}</p>
                    <div className="product-card-bottom">
                      <div><small>Price</small><strong>{money(product.sellingPrice)}</strong></div>
                      <span className="unit-tag">/ {product.unitOfMeasure}</span>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="service-band">
          <div><span className="service-icon"><ShieldCheck size={18} /></span><span><strong>Reliable information</strong><small>Catalog data comes from the inventory service.</small></span></div>
          <div><span className="service-icon"><ShoppingBag size={18} /></span><span><strong>Ordering is in progress</strong><small>Checkout will open when order and payment APIs are ready.</small></span></div>
          <Link to="/admin/login">Staff workspace <ArrowRight size={16} /></Link>
        </section>
      </main>

      <footer className="store-footer">
        <Link className="brand-lockup" to="/"><span className="brand-mark"><Store size={17} /></span><span><strong>Northstar</strong><small>SUPPLY CO.</small></span></Link>
        <span>Inventory management foundation · customer ordering is being built</span>
        <Link to="/admin/login">Staff sign in</Link>
      </footer>
    </div>
  )
}
