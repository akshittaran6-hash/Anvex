import { useMemo, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Tabs from '../../components/Tabs.jsx'
import { TextField } from '../../components/FormControls.jsx'
import { SecondaryButton } from '../../components/Buttons.jsx'
import { CATEGORIES } from '../data/defaults.js'
import { useLab } from '../lab-context.jsx'
import './scenario-templates.css'

const TEMPLATE_ICONS = {
  'failed-login': '↻',
  'repeated-burst': '⇉',
  'threshold-test': '△',
  'multi-source': '◈',
  'service-error': '✕',
  'custom-json': '{}'
}

export default function ScenarioTemplates({ onNavigate }) {
  const { templates, config, updateConfig } = useLab()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('All')

  const filtered = useMemo(() => {
    return templates.filter((t) => {
      const matchesCategory = category === 'All' || t.category === category
      const q = search.trim().toLowerCase()
      const matchesSearch = !q || t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q)
      return matchesCategory && matchesSearch
    })
  }, [templates, category, search])

  function useTemplate(id) {
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
    if (onNavigate) onNavigate('CREATE')
  }

  return (
    <PanelShell
      title="5"
      titleAccent="SCENARIO TEMPLATES"
      className="templates-panel"
    >
      <div className="templates-head">
        <div className="templates-search">
          <TextField label="" value={search} onChange={setSearch} placeholder="Search templates..." />
        </div>
        <Tabs tabs={CATEGORIES} active={category} onChange={setCategory} />
      </div>
      <div className="templates-list">
        {filtered.length === 0 && (
          <p className="templates-empty">No templates match the current filters.</p>
        )}
        {filtered.map((tpl) => (
          <div key={tpl.id} className={`template-card ${config.typeId === tpl.id ? 'template-active' : ''}`}>
            <span className="template-icon">{TEMPLATE_ICONS[tpl.id] || '◇'}</span>
            <div className="template-info">
              <span className="template-name">{tpl.name}</span>
              <span className="template-desc">{tpl.description}</span>
              <span className="template-meta">{tpl.category} · {tpl.config.attempts} attempts · {tpl.config.interval}s interval</span>
            </div>
            <SecondaryButton className="template-use" onClick={() => useTemplate(tpl.id)}>Use</SecondaryButton>
          </div>
        ))}
      </div>
    </PanelShell>
  )
}
