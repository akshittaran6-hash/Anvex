import { useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Tabs from '../../components/Tabs.jsx'
import { TextField, SelectField, RadioGroup } from '../../components/FormControls.jsx'
import { PrimaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './create-simulation.css'

const SUB_TABS = ['BASIC', 'TARGET', 'PAYLOAD', 'SCHEDULE', 'REVIEW']

export default function CreateSimulation({ onStart }) {
  const { sources, templates, startSimulation } = useLab()
  const [subTab, setSubTab] = useState('BASIC')
  const [name, setName] = useState('Windows Failed Login Test')
  const [typeId, setType] = useState('failed-login')
  const [sourceId, setSourceIdentity] = useState(sources[0]?.id || '')
  const [targetUser, setTargetUser] = useState('lab_target')
  const [attempts, setAttempts] = useState(10)
  const [interval, setIntervalSeconds] = useState(5)
  const [mode, setMode] = useState('realistic')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const template = templates.find((t) => t.id === typeId)

  function applyTemplate(id) {
    setType(id)
    const tpl = templates.find((t) => t.id === id)
    if (tpl) {
      setName(tpl.name)
      setAttempts(tpl.config.attempts)
      setIntervalSeconds(tpl.config.interval)
      setMode(tpl.config.mode)
    }
  }

  function handleStart(e) {
    e.preventDefault()
    if (!sourceId) {
      setError('Select a source identity')
      return
    }
    setBusy(true)
    setError('')
    startSimulation({
      name,
      type: template ? template.name : typeId,
      sourceId,
      targetUser,
      attempts: Number(attempts) || 1,
      interval: Number(interval) || 0,
      mode,
      multiSource: mode === 'custom'
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
          <TextField label="Simulation Name" value={name} onChange={setName} />
          <SelectField
            label="Simulation Type"
            value={typeId}
            onChange={applyTemplate}
            options={templates.map((t) => ({ value: t.id, label: t.name }))}
          />
          <SelectField
            label="Source Identity"
            value={sourceId}
            onChange={setSourceIdentity}
            options={sources.map((s) => ({ value: s.id, label: `${s.id} (${s.ip})` }))}
          />
          <div className="create-row">
            <TextField label="Target User" value={targetUser} onChange={setTargetUser} />
            <TextField label="Target Service" value="ANVEX Core Authentication (Event 4215)" onChange={() => {}} disabled />
          </div>
          <div className="create-row">
            <TextField label="Attempts" value={attempts} onChange={setAttempts} type="number" />
            <TextField label="Interval (seconds)" value={interval} onChange={setIntervalSeconds} type="number" />
          </div>
          <RadioGroup
            label="Simulation Mode"
            value={mode}
            onChange={setMode}
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
      {subTab !== 'BASIC' && (
        <p className="create-placeholder">{subTab.charAt(0) + subTab.slice(1).toLowerCase()} configuration pending.</p>
      )}
    </PanelShell>
  )
}
