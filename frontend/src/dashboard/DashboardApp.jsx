import { useEffect, useRef, useState } from 'react'
import { getToken, setToken } from '../api/client.js'
import useDashboard from './useDashboard.js'
import './dashboard.css'
const value = x => x == null ? '—' : String(x)
function Panel({ title, children, id }) { return <section className="soc-panel" id={id}><h2>{title}</h2>{children}</section> }
function Facts({ items }) { return <dl className="soc-facts">{items.map(([label, data]) => <div key={label}><dt>{label}</dt><dd>{value(data)}</dd></div>)}</dl> }
function ThreatIndicator({ source }) {
  const prior = useRef(null)
  const [recovering, setRecovering] = useState(false)
  useEffect(() => {
    setRecovering(Boolean(source && prior.current?.source === source.source && source.score < prior.current.score))
    prior.current = source
  }, [source])
  return <div className={`soc-indicator ${source?.level || 'WAITING'}`}><div className="soc-ring"><strong>{value(source?.score)}</strong><span>{source?.level || 'WAITING'}</span></div><p>{recovering ? 'RECOVERY · backend score falling' : source ? 'LIVE BACKEND ASSESSMENT' : 'Ready · waiting for a tracked source'}</p></div>
}
function EventStream({ events }) {
  return <div className="soc-event-scroll">{!events.length ? <p className="soc-empty">Ready for backend events. Start a simulation from System 1.</p> : events.map(e => <article className={`soc-event type-${e.type}`} key={e.id ?? `live-${e.liveKey}`}><div className="soc-event-heading"><time>{e.timestamp ? new Date(e.timestamp).toLocaleTimeString('en-GB', { hour12: false }) : '—'}</time><strong>{value(e.type)}</strong><span>{value(e.level)}</span></div><div className="soc-event-meta"><span>Source: {value(e.source)}</span><span>Outcome: {value(e.outcome)}</span>{e.score != null && <span>Score: {e.score}</span>}</div><p>{value(e.message)}</p></article>)}</div>
}
export default function DashboardApp() {
  const [revision, setRevision] = useState(0)
  const model = useDashboard(revision)
  const [token, setTokenInput] = useState('')
  const [selected, setSelected] = useState('')
  const sorted = model.sources.slice().sort((a,b) => b.score - a.score || a.source.localeCompare(b.source))
  const source = model.sources.find(x => x.source === selected) || sorted[0]
  const latest = model.runs.slice().sort((a,b) => b.runId - a.runId)[0]
  const run = model.runs.find(x => x.runId === model.status?.runId) || latest
  const m = model.metrics
  return <div className="soc-shell"><header className="soc-top"><div><b>A N V E X</b><span>SYSTEM 2 · SOC / DEFENDER</span></div><nav><a href="#live">LIVE</a><a href="#runs">RUN STATUS</a><a href="/lab">SYSTEM 1 ↗</a></nav><span className={model.connected ? 'soc-online' : 'soc-offline'}>{model.connected ? 'BACKEND CONNECTED' : 'BACKEND DISCONNECTED'}</span></header><main id="live"><div className="soc-intro"><h1>Live integration monitor</h1><p>Real Java detection and protection · temporary threat indicator</p></div>
  <Panel title="01 / CONNECTION"><Facts items={[
    ['Backend', model.connected ? 'Connected' : 'Disconnected'], ['Host', window.location.hostname], ['API port', model.status?.apiPort], ['SSE', model.sse], ['Active run', model.status?.runId || 'None'], ['Events (stored)', model.status?.eventCount < 0 ? 'Unavailable' : model.status?.eventCount], ['Alerts (stored)', model.status?.alertCount < 0 ? 'Unavailable' : model.status?.alertCount]
  ]}/>{model.error && <p role="alert" className="soc-error">{model.error} {model.status && 'Last received values remain visible.'}</p>}<form className="soc-auth" onSubmit={e => { e.preventDefault(); setToken(token.trim()); setTokenInput(''); setRevision(x => x+1) }}><label htmlFor="soc-token">{getToken() ? 'Replace API token' : 'API token'}</label><input id="soc-token" type="password" autoComplete="off" value={token} onChange={e => setTokenInput(e.target.value)} placeholder="Shared with System 1 on this origin"/><button type="submit">Connect</button><button type="button" onClick={model.refresh}>Refresh</button></form></Panel>
  <div className="soc-columns"><Panel title="02 / CURRENT SOURCE"><ThreatIndicator source={source}/><Facts items={[
    ['Source ID', source?.source], ['Threat score', source?.score], ['Threat level', source?.level], ['Protection phase', source?.phase], ['Blocked until', source?.blockedUntil], ['Reason', source?.reason]
  ]}/><h3>Backend evidence</h3>{source?.evidence ? <Facts items={Object.entries(source.evidence)}/> : <p className="soc-empty">No evidence provided.</p>}</Panel><div className="soc-side"><Panel title="03 / TRACKED SOURCES"><button onClick={() => setSelected('')}>Focus highest score {selected ? '' : '· AUTO'}</button>{!sorted.length ? <p className="soc-empty">No tracked sources.</p> : <div className="soc-sources">{sorted.map(x => <button key={x.source} className={source?.source === x.source ? 'selected' : ''} onClick={() => setSelected(x.source)}><span>{x.source}</span><b>{x.score}</b><span className={x.level}>{x.level}</span></button>)}</div>}</Panel><Panel title="04 / RUN STATUS" id="runs"><Facts items={[
    ['Active run', model.status?.runId || 'None'], ['Latest run', latest?.runId], ['Run label', run?.label], ['Status', run?.status], ['Started', run?.startTime], ['Ended', run?.endTime], ['Attacker attempts', m?.attackerAttempts], ['Blocked attempts', m?.attackerBlocked], ['Legitimate successes', m?.legitimateSuccesses], ['Duration (ms)', m?.durationMs]
  ]}/></Panel></div></div><Panel title="05 / LIVE EVENT STREAM · NEWEST FIRST"><EventStream events={model.events}/></Panel><footer>Scores, levels, evidence and protection are supplied by Java. Missing metadata is shown as —. Stored history reconciles every 5 seconds.</footer></main></div>
}
