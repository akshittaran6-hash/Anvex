import './buttons.css'

export function PrimaryButton({ children, onClick, disabled, className = '', type = 'button', arrow = true }) {
  return (
    <button type={type} className={`btn btn-primary ${className}`} onClick={onClick} disabled={disabled}>
      {children}
      {arrow && <span className="btn-arrow">&rarr;</span>}
    </button>
  )
}

export function SecondaryButton({ children, onClick, disabled, className = '', type = 'button' }) {
  return (
    <button type={type} className={`btn btn-secondary ${className}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}

export function DangerButton({ children, onClick, disabled, className = '', type = 'button' }) {
  return (
    <button type={type} className={`btn btn-danger ${className}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}
