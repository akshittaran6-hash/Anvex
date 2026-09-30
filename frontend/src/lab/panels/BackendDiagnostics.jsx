import { useEffect, useRef, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import DiagnosticRow from '../../components/DiagnosticRow.jsx'
import MetricDisplay from '../../components/MetricDisplay.jsx'
import MiniChart from '../../components/MiniChart.jsx'
import { ConnectionBadge } from '../../components/Badges.jsx'
import { SecondaryButton } from '../../components/Buttons.jsx'
import { api } from '../../api/client.js'
import { useLab } from '../lab-context.jsx'
import './backend-diagnostics.css'

export default function BackendDiagnostics() {
  const { simulation } = useLab()
  const [status, setStatus] = useState(null)
  const [metrics, setMetrics] = useState(null)
  const [latency, setLatency] = useState(null)
  const [lastCheck, setLastCheck] = useState(null)
  const [testing, setTesting] = useState(false)
  const latencyHistoryRef = useRef([])

  async function poll() {
    const started = performance.now()
    try {
      const result = await api.get('/api/status')
      const measured = Math.round(performance.now() - started)
      setStatus(result)
      setLatency(measured)
      setLastCheck(new Date().toLocaleTimeString('en-GB', { hour12: false }))
      latencyHistoryRef.current = [...latencyHistoryRef.current.slice(-19), measured]
      api.get('/api/metrics').then(setMetrics).catch(() => setMetrics(null))
    } catch {
      setStatus(null)
      setMetrics(null)
    }
  }

  useEffect(() => {
    poll()
    const timer = setInterval(poll, 4000)
    return () => clearInterval(timer)
  }, [])

  const connected = status !== null

  return (
    <PanelShell
      title="12"
      titleAccent="BACKEND CONNECTION / DIAGNOSTICS"
      className="diagnostics-panel"
      actions={
        <>
          <ConnectionBadge connected={connected} />
          <SecondaryButton
            onClick={async () => {
              setTesting(true)
              await poll()
              setTesting(false)
            }}
            disabled={testing}
          >
            {testing ? 'TESTING' : 'TEST CONNECTION'}
          </SecondaryButton>
        </>
      }
    >
      <div className="diagnostics-grid">
        <div className="diagnostics-connection">
          <div className="diagnostics-block">
            <span className="diagnostics-block-title">ANVEX Core Connection</span>
            <DiagnosticRow label="Status" value={connected ? 'Connected' : 'Unreachable'} accent={connected ? 'green' : 'red'} />
            <DiagnosticRow label="Endpoint" value={connected ? `${location.host}/api` : '—'} />
            <DiagnosticRow label="API Port" value={connected && status.apiPort ? String(status.apiPort) : '—'} />
            <DiagnosticRow label="Latency" value={latency !== null ? `~${latency} ms` : '—'} accent="cyan" />
            <DiagnosticRow label="Last Check" value={lastCheck || '—'} />
          </div>
          <div className="diagnostics-block">
            <span className="diagnostics-block-title">Live Backend Data</span>
            <DiagnosticRow
              label="Event Count"
              value={connected && status.eventCount >= 0 ? String(status.eventCount) : '—'}
            />
            <DiagnosticRow
              label="Alert Count"
              value={connected && status.alertCount >= 0 ? String(status.alertCount) : '—'}
              accent="orange"
            />
            <DiagnosticRow
              label="Active Run"
              value={connected && status.runId ? `RUN-${String(status.runId).padStart(3, '0')}` : 'None'}
            />
          </div>
        </div>
        <div className="diagnostics-metrics">
          <div className="diagnostics-block">
            <span className="diagnostics-block-title">Backend Metrics</span>
            <div className="diagnostics-metric-tiles">
              <MetricDisplay
                value={metrics ? metrics.attackerAttempts : '—'}
                label="Attempts"
                accent="cyan"
                size="sm"
              />
              <MetricDisplay
                value={metrics ? metrics.attackerFailures : '—'}
                label="Failures"
                accent="orange"
                size="sm"
              />
              <MetricDisplay
                value={metrics ? metrics.attackerBlocked : '—'}
                label="Blocked"
                accent="red"
                size="sm"
              />
            </div>
          </div>
          <div className="diagnostics-latency">
            <div className="diagnostics-latency-head">
              <span className="diagnostics-block-title">Latency Trend</span>
              <span className="diagnostics-latency-value">{latency !== null ? `${latency} ms` : '—'}</span>
            </div>
            <MiniChart values={latencyHistoryRef.current} />
          </div>
          <div className="diagnostics-sim">
            <DiagnosticRow
              label="Lab Client Sent"
              value={simulation ? String(simulation.sent) : '0'}
            />
            <DiagnosticRow
              label="Lab Client Failed"
              value={simulation ? String(simulation.failed) : '0'}
              accent="orange"
            />
          </div>
        </div>
        <div className="diagnostics-visual" aria-hidden="true">
          <div className="diag-server">
            <i className="diag-server-light" />
            <i className="diag-server-slot" />
            <i className="diag-server-slot" />
            <i className="diag-server-slot" />
          </div>
          <span className="diagnostics-visual-caption">
            {connected ? 'CONNECTION HEALTHY' : 'CONNECTION LOST'}
          </span>
        </div>
      </div>
    </PanelShell>
  )
}
