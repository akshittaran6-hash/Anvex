import { useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Tabs from '../../components/Tabs.jsx'
import { TextField, SelectField, RadioGroup } from '../../components/FormControls.jsx'
import { PrimaryButton, SecondaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import EventPayloadPreview from './EventPayloadPreview.jsx'
import './create-simulation.css'

const SUB_TABS = ['BASIC', 'TARGET', 'PAYLOAD', 'SCHEDULE', 'REVIEW']

const MODE_LABELS = { realistic: 'Realistic', burst: 'Burst', custom: 'Custom (source rotation)' }

export default function CreateSimulation({ onStart }) {
  const { sources, templates, startSimulation, config, updateConfig, safety, schedule } = useLab()
  const [subTab, setSubTab] = useState('BASIC')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const template = templates.find((t) => t.id === config.typeId)
  const unimplemented = template && template.implemented === false

  const effectiveInterval = (() => {
    let value = config.mode === 'burst' ? Math.min(Number(config.interval) || 0, 0.5) : (Number(config.interval) || 0)
    if (safety.rateLimiting) {
      value = Math.max(value, 0.3)
    }
    return value
  })()

  function applyTemplate(id) {
    const tpl = templates.find((t) => t.id === id)
    if (tpl) {
      updateConfig({
        typeId: id,
        name: tpl.name,
        attempts: tpl.config.attempts,
        interval: tpl.config.interval,
        mode: tpl.config.mode,
        multiSource: tpl.config.multiSource === true
      })
    }
  }

  function goNext(tab) {
    setError('')
    setSubTab(tab)
  }

  function goBack(tab) {
    setError('')
    setSubTab(tab)
  }

  function handleLaunch() {
    if (!config.sourceId) {
      setError('Select a source identity in the BASIC tab.')
      setSubTab('BASIC')
      return
    }
    if (unimplemented) {
      setError('This scenario is not implemented yet and cannot be launched.')
      return
    }
    if (safety.safeMode !== true) {
      setError('Simulation safety guard is OFF. Enable it in LAB SAFETY before launching.')
      return
    }
    if (safety.targetValidation && String(config.targetUser).trim() !== 'lab_target') {
      setError('Target validation is ON: browser simulations are restricted to lab_target.')
      return
    }
    setBusy(true)
    setError('')
    startSimulation({
      name: config.name,
      type: template ? template.name : config.typeId,
      sourceId: config.sourceId,
      targetUser: config.targetUser,
      attempts: Number(config.attempts) || 1,
      interval: Number(config.interval) || 0,
      mode: config.mode,
      multiSource: config.multiSource === true,
      autoStopRun: true
    }, () => {
      setBusy(false)
      onStart()
    }).catch((err) => {
      setBusy(false)
      setError(err.message || 'Failed to start simulation.')
    })
  }

  return (
    <PanelShell title="3" titleAccent="CREATE SIMULATION" className="create-panel">
      <div className="create-head">
        <Tabs tabs={SUB_TABS} active={subTab} onChange={(tab) => { setError(''); setSubTab(tab) }} />
      </div>

      {subTab === 'BASIC' && (
        <div className="create-form">
          <TextField label="Simulation Name" value={config.name} onChange={(v) => updateConfig({ name: v })} />
          <SelectField
            label="Scenario"
            value={config.typeId}
            onChange={applyTemplate}
            options={templates.map((t) => ({ value: t.id, label: t.implemented === false ? `${t.name} — not implemented yet` : t.name }))}
          />
          <SelectField
            label="Source Identity"
            value={config.sourceId}
            onChange={(v) => updateConfig({ sourceId: v })}
            options={sources.map((s) => ({ value: s.id, label: `${s.id} (${s.ip})` }))}
          />
          <div className="create-row">
            <TextField label="Target User" value={config.targetUser} onChange={(v) => updateConfig({ targetUser: v })} />
            <TextField label="Attempts" value={config.attempts} onChange={(v) => updateConfig({ attempts: v })} type="number" />
          </div>
          <div className="create-row">
            <TextField label="Interval (seconds)" value={config.interval} onChange={(v) => updateConfig({ interval: v })} type="number" />
            <RadioGroup
              label="Simulation Mode"
              value={config.mode}
              onChange={(v) => updateConfig({ mode: v })}
              options={[
                { value: 'realistic', label: 'Realistic' },
                { value: 'burst', label: 'Burst' },
                { value: 'custom', label: 'Custom' }
              ]}
            />
          </div>
          <div className="create-actions">
            <PrimaryButton arrow onClick={() => goNext('TARGET')}>NEXT TARGET</PrimaryButton>
          </div>
        </div>
      )}

      {subTab === 'TARGET' && (
        <div className="create-form">
          <div className="target-info">
            <div className="target-row">
              <span className="target-label">Configured source identity</span>
              <span className="target-value">{config.sourceId || '—'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Configured target</span>
              <span className="target-value">ANVEX lab authentication service (Java backend)</span>
            </div>
            <div className="target-row">
              <span className="target-label">Target user</span>
              <span className="target-value">{config.targetUser || '—'}</span>
            </div>
          </div>
          <TextField
            label="Target User"
            value={config.targetUser}
            onChange={(v) => updateConfig({ targetUser: v })}
          />
          <p className="create-note">
            All attempts are sent to the ANVEX lab authentication service running on the Java backend.
            The source identity IP is simulated lab metadata, not a real network destination.
          </p>
          <div className="create-actions">
            <SecondaryButton onClick={() => goBack('BASIC')}>← BACK</SecondaryButton>
            <PrimaryButton arrow onClick={() => goNext('PAYLOAD')}>NEXT PAYLOAD</PrimaryButton>
          </div>
        </div>
      )}

      {subTab === 'PAYLOAD' && (
        <div className="create-form">
          <EventPayloadPreview />
          <div className="create-actions">
            <SecondaryButton onClick={() => goBack('TARGET')}>← BACK</SecondaryButton>
            <PrimaryButton arrow onClick={() => goNext('SCHEDULE')}>NEXT SCHEDULE</PrimaryButton>
          </div>
        </div>
      )}

      {subTab === 'SCHEDULE' && (
        <div className="create-form">
          <div className="target-info">
            <div className="target-row">
              <span className="target-label">Scheduling mode</span>
              <span className="target-value">{schedule.enabled ? `${schedule.recurrence} (configured in SETTINGS)` : 'Manual launch only'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Scheduling storage</span>
              <span className="target-value">Browser-local (this page only)</span>
            </div>
          </div>
          <p className="create-note">
            Launching here always starts immediately. Scheduled/automatic launches are configured in the
            SCHEDULING / AUTOMATION panel (SETTINGS view) and run only while the page is open.
          </p>
          <div className="create-actions">
            <SecondaryButton onClick={() => goBack('PAYLOAD')}>← BACK</SecondaryButton>
            <PrimaryButton arrow onClick={() => goNext('REVIEW')}>NEXT REVIEW</PrimaryButton>
          </div>
        </div>
      )}

      {subTab === 'REVIEW' && (
        <div className="create-form">
          <div className="target-info">
            <div className="target-row">
              <span className="target-label">Scenario</span>
              <span className="target-value">{template ? template.name : config.typeId}{unimplemented ? ' (not implemented yet)' : ''}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Source</span>
              <span className="target-value">{config.sourceId || '—'}{config.multiSource === true ? ' + rotation across all identities' : ''}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Target user</span>
              <span className="target-value">{config.targetUser || '—'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Attempts</span>
              <span className="target-value">{Number(config.attempts) || 1}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Requested interval</span>
              <span className="target-value">{Number(config.interval) || 0}s</span>
            </div>
            <div className="target-row">
              <span className="target-label">Effective interval</span>
              <span className="target-value">{effectiveInterval}s{effectiveInterval !== (Number(config.interval) || 0) ? ' (safety-clamped)' : ''}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Mode</span>
              <span className="target-value">{MODE_LABELS[config.mode] || config.mode}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Safety guard</span>
              <span className="target-value">{safety.safeMode ? 'ON' : 'OFF (launch blocked)'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Rate limiting</span>
              <span className="target-value">{safety.rateLimiting ? 'ON (min 0.3s)' : 'OFF'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Target validation</span>
              <span className="target-value">{safety.targetValidation ? 'ON (lab_target only)' : 'OFF'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Payload sanitization</span>
              <span className="target-value">{safety.payloadSanitization ? 'ON' : 'OFF'}</span>
            </div>
            <div className="target-row">
              <span className="target-label">Scheduling</span>
              <span className="target-value">{schedule.enabled ? `${schedule.recurrence} (separate from this launch)` : 'Manual launch'}</span>
            </div>
          </div>
          {error && <p className="create-error">{error}</p>}
          <div className="create-actions">
            <SecondaryButton onClick={() => goBack('SCHEDULE')}>← BACK</SecondaryButton>
            <PrimaryButton
              type="button"
              arrow={false}
              className="create-launch"
              onClick={handleLaunch}
              disabled={busy || unimplemented}
            >
              {busy ? 'STARTING…' : unimplemented ? 'NOT IMPLEMENTED' : 'START SIMULATION'}
            </PrimaryButton>
          </div>
        </div>
      )}
    </PanelShell>
  )
}
