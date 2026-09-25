import './panel-shell.css'

export default function PanelShell({ title, titleAccent, actions, children, className = '', fill = false }) {
  return (
    <section className={`panel-shell ${fill ? 'panel-fill' : ''} ${className}`}>
      <i className="corner corner-tl" />
      <i className="corner corner-tr" />
      <i className="corner corner-bl" />
      <i className="corner corner-br" />
      {(title || actions) && (
        <header className="panel-header">
          <h2 className="panel-title">
            {title && <span className="panel-title-index">{title}</span>}
            {titleAccent && <span className="panel-title-accent">{titleAccent}</span>}
          </h2>
          {actions && <div className="panel-actions">{actions}</div>}
        </header>
      )}
      <div className="panel-body">{children}</div>
    </section>
  )
}
