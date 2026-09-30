import './toggle.css'

export default function Toggle({ on, onChange, disabled, label, sub }) {
  return (
    <div className={`toggle-row ${disabled ? 'toggle-disabled' : ''}`}>
      {(label || sub) && (
        <span className="toggle-text">
          {label && <span className="toggle-label">{label}</span>}
          {sub && <span className="toggle-sub">{sub}</span>}
        </span>
      )}
      <button
        type="button"
        role="switch"
        aria-checked={on}
        className={`toggle ${on ? 'toggle-on' : 'toggle-off'}`}
        onClick={() => !disabled && onChange && onChange(!on)}
        disabled={disabled}
      >
        <span className="toggle-track">
          <span className="toggle-thumb" />
        </span>
        <span className="toggle-state">{on ? 'ON' : 'OFF'}</span>
      </button>
    </div>
  )
}
