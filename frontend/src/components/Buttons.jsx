import './buttons.css'

export function PrimaryButton({ children, onClick, disabled, className = '' }) {
  return (
    <button className={`btn btn-primary ${className}`} onClick={onClick} disabled={disabled}>
      {children}
      <span className="btn-arrow">&rarr;</span>
    </button>
  )
}

export function SecondaryButton({ children, onClick, disabled, className = '' }) {
  return (
    <button className={`btn btn-secondary ${className}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}

export function DangerButton({ children, onClick, disabled, className = '' }) {
  return (
    <button className={`btn btn-danger ${className}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}
