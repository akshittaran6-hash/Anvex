import TopBar from '../components/TopBar.jsx'

export default function LabApp() {
  return (
    <div className="app-shell">
      <TopBar system="system1" />
      <main className="app-main lab-main">
        <div className="lab-grid">
          <section className="panel-shell">
            <header className="panel-header">
              <h2 className="panel-title">
                <span className="panel-title-index">1</span> ADMIN LOGIN
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
