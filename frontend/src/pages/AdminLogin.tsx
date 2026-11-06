import { useState, type FormEvent } from 'react'
import { ArrowLeft, KeyRound, Store } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function AdminLogin() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login(email, password)
      navigate('/admin', { replace: true })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Sign in failed. Check your details and try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-shell">
      <Link to="/" className="login-back"><ArrowLeft size={16} /> Back to storefront</Link>
      <section className="login-card">
        <div className="login-mark"><KeyRound size={22} /></div>
        <div className="eyebrow">NORTHSTAR SUPPLY</div>
        <h1>Welcome back</h1>
        <p>Sign in to manage products and inventory.</p>
        <form onSubmit={submit} className="form-stack">
          <label>Email address<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          {error && <div className="notice notice-error">{error}</div>}
          <button className="primary-button full-button" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in to workspace'}
          </button>
        </form>
        <div className="login-foot"><Store size={15} /> Access is granted by your assigned business role.</div>
      </section>
    </main>
  )
}
