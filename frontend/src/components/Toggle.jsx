import './toggle.css'

export default function Toggle({ on, onChange, disabled, label }) {
  return (
    <label className={`toggle-row ${disabled ? 'toggle-disabled' : ''}`}>
      {label && <span className="toggle-label">{label}</span>}
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
    </label>
  )
}
