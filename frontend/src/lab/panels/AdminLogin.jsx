import { useState } from 'react'
import { PrimaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './admin-login.css'

export default function AdminLogin() {
  const { signIn } = useLab()
  const [operator, setOperator] = useState('operator')
  const [token, setTokenValue] = useState('')
  const [remember, setRemember] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!token.trim()) {
      setError('Enter the API token to connect to ANVEX Core')
      return
    }
    setBusy(true)
    setError('')
    try {
      await signIn(operator.trim() || 'operator', token.trim(), remember)
    } catch (err) {
      setError(err.status === 401 ? 'Invalid API token' : 'Cannot reach ANVEX Core: ' + err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-login">
      <div className="login-card">
        <div className="login-left">
          <div className="login-brand">
            <h1 className="login-logo">A N V E X</h1>
            <p className="login-sub">LAB CLIENT</p>
            <p className="login-sub-dim">CONTROLLED ATTACK SIMULATION INTERFACE</p>
          </div>
          <form className="login-form" onSubmit={handleSubmit}>
            <label className="login-field">
              <span className="login-field-icon">⊙</span>
              <input
                type="text"
                value={operator}
                onChange={(e) => setOperator(e.target.value)}
                placeholder="operator"
                aria-label="Operator"
              />
            </label>
            <label className="login-field">
              <span className="login-field-icon">***</span>
              <input
                type="password"
                value={token}
                onChange={(e) => setTokenValue(e.target.value)}
                placeholder="API token"
                aria-label="API token"
              />
            </label>
            <label className="login-remember">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              <i className="login-checkbox" />
              Remember me
            </label>
            {error && <p className="login-error">{error}</p>}
            <PrimaryButton className="login-submit" disabled={busy}>
              {busy ? 'CONNECTING' : 'SIGN IN'}
            </PrimaryButton>
          </form>
        </div>
        <div className="login-right">
          <div className="login-globe" aria-hidden="true">
            <i className="globe-ring globe-ring-1" />
            <i className="globe-ring globe-ring-2" />
            <i className="globe-ring globe-ring-3" />
            <i className="globe-core" />
            <i className="globe-arc" />
          </div>
          <div className="login-phases">
            <span>CONFIGURE</span>
            <span>SIMULATE</span>
            <span>VALIDATE</span>
            <span>IMPROVE</span>
          </div>
        </div>
      </div>
    </div>
  )
}
