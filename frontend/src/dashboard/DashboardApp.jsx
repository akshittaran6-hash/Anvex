import TopBar from '../components/TopBar.jsx'

export default function DashboardApp() {
  return (
    <div className="app-shell">
      <TopBar system="system2" activeTab="LIVE" />
      <main className="app-main dashboard-main">
        <div className="dashboard-grid">
          <section className="panel-shell">
            <header className="panel-header">
              <h2 className="panel-title">
                <span className="panel-title-index">LIVE</span> READY
              </h2>
            </header>
            <div className="panel-body">
              <p style={{ color: 'var(--text-dim)' }}>Panel implementation pending.</p>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
