import './progress.css'

export default function ProgressIndicator({ value, max = 100, label }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  return (
    <div className="progress">
      {(label || pct !== null) && (
        <div className="progress-head">
          <span className="progress-label">{label}</span>
          <span className="progress-pct">{pct}%</span>
        </div>
      )}
      <div className="progress-track">
        <div className="progress-fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
