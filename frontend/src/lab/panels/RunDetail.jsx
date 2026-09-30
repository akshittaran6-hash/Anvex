import { useEffect, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import DiagnosticRow from '../../components/DiagnosticRow.jsx'
import EventRow from '../../components/EventRow.jsx'
import EvidenceTags from '../../components/EvidenceTags.jsx'
import { SecondaryButton } from '../../components/Buttons.jsx'
import { api } from '../../api/client.js'
import './run-detail.css'

const LEVEL_ORDER = { NORMAL: 0, SUSPICIOUS: 1, HIGH: 2, CRITICAL: 3 }
const OUTCOME_LABELS = { LOGIN_SUCCESS: 'LOGIN_SUCCESS', LOGIN_FAILURE: 'LOGIN_FAILED', LOGIN_BLOCKED: 'LOGIN_BLOCKED' }

function deriveProgression(events) {
  const stages = []
  let current = null
  for (const event of events) {
    if (!event.level) continue
    if (event.level !== current) {
      current = event.level
      stages.push({ level: event.level, time: event.timestamp, type: event.type })
    }
  }
  return stages
}

function formatDuration(start, end) {
  if (!start || !end) return null
  const ms = new Date(end) - new Date(start)
  if (ms < 0) return null
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return h > 0 ? `${h}h ${m}m ${String(s).padStart(2, '0')}s` : m > 0 ? `${m}m ${String(s).padStart(2, '0')}s` : `${s}s`
}

function formatTimestamp(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  })
}

export default function RunDetail({ runId, onBack }) {
  const [run, setRun] = useState(null)
  const [events, setEvents] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setRun(null)
    setEvents(null)
    setExpanded(null)

    async function load() {
      try {
        const runData = await api.get(`/api/runs/${runId}`)
        if (cancelled) return
        setRun(runData)
        const eventList = await api.get(`/api/runs/${runId}/events`)
        if (cancelled) return
        setEvents(eventList)
      } catch (err) {
        if (!cancelled) {
          setError(err.status === 404 ? 'Run not found.' : err.message || 'Cannot reach the ANVEX backend')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [runId])

  if (loading) {
    return (
      <PanelShell title="11" titleAccent="RUN DETAIL / SUMMARY">
        <p className="detail-empty">Loading run…</p>
      </PanelShell>
    )
  }

  if (error) {
    return (
      <PanelShell title="11" titleAccent="RUN DETAIL / SUMMARY">
        <p className="detail-error">{error}</p>
        <div className="detail-back">
          <SecondaryButton onClick={onBack}>← BACK TO HISTORY</SecondaryButton>
        </div>
      </PanelShell>
    )
  }

  const loginEvents = (events || []).filter((e) => e.type && e.type.startsWith('LOGIN_'))
  const decisionEvents = (events || []).filter(
    (e) => e.type === 'PROTECTION_ENABLED' || e.type === 'SOURCE_BLOCKED'
  )
  const progression = deriveProgression(events || [])
  let peak = null
  for (const event of events || []) {
    if (event.level && (peak === null || LEVEL_ORDER[event.level] > LEVEL_ORDER[peak])) {
      peak = event.level
    }
  }
  const finalLevel = progression.length > 0 ? progression[progression.length - 1].level : null
  const sources = [...new Set((events || []).map((e) => e.source).filter(Boolean))]
  const duration = formatDuration(run.startTime, run.endTime)

  return (
    <PanelShell
      title="11"
      titleAccent="RUN DETAIL / SUMMARY"
      className="detail-panel"
      actions={
        <SecondaryButton onClick={onBack}>← BACK TO HISTORY</SecondaryButton>
      }
    >
      <div className="detail-head">
        <span className="detail-run-id">RUN-{String(run.runId).padStart(3, '0')}</span>
        <span className={`detail-run-status ${run.status === 'COMPLETED' ? 'accent-green' : 'accent-cyan'}`}>
          {run.status}
        </span>
        <span className="detail-run-label">{run.label || '—'}</span>
      </div>

      <div className="detail-summary">
        <div className="detail-summary-block">
          <span className="detail-block-title">Run</span>
          <DiagnosticRow label="Started" value={formatTimestamp(run.startTime)} />
          <DiagnosticRow label="Ended" value={run.endTime ? formatTimestamp(run.endTime) : '—'} />
          <DiagnosticRow label="Duration" value={duration || '—'} accent="cyan" />
        </div>
        <div className="detail-summary-block">
          <span className="detail-block-title">Activity</span>
          <DiagnosticRow label="Total Events" value={String(loginEvents.length)} />
          <DiagnosticRow label="Failures" value={String(loginEvents.filter((e) => e.outcome === 'FAILURE').length)} accent="orange" />
          <DiagnosticRow label="Blocked" value={String(loginEvents.filter((e) => e.outcome === 'BLOCKED').length)} accent="red" />
          <DiagnosticRow label="Protection Actions" value={String(decisionEvents.length)} accent="orange" />
        </div>
        <div className="detail-summary-block">
          <span className="detail-block-title">Threat</span>
          <DiagnosticRow label="Peak Level" value={peak || 'NORMAL'} accent={peak === 'CRITICAL' ? 'red' : peak === 'HIGH' ? 'orange' : 'cyan'} />
          <DiagnosticRow label="Final Level" value={finalLevel || '—'} accent={finalLevel === 'CRITICAL' ? 'red' : 'cyan'} />
          <DiagnosticRow label="Source" value={sources.length > 0 ? sources.join(', ') : '—'} />
        </div>
      </div>

      {progression.length > 0 && (
        <div className="detail-progression">
          <span className="detail-block-title">Threat Progression (actual, from run events)</span>
          <div className="progression-steps">
            {progression.map((stage, i) => (
              <span key={i} className="progression-step-wrap">
                <span className={`progression-step level-${stage.level.toLowerCase()}`}>
                  {stage.level}
                  <span className="progression-time">{formatTimestamp(stage.time)}</span>
                </span>
                {i < progression.length - 1 && <span className="progression-arrow">→</span>}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="detail-timeline">
        <span className="detail-block-title">Event Timeline ({(events || []).length} events)</span>
        <div className="detail-timeline-list">
          {(events || []).length === 0 && (
            <p className="detail-empty">No events recorded for this run.</p>
          )}
          {(events || []).map((event) => (
            <div key={event.id} className="detail-event-wrap">
              <div
                className={`detail-event-row ${expanded === event.id ? 'detail-event-open' : ''}`}
                onClick={() => setExpanded(expanded === event.id ? null : event.id)}
              >
                <EventRow
                  time={formatTimestamp(event.timestamp).split(', ')[1] || formatTimestamp(event.timestamp)}
                  type={OUTCOME_LABELS[event.type] || event.type}
                  source={event.source}
                  level={event.level}
                  message={event.message}
                />
              </div>
              {expanded === event.id && (
                <div className="detail-event-expand">
                  {event.evidence && <EvidenceTags evidence={event.evidence} />}
                  <pre className="detail-raw">{JSON.stringify(event, null, 2)}</pre>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </PanelShell>
  )
}
