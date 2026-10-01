import { useEffect } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import MetricDisplay from '../../components/MetricDisplay.jsx'
import DiagnosticRow from '../../components/DiagnosticRow.jsx'
import { StatusPill, ConnectionBadge } from '../../components/Badges.jsx'
import { PrimaryButton, SecondaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './lab-home.css'

export default function LabHome({ onNavigate }) {
  const { templates, sources, simulation, safety, backendStatus, backendLatency } = useLab()

  const connected = backendStatus !== null
  const latency = backendLatency
  const running = simulation && simulation.status === 'running'

  return (
    <div className="lab-home">
      <div className="lab-home-main">
        <PanelShell
          title="2"
          titleAccent="LAB READY / HOME"
          className="home-panel"
        >
          <div className="home-top">
            <div className="home-ready">
              <h1 className="home-title">READY</h1>
              <p className="home-desc">
                Lab Client connected to ANVEX Core. Configure and send controlled simulations.
              </p>
            </div>
            <div className="home-side">
              <div className="home-connection">
                <ConnectionBadge connected={connected} />
                <DiagnosticRow label="Endpoint" value={connected ? `${location.host}/api` : '—'} />
                <DiagnosticRow label="Latency" value={latency !== null ? `~${latency} ms` : '—'} accent="cyan" />
              </div>
              <div className="home-safe">
                <StatusPill on={safety.safeMode} label={safety.safeMode ? 'LAB SAFETY ON' : 'LAB SAFETY OFF'} />
              </div>
            </div>
          </div>
          <div className="home-metrics">
            <MetricDisplay value={sources.length} label="Source Identities" size="lg" />
            <MetricDisplay value={templates.length} label="Scenario Templates" size="lg" />
            <MetricDisplay value={running ? 1 : 0} label="Simulations Running" size="lg" accent={running ? 'green' : 'default'} />
            <MetricDisplay
              value={backendStatus && backendStatus.eventCount >= 0 ? backendStatus.eventCount : '—'}
              label="Stored Events (History)"
              size="lg"
            />
          </div>
          <div className="home-actions">
            <PrimaryButton onClick={() => onNavigate('CREATE')}>CREATE SIMULATION</PrimaryButton>
            <SecondaryButton onClick={() => onNavigate('TEMPLATES')}>VIEW TEMPLATES</SecondaryButton>
          </div>
        </PanelShell>

        <div className="home-visual" aria-hidden="true">
          <div className="home-globe">
            <i className="hg-ring hg-1" />
            <i className="hg-ring hg-2" />
            <i className="hg-ring hg-3" />
            <i className="hg-core" />
            <i className="hg-arc" />
          </div>
          <p className="home-visual-caption">CONTROLLED EVENTS · SAFE SECURITY · STRONGER DEFENSE</p>
        </div>
      </div>
    </div>
  )
}
