import './tabs.css'

export default function Tabs({ tabs, active, onChange, className = '' }) {
  return (
    <nav className={`tabs ${className}`}>
      {tabs.map((tab) => (
        <button
          key={tab}
          type="button"
          className={`tab ${active === tab ? 'tab-active' : ''}`}
          onClick={() => onChange && onChange(tab)}
        >
          {tab}
        </button>
      ))}
    </nav>
  )
}
