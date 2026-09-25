import './form-controls.css'

export function TextField({ label, value, onChange, type = 'text', placeholder, disabled }) {
  return (
    <label className={`form-field ${disabled ? 'form-disabled' : ''}`}>
      <span className="form-label">{label}</span>
      <input
        className="form-input"
        type={type}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => onChange && onChange(e.target.value)}
      />
    </label>
  )
}

export function SelectField({ label, value, options, onChange, disabled }) {
  return (
    <label className={`form-field ${disabled ? 'form-disabled' : ''}`}>
      <span className="form-label">{label}</span>
      <select
        className="form-input form-select"
        value={value}
        disabled={disabled}
        onChange={(e) => onChange && onChange(e.target.value)}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </label>
  )
}

export function RadioGroup({ label, value, options, onChange, disabled }) {
  return (
    <div className={`form-field ${disabled ? 'form-disabled' : ''}`}>
      <span className="form-label">{label}</span>
      <div className="radio-group" role="radiogroup">
        {options.map((opt) => (
          <label key={opt.value} className={`radio-option ${value === opt.value ? 'radio-active' : ''}`}>
            <input
              type="radio"
              name={label}
              value={opt.value}
              checked={value === opt.value}
              disabled={disabled}
              onChange={() => onChange && onChange(opt.value)}
            />
            <i className="radio-dot" />
            {opt.label}
          </label>
        ))}
      </div>
    </div>
  )
}
