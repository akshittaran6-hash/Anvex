import { useEffect, useMemo, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import { SecondaryButton } from '../../components/Buttons.jsx'
import { RunRow } from '../../components/RunRow.jsx'
import { api } from '../../api/client.js'
import './run-history.css'

const LEVEL_ORDER = { NORMAL: 0, SUSPICIOUS: 1, HIGH: 2, CRITICAL: 3 }

function deriveFromEvents(events) {
  const loginEvents = events.filter((e) => e.type && e.type.startsWith('LOGIN_'))
  const decisionEvents = events.filter((e) => e.type === 'PROTECTION_ENABLED' || e.type === 'SOURCE_BLOCKED')
  let peak = null
  for (const e of events) {
    if (e.level && (peak === null || LEVEL_ORDER[e.level] > LEVEL_ORDER[peak])) {
      peak = e.level
    }
  }
  const sources = [...new Set(events.map((e) => e.source).filter(Boolean))]
  return {
    events: loginEvents.length,
    failed: loginEvents.filter((e) => e.outcome === 'FAILURE').length,
    blocked: loginEvents.filter((e) => e.outcome === 'BLOCKED').length,
    peak,
    protectionActions: decisionEvents.length,
    sources,
    type: sources.length > 1 ? 'Multi-Source' : loginEvents.length > 0 ? 'Login Simulation' : 'Session'
  }
}

function formatDuration(start, end) {
  if (!start || !end) return null
  const ms = new Date(end) - new Date(start)
  if (ms < 0) return null
  const total = Math.floor(ms / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return m > 0 ? `${m}m ${String(s).padStart(2, '0')}s` : `${s}s`
}

function formatStarted(start) {
  if (!start) return '—'
  const d = new Date(start)
  return d.toLocaleString('en-GB', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false
  })
}

export default function RunHistory({ onSelect }) {
  const [runs, setRuns] = useState(null)
  const [details, setDetails] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      const list = await api.get('/api/runs')
      setRuns(list)

      const relevant = list.filter((r) => r.status !== 'RUNNING').slice(0, 15)
      const entries = await Promise.all(relevant.map(async (run) => {
        try {
          const events = await api.get(`/api/runs/${run.runId}/events`)
          return [run.runId, deriveFromEvents(events)]
        } catch {
          return [run.runId, null]
        }
      }))
      setDetails(Object.fromEntries(entries))
    } catch (err) {
      setRuns(null)
      setError(err.message || 'Cannot reach the ANVEX backend')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const rows = useMemo(() => {
    if (!runs) return []
    return runs
      .filter((r) => r.status !== 'RUNNING')
      .map((run) => {
        const derived = details[run.runId]
        return {
          runId: `RUN-${String(run.runId).padStart(3, '0')}`,
          label: run.label || '—',
          type: derived ? derived.type : null,
          events: derived ? derived.events : null,
          failed: derived ? derived.failed : null,
          blocked: derived ? derived.blocked : null,
          peak: derived ? derived.peak : null,
          elapsed: formatDuration(run.startTime, run.endTime),
          started: formatStarted(run.startTime)
        }
      })
      .filter((row) => {
        const q = search.trim().toLowerCase()
        if (!q) return true
        return row.runId.toLowerCase().includes(q) || row.label.toLowerCase().includes(q)
      })
  }, [runs, details, search])

  return (
    <PanelShell
      title="10"
      titleAccent="RUN HISTORY"
      className="history-panel"
      actions={
        <>
          <div className="history-search">
            <input
              type="text"
              value={search}
              placeholder="Search runs..."
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <SecondaryButton onClick={load} disabled={loading}>{loading ? 'LOADING' : 'REFRESH'}</SecondaryButton>
        </>
      }
    >
      {error && <p className="history-error">{error}</p>}
      {loading && !runs && <p className="history-empty">Loading run history…</p>}
      {!loading && !error && rows.length === 0 && (
        <p className="history-empty">No completed runs recorded yet. Complete a simulation to build history.</p>
      )}
      {rows.length > 0 && (
        <div className="history-table">
          <div className="history-table-head">
            <span>RUN ID</span>
            <span>NAME</span>
            <span>TYPE</span>
            <span>EVENTS</span>
            <span>FAILED</span>
            <span>BLOCKED</span>
            <span>PEAK LEVEL</span>
            <span>ELAPSED</span>
            <span>STARTED</span>
          </div>
          {rows.map((row) => (
            <RunRow key={row.runId} run={row} variant="history" onClick={() => onSelect(row.runId)} />
          ))}
        </div>
      )}
      <p className="history-note">
        Counts and peak levels are derived from each run's real persisted events in the ANVEX backend.
      </p>
    </PanelShell>
  )
}
