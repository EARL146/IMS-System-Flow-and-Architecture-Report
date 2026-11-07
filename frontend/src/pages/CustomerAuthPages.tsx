import { useState, type FormEvent } from 'react'
import { ArrowLeft, KeyRound, Store, UserRoundPlus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function CustomerLoginPage() {
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
      const user = await login(email, password)
      navigate(user.roles.includes('CUSTOMER') ? '/account/orders' : '/admin', { replace: true })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Sign in failed. Check your email and password.'))
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
        <p>Sign in to manage your cart and orders.</p>
        <form onSubmit={submit} className="form-stack">
          <label>Email address<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          {error && <div className="notice notice-error" role="alert">{error}</div>}
          <button className="primary-button full-button" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <div className="customer-auth-footer">New to Northstar? <Link to="/register">Create an account</Link></div>
        <div className="login-foot"><Store size={15} /> Staff members can also sign in here.</div>
      </section>
    </main>
  )
}

export function CustomerRegisterPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [created, setCreated] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (password !== confirmPassword) {
      setError('The passwords do not match.')
      return
    }
    setSubmitting(true)
    try {
      await api.post('/auth/register', { fullName: fullName.trim(), email: email.trim(), password })
      setCreated(true)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Your account could not be created. Please review the details and try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-shell">
      <Link to="/" className="login-back"><ArrowLeft size={16} /> Back to storefront</Link>
      <section className="login-card">
        <div className="login-mark"><UserRoundPlus size={22} /></div>
        <div className="eyebrow">CUSTOMER ACCOUNT</div>
        <h1>Create your account</h1>
        {created ? (
          <div className="registration-success" role="status">
            <strong>Your account is ready.</strong>
            <p>Sign in to save delivery addresses, build a cart, and place orders.</p>
            <Link to="/login" className="primary-button full-button">Continue to sign in</Link>
          </div>
        ) : (
          <>
            <p>Your account is for shopping and order history.</p>
            <form onSubmit={submit} className="form-stack">
              <label>Full name<input autoComplete="name" maxLength={160} required value={fullName} onChange={(event) => setFullName(event.target.value)} /></label>
              <label>Email address<input type="email" autoComplete="email" maxLength={254} required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
              <label>Password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} /><small>Use at least 12 characters.</small></label>
              <label>Confirm password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>
              {error && <div className="notice notice-error" role="alert">{error}</div>}
              <button className="primary-button full-button" type="submit" disabled={submitting}>
                {submitting ? 'Creating account…' : 'Create account'}
              </button>
            </form>
            <div className="customer-auth-footer">Already registered? <Link to="/login">Sign in</Link></div>
          </>
        )}
        <div className="login-foot"><Store size={15} /> Customer accounts receive customer access only.</div>
      </section>
    </main>
  )
}
