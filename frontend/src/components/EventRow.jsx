import './event-row.css'

const TYPE_ACCENTS = {
  LOGIN_SUCCESS: 'green',
  LOGIN_FAILURE: 'red',
  LOGIN_BLOCKED: 'orange',
  ANOMALY_DETECTED: 'amber',
  THREAT_ESCALATED: 'orange',
  THREAT_DEESCALATED: 'cyan',
  PROTECTION_ENABLED: 'orange',
  SOURCE_BLOCKED: 'red',
  SOURCE_RELEASED: 'green',
  SECURITY_INCIDENT: 'red',
  ATTACK_STARTED: 'orange',
  ATTACK_COMPLETED: 'cyan',
  RUN_STARTED: 'cyan',
  RUN_COMPLETED: 'green',
  SERVER_STARTED: 'cyan',
  SERVER_STOPPED: 'muted'
}

export default function EventRow({ time, type, message, level, statusBadge, compact = false }) {
  const accent = TYPE_ACCENTS[type] || 'muted'
  return (
    <div className={`event-row ${compact ? 'event-compact' : ''}`}>
      <span className="event-time">{time}</span>
      <span className={`event-type accent-${accent}`}>{type}</span>
      {!compact && <span className="event-message">{message}</span>}
      {level && <span className={`event-level level-${level.toLowerCase()}`}>{level}</span>}
      {statusBadge && <span className="event-status">{statusBadge}</span>}
    </div>
  )
}

export { TYPE_ACCENTS }
