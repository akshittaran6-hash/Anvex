import PanelShell from '../../components/PanelShell.jsx'
import Toggle from '../../components/Toggle.jsx'
import { StatusPill } from '../../components/Badges.jsx'
import { DangerButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './safety-controls.css'

export default function SafetyControls() {
  const { safety, updateSafety, simulation, stopSimulation } = useLab()
  const running = simulation && simulation.status === 'running'

  return (
    <PanelShell
      title="8"
      titleAccent="SAFETY CONTROLS / KILL SWITCH"
      className="safety-panel"
    >
      <div className="safety-head">
        <span className={`safety-shield ${safety.safeMode ? '' : 'safety-shield-off'}`}>🛡</span>
        <div className="safety-head-text">
          <span className={`safety-title ${safety.safeMode ? '' : 'safety-title-off'}`}>
            {safety.safeMode ? 'SAFE MODE ACTIVE' : 'SAFE MODE DISABLED'}
          </span>
          <span className="safety-desc">
            All simulations are controlled, safe, and non-disruptive.
          </span>
        </div>
        <StatusPill on={safety.safeMode} label={safety.safeMode ? 'ON' : 'OFF'} />
      </div>

      <div className="safety-toggles">
        <Toggle
          label="Safe Mode Required"
          on={safety.safeMode}
          onChange={(v) => updateSafety({ safeMode: v })}
        />
        {safety.safeMode === false && (
          <p className="safety-warning">Simulations cannot start while safe mode is disabled.</p>
        )}
        <Toggle
          label="Rate Limiting"
          sub="Enforce a minimum interval to prevent overload."
          on={safety.rateLimiting}
          onChange={(v) => updateSafety({ rateLimiting: v })}
        />
        <Toggle
          label="Target Validation"
          sub="Only the designated target (lab_target) is allowed."
          on={safety.targetValidation}
          onChange={(v) => updateSafety({ targetValidation: v })}
        />
        <Toggle
          label="Payload Sanitization"
          sub="Prevent harmful or disruptive payloads."
          on={safety.payloadSanitization}
          onChange={(v) => updateSafety({ payloadSanitization: v })}
        />
      </div>

      <div className="safety-footnote">
        These controls govern the LAB CLIENT simulation behavior only. Backend threat detection and
        protection responses are handled by the ANVEX security pipeline.
      </div>

      <div className="safety-kill">
        <DangerButton
          className="safety-kill-btn"
          onClick={stopSimulation}
          disabled={!running}
        >
          ⚠ EMERGENCY STOP (KILL SWITCH)
        </DangerButton>
        <span className="safety-kill-note">
          {running ? 'Immediately stop the running simulation and end the run.' : 'No simulation is currently running.'}
        </span>
      </div>
    </PanelShell>
  )
}
