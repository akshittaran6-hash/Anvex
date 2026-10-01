import { useEffect, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import MetricDisplay from '../../components/MetricDisplay.jsx'
import ProgressIndicator from '../../components/ProgressIndicator.jsx'
import DiagnosticRow from '../../components/DiagnosticRow.jsx'
import EventRow from '../../components/EventRow.jsx'
import { StatusPill } from '../../components/Badges.jsx'
import { DangerButton, SecondaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './simulation-running.css'

function formatElapsed(ms) {
  const total = Math.floor(ms / 1000)
  const h = String(Math.floor(total / 3600)).padStart(2, '0')
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

const OUTCOME_LABELS = {
  LOGIN_SUCCESS: 'LOGIN_SUCCESS',
  LOGIN_FAILURE: 'LOGIN_FAILED',
  LOGIN_BLOCKED: 'LOGIN_BLOCKED',
  ERROR: 'ERROR'
}

export default function SimulationRunning({ onNewSimulation, onInspect }) {
  const { sources, simulation, stopSimulation, safety } = useLab()
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    if (!simulation || simulation.status !== 'running') return
    const timer = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(timer)
  }, [simulation])

  if (!simulation) {
    return (
      <PanelShell title="4" titleAccent="SIMULATION RUNNING">
        <p className="sim-empty">No simulation has been started.</p>
      </PanelShell>
    )
  }

  const source = sources.find((s) => s.id === simulation.sourceId)
  const elapsed = simulation.status === 'running' ? now - simulation.startedAt : simulation.completedAt - simulation.startedAt

  return (
    <div className="sim-layout">
      <PanelShell
        title="4"
        titleAccent="SIMULATION RUNNING"
        actions={<span className={`sim-run-badge ${simulation.status === 'running' ? 'badge-running' : 'badge-done'}`}>{simulation.status === 'running' ? '● RUNNING' : '○ ' + simulation.status.toUpperCase()}</span>}
        className="sim-panel"
      >
        <div className="sim-title-row">
          <div>
            <h1 className="sim-title">{simulation.name}</h1>
            <p className="sim-desc">
              {simulation.status === 'running'
                ? 'Controlled simulation in progress.'
                : simulation.status === 'stopped'
                  ? `Simulation stopped by operator at ${formatElapsed(simulation.completedAt - simulation.startedAt)} elapsed.`
                  : 'Simulation completed.'}
            </p>
          </div>
          <div className="sim-elapsed">
            <span className="sim-elapsed-label">Elapsed Time</span>
            <span className="sim-elapsed-value">{formatElapsed(elapsed)}</span>
          </div>
        </div>
        <div className="sim-progress">
          <div className="sim-sent">
            <span className="sim-sent-label">Responses Received</span>
            <span className="sim-sent-value">
              {simulation.sent} <span className="sim-sent-of">of {simulation.attempts}</span>
            </span>
          </div>
          <ProgressIndicator value={simulation.sent} max={simulation.attempts} />
        </div>
        <div className="sim-metrics">
          <MetricDisplay value={simulation.sent} label="Responses" accent="cyan" size="sm" />
          <MetricDisplay value={simulation.failed} label="Failed" accent="orange" size="sm" />
          <MetricDisplay value={simulation.blocked} label="Blocked" accent="red" size="sm" />
          <MetricDisplay value={Math.max(0, simulation.attempts - simulation.sent)} label="Remaining" size="sm" />
        </div>
        <div className="sim-source-row">
          <div className="sim-source-block">
            <span className="sim-source-label">Current Source</span>
            <span className="sim-source-id">{simulation.sourceId}</span>
            <span className="sim-source-ip">{source ? source.ip : '—'}</span>
          </div>
          <div className="sim-source-meta">
            <DiagnosticRow label="Simulated IP" value={source ? source.ip : '—'} />
            <DiagnosticRow label="Effective Interval" value={`${simulation.interval} seconds`} />
            <DiagnosticRow label="Target User" value={simulation.targetUser} />
          </div>
        </div>
        <div className="sim-actions">
          <DangerButton onClick={stopSimulation} disabled={simulation.status !== 'running'}>
            STOP SIMULATION
          </DangerButton>
          {simulation.status !== 'running' && (
            <>
              <SecondaryButton onClick={onNewSimulation}>NEW SIMULATION</SecondaryButton>
              {onInspect && simulation.runId && (
                <SecondaryButton onClick={() => onInspect(simulation.runId)}>INSPECT RUN →</SecondaryButton>
              )}
            </>
          )}
          <div className="sim-mode-note">
            <span>Simulation Mode: {simulation.mode === 'burst' ? 'Burst' : simulation.mode === 'custom' ? 'Custom' : 'Realistic'}</span>
            <StatusPill on={safety.safeMode} label={safety.safeMode ? 'LAB SAFETY ON' : 'LAB SAFETY OFF'} />
          </div>
          {simulation.endError && (
            <p className="sim-end-error">{simulation.endError}</p>
          )}
        </div>
      </PanelShell>

      <PanelShell
        title="EVENTS"
        titleAccent="SIMULATION LOG"
        className="sim-log-panel"
      >
        <div className="sim-log">
          {simulation.log.length === 0 && (
            <p className="sim-log-empty">Waiting for the first event…</p>
          )}
          {simulation.log.map((entry, i) => (
            <EventRow
              key={i}
              time={entry.time}
              type={OUTCOME_LABELS[entry.type] || entry.type}
              statusBadge={entry.error ? 'FAILED' : 'Sent'}
              compact
            />
          ))}
        </div>
      </PanelShell>
    </div>
  )
}
