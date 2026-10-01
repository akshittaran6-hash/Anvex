import PanelShell from '../../components/PanelShell.jsx'
import Toggle from '../../components/Toggle.jsx'
import { TextField, SelectField } from '../../components/FormControls.jsx'
import { PrimaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import { RECURRENCES, formatScheduleTime } from '../scheduler.js'
import './scheduling-automation.css'

export default function SchedulingAutomation() {
  const { schedule, nextRun, scheduleNotification, armScheduleTimer, setSchedule, simulation } = useLab()

  function handleSave(e) {
    e.preventDefault()
    armScheduleTimer()
    setScheduleNotification(null)
  }

  return (
    <PanelShell
      title="9"
      titleAccent="SCHEDULING / AUTOMATION"
      className="schedule-panel"
    >
      <form className="schedule-form" onSubmit={handleSave}>
        <Toggle
          label="Scheduled Launch"
          sub="Automatically start the configured simulation (configured in CREATE). Runs only while this page is open."
          on={schedule.enabled}
          onChange={(v) => setSchedule({ enabled: v })}
        />
        <div className={`schedule-fields ${schedule.enabled ? '' : 'schedule-fields-off'}`}>
          <div className="schedule-row schedule-row-3">
            <TextField label="Start Date" value={schedule.date} onChange={(v) => setSchedule({ date: v })} type="date" />
            <TextField label="Time" value={schedule.time} onChange={(v) => setSchedule({ time: v })} type="time" />
            <SelectField label="Recurrence" value={schedule.recurrence} onChange={(v) => setSchedule({ recurrence: v })} options={RECURRENCES} />
          </div>
          <div className="schedule-toggles">
            <Toggle
              label="Auto-stop after completion"
              sub="End the run automatically when the scheduled simulation finishes."
              on={schedule.autoStop}
              onChange={(v) => setSchedule({ autoStop: v })}
            />
            <Toggle
              label="In-app notification on completion"
              sub="Show a notification banner in this panel (not a system notification)."
              on={schedule.notify}
              onChange={(v) => setSchedule({ notify: v })}
            />
          </div>
        </div>
        {scheduleNotification && (
          <p className="schedule-notification">NOTIFICATION: {scheduleNotification}</p>
        )}
        {simulation && simulation.status === 'completed' && schedule.notify && (
          <p className="schedule-notification">
            NOTIFICATION: Simulation "{simulation.name}" completed at {formatScheduleTime(simulation.completedAt)}
          </p>
        )}
        <div className="schedule-summary">
          <div className="schedule-summary-row">
            <span className="schedule-summary-label">Next Run</span>
            <span className="schedule-summary-value">
              {schedule.enabled && nextRun ? formatScheduleTime(nextRun) : '—'}
            </span>
          </div>
          <div className="schedule-summary-row">
            <span className="schedule-summary-label">Recurrence</span>
            <span className="schedule-summary-value">{schedule.enabled ? schedule.recurrence : 'Disabled'}</span>
          </div>
          <div className="schedule-summary-row">
            <span className="schedule-summary-label">Target Simulation</span>
            <span className="schedule-summary-value">Current CREATE configuration</span>
          </div>
        </div>
        <p className="schedule-local-note">
          Scheduling is stored locally in this browser and runs only while this page is open. It is not
          persisted on the ANVEX backend. SAVE SCHEDULE arms exactly one timer (or none when disabled).
        </p>
        <div className="schedule-actions">
          <PrimaryButton arrow={false}>{schedule.enabled ? 'SAVE & ARM SCHEDULE' : 'SAVE SCHEDULE'}</PrimaryButton>
        </div>
      </form>
    </PanelShell>
  )
}
