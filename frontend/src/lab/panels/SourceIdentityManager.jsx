import { useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Tabs from '../../components/Tabs.jsx'
import { TextField, SelectField } from '../../components/FormControls.jsx'
import { SecondaryButton, PrimaryButton } from '../../components/Buttons.jsx'
import { SourceRow } from '../../components/RunRow.jsx'
import { useLab } from '../lab-context.jsx'
import './source-identity-manager.css'

const TABS = ['SOURCES', 'GROUPS', 'IMPORT', 'EXPORT']
const OS_OPTIONS = ['Windows', 'Linux', 'Web'].map((os) => ({ value: os, label: os }))

export default function SourceIdentityManager() {
  const { sources, config, updateConfig, addSource } = useLab()
  const [tab, setTab] = useState('SOURCES')
  const [selectedId, setSelected] = useState(sources[0]?.id || '')
  const [showAdd, setShowAdd] = useState(false)
  const [newSource, setNewSource] = useState({ id: '', os: 'Windows', ip: '', description: '' })
  const [addError, setAddError] = useState('')

  const selected = sources.find((s) => s.id === selectedId)
  const isActiveSource = config.sourceId === selectedId

  function handleAdd(e) {
    e.preventDefault()
    const id = newSource.id.trim()
    if (!id || !newSource.ip.trim()) {
      setAddError('Name and IP address are required')
      return
    }
    if (sources.some((s) => s.id === id)) {
      setAddError('A source with this name already exists')
      return
    }
    addSource({
      id,
      os: newSource.os,
      osVersion: '',
      ip: newSource.ip.trim(),
      description: newSource.description.trim() || 'Lab source',
      originating: 'ANVEX-LabSimulator',
      notes: ''
    })
    setSelected(id)
    setNewSource({ id: '', os: 'Windows', ip: '', description: '' })
    setAddError('')
    setShowAdd(false)
  }

  return (
    <PanelShell
      title="6"
      titleAccent="SOURCE IDENTITY MANAGER"
      className="sources-panel"
      actions={<SecondaryButton onClick={() => setShowAdd((v) => !v)}>+ ADD SOURCE</SecondaryButton>}
    >
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      {tab !== 'SOURCES' && (
        <p className="sources-placeholder">{tab} view is not implemented yet. Source identities are managed in the SOURCES tab.</p>
      )}
      {tab === 'SOURCES' && (
        <>
          <p className="sources-note">
            Source identities are simulated lab metadata used to label the browser's attempts. They do not
            represent real machines or verified network hosts.
          </p>
          {showAdd && (
            <form className="sources-add" onSubmit={handleAdd}>
              <TextField label="Name" value={newSource.id} onChange={(v) => setNewSource((p) => ({ ...p, id: v }))} />
              <SelectField label="OS" value={newSource.os} onChange={(v) => setNewSource((p) => ({ ...p, os: v }))} options={OS_OPTIONS} />
              <TextField label="IP Address" value={newSource.ip} onChange={(v) => setNewSource((p) => ({ ...p, ip: v }))} />
              <TextField label="Description" value={newSource.description} onChange={(v) => setNewSource((p) => ({ ...p, description: v }))} />
              <div className="sources-add-actions">
                {addError && <span className="sources-add-error">{addError}</span>}
                <PrimaryButton>ADD</PrimaryButton>
              </div>
            </form>
          )}
          <div className="sources-table">
            <div className="sources-table-head">
              <span />
              <span>SOURCE ID</span>
              <span>OS</span>
              <span>IP (SIMULATED)</span>
              <span>DESCRIPTION</span>
              <span>STATUS</span>
            </div>
            {sources.map((source) => (
              <SourceRow
                key={source.id}
                source={source}
                active={config.sourceId === source.id}
                selected={selectedId === source.id}
                onClick={() => setSelected(source.id)}
              />
            ))}
          </div>
          {selected && (
            <div className="sources-detail">
              <div className="sources-detail-head">
                <span className="sources-detail-name">
                  <i className="sources-detail-icon">{selected.os === 'Windows' ? '⊞' : selected.os === 'Linux' ? '⚙' : '◍'}</i>
                  {selected.id}
                </span>
                <span className={`sources-detail-status ${isActiveSource ? 'accent-green' : ''}`}>
                  {isActiveSource ? '● Selected for simulation' : '○ Not selected'}
                </span>
              </div>
              <div className="sources-detail-grid">
                <div className="sources-detail-field">
                  <span className="sources-detail-label">OS Type</span>
                  <span className="sources-detail-value">{selected.osVersion || selected.os}</span>
                </div>
                <div className="sources-detail-field">
                  <span className="sources-detail-label">IP (simulated metadata)</span>
                  <span className="sources-detail-value">{selected.ip}</span>
                </div>
                <div className="sources-detail-field">
                  <span className="sources-detail-label">Originating</span>
                  <span className="sources-detail-value">{selected.originating}</span>
                </div>
                <div className="sources-detail-field">
                  <span className="sources-detail-label">Description</span>
                  <span className="sources-detail-value">{selected.description}</span>
                </div>
              </div>
              {selected.notes && (
                <div className="sources-detail-field">
                  <span className="sources-detail-label">Notes</span>
                  <span className="sources-detail-value">{selected.notes}</span>
                </div>
              )}
              {!isActiveSource && (
                <div className="sources-detail-actions">
                  <PrimaryButton onClick={() => updateConfig({ sourceId: selected.id })}>
                    SET AS SELECTED SOURCE
                  </PrimaryButton>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </PanelShell>
  )
}
