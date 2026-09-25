import './metric-display.css'

export default function MetricDisplay({ value, label, accent = 'default', size = 'md' }) {
  return (
    <div className={`metric-display metric-${accent} metric-${size}`}>
      <span className="metric-value">{value}</span>
      <span className="metric-label">{label}</span>
    </div>
  )
}
