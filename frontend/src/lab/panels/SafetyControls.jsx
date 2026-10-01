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
            {safety.safeMode ? 'SIMULATION SAFETY ACTIVE' : 'SIMULATION SAFETY GUARD OFF'}
          </span>
          <span className="safety-desc">
            Browser-side guard for this lab client. It permits or blocks launching new simulations from
            this browser. It does not alter ANVEX backend protection, threat scoring or blocking.
          </span>
        </div>
        <StatusPill on={safety.safeMode} label={safety.safeMode ? 'GUARD ON' : 'GUARD OFF'} />
      </div>

      <div className="safety-toggles">
        <Toggle
          label="Simulation Safety Guard"
          sub="Permits new browser simulations when ON; blocks launching when OFF."
          on={safety.safeMode}
          onChange={(v) => updateSafety({ safeMode: v })}
        />
        {safety.safeMode === false && (
          <p className="safety-warning">New simulations cannot be launched from this browser while the guard is OFF.</p>
        )}
        <Toggle
          label="Rate Limiting"
          sub="Enforce a minimum browser-side interval (0.3s) between attempts."
          on={safety.rateLimiting}
          onChange={(v) => updateSafety({ rateLimiting: v })}
        />
        <Toggle
          label="Target Validation"
          sub="Restrict browser simulations to the designated target user (lab_target)."
          on={safety.targetValidation}
          onChange={(v) => updateSafety({ targetValidation: v })}
        />
        <Toggle
          label="Payload Sanitization"
          sub="Strip unsafe delimiters (|, CR, LF) from simulation inputs."
          on={safety.payloadSanitization}
          onChange={(v) => updateSafety({ payloadSanitization: v })}
        />
      </div>

      <div className="safety-footnote">
        These controls govern this browser's simulation behavior only. Threat detection, protection
        responses and blocking are decided by the ANVEX Java backend.
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
          {running
            ? 'Stops the running simulation loop and ends the run. Requests already received by the backend are still processed by it.'
            : 'No simulation is currently running.'}
        </span>
      </div>
    </PanelShell>
  )
}
