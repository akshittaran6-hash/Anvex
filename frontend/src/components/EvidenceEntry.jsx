import './evidence-entry.css'

export default function EvidenceEntry({ time, title, reasons, accent = 'orange' }) {
  return (
    <div className={`evidence-entry evidence-${accent}`}>
      <div className="evidence-head">
        <i className="evidence-marker" />
        <span className="evidence-time">{time}</span>
        {title && <span className="evidence-title">{title}</span>}
      </div>
      {reasons && reasons.length > 0 && (
        <ul className="evidence-reasons">
          {reasons.map((reason, i) => (
            <li key={i}>{reason}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
