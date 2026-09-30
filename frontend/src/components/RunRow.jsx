import './table-rows.css'

const STATUS_ACCENT = {
  Completed: 'green',
  Running: 'cyan',
  Threat: 'red',
  Failed: 'red'
}

export function RunRow({ run, variant = 'runs', selected, onClick }) {
  const accent = STATUS_ACCENT[run.status] || 'muted'
  if (variant === 'history') {
    return (
      <div className={`table-row run-row-history ${selected ? 'row-selected' : ''}`} onClick={onClick}>
        <span className="cell cell-id">{run.runId}</span>
        <span className="cell cell-name">{run.label}</span>
        <span className="cell cell-type">{run.type || '—'}</span>
        <span className="cell cell-metric accent-cyan">{run.events ?? '—'}</span>
        <span className="cell cell-metric accent-orange">{run.failed ?? '—'}</span>
        <span className="cell cell-metric accent-red">{run.blocked ?? '—'}</span>
        <span className={`cell cell-peak level-${(run.peak || 'normal').toLowerCase()}`}>{run.peak || 'NORMAL'}</span>
        <span className="cell cell-time">{run.elapsed ?? '—'}</span>
        <span className="cell cell-time">{run.started}</span>
      </div>
    )
  }
  return (
    <div className={`table-row run-row ${selected ? 'row-selected' : ''}`} onClick={onClick}>
      <span className="cell cell-id">{run.runId}</span>
      <span className="cell cell-name">{run.label}</span>
      <span className="cell cell-type">{run.type || '—'}</span>
      <span className="cell cell-metric">{run.attempts ?? '—'}</span>
      <span className="cell cell-metric">{run.reached ?? '—'}</span>
      <span className="cell cell-metric">{run.prevented ?? '—'}</span>
      <span className={`cell cell-status accent-${accent}`}>{run.status}</span>
      <span className="cell cell-time">{run.started}</span>
    </div>
  )
}

export function SourceRow({ source, active, selected, onClick }) {
  return (
    <div className={`table-row source-row ${selected ? 'row-selected' : ''}`} onClick={onClick}>
      <span className="cell cell-os-icon">{source.os === 'Windows' ? '⊞' : '⚙'}</span>
      <span className="cell cell-name">{source.name}</span>
      <span className="cell cell-type">{source.os}</span>
      <span className="cell cell-ip">{source.ip}</span>
      <span className="cell cell-desc">{source.description}</span>
      <span className={`cell cell-status ${active ? 'accent-green' : 'accent-muted'}`}>
        {active ? '● Active' : '○ Idle'}
      </span>
    </div>
  )
}
