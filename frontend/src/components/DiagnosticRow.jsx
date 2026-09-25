import './diagnostic-row.css'

export default function DiagnosticRow({ label, value, accent = 'default' }) {
  return (
    <div className="diagnostic-row">
      <span className="diagnostic-label">{label}</span>
      <span className={`diagnostic-value accent-${accent}`}>{value}</span>
    </div>
  )
}
