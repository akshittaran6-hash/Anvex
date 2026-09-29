import { useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Tabs from '../../components/Tabs.jsx'
import { TextField, SelectField, RadioGroup } from '../../components/FormControls.jsx'
import { PrimaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import EventPayloadPreview from './EventPayloadPreview.jsx'
import './create-simulation.css'

const SUB_TABS = ['BASIC', 'TARGET', 'PAYLOAD', 'SCHEDULE', 'REVIEW']

export default function CreateSimulation({ onStart }) {
  const { sources, templates, startSimulation, config, updateConfig } = useLab()
  const [subTab, setSubTab] = useState('BASIC')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const template = templates.find((t) => t.id === config.typeId)

  function applyTemplate(id) {
    const tpl = templates.find((t) => t.id === id)
    if (tpl) {
      updateConfig({
        typeId: id,
        name: tpl.name,
        attempts: tpl.config.attempts,
        interval: tpl.config.interval,
        mode: tpl.config.mode
      })
    }
  }

  function handleStart(e) {
    e.preventDefault()
    if (!config.sourceId) {
      setError('Select a source identity')
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
      multiSource: config.mode === 'custom'
    }).catch(() => {})
    onStart()
  }

  return (
    <PanelShell title="3" titleAccent="CREATE SIMULATION" className="create-panel">
      <div className="create-head">
        <Tabs tabs={SUB_TABS} active={subTab} onChange={setSubTab} />
      </div>
      {subTab === 'BASIC' && (
        <form className="create-form" onSubmit={handleStart}>
          <TextField label="Simulation Name" value={config.name} onChange={(v) => updateConfig({ name: v })} />
          <SelectField
            label="Simulation Type"
            value={config.typeId}
            onChange={applyTemplate}
            options={templates.map((t) => ({ value: t.id, label: t.name }))}
          />
          <SelectField
            label="Source Identity"
            value={config.sourceId}
            onChange={(v) => updateConfig({ sourceId: v })}
            options={sources.map((s) => ({ value: s.id, label: `${s.id} (${s.ip})` }))}
          />
          <div className="create-row">
            <TextField label="Target User" value={config.targetUser} onChange={(v) => updateConfig({ targetUser: v })} />
            <TextField label="Target Service" value="ANVEX Core Authentication (Event 4625)" onChange={() => {}} disabled />
          </div>
          <div className="create-row">
            <TextField label="Attempts" value={config.attempts} onChange={(v) => updateConfig({ attempts: v })} type="number" />
            <TextField label="Interval (seconds)" value={config.interval} onChange={(v) => updateConfig({ interval: v })} type="number" />
          </div>
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
          {error && <p className="create-error">{error}</p>}
          <div className="create-actions">
            <PrimaryButton disabled={busy}>{busy ? 'STARTING' : 'NEXT TARGET'}</PrimaryButton>
          </div>
        </form>
      )}
      {subTab === 'PAYLOAD' && <EventPayloadPreview />}
      {subTab !== 'BASIC' && subTab !== 'PAYLOAD' && (
        <p className="create-placeholder">{subTab.charAt(0) + subTab.slice(1).toLowerCase()} configuration pending.</p>
      )}
    </PanelShell>
  )
}
