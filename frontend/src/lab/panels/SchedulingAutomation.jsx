import { useCallback, useEffect, useRef, useState } from 'react'
import PanelShell from '../../components/PanelShell.jsx'
import Toggle from '../../components/Toggle.jsx'
import { TextField, SelectField } from '../../components/FormControls.jsx'
import { PrimaryButton } from '../../components/Buttons.jsx'
import { useLab } from '../lab-context.jsx'
import './scheduling-automation.css'

const RECURRENCES = ['Once', 'Hourly', 'Daily', 'Weekly'].map((r) => ({ value: r, label: r }))
const SCHEDULE_KEY = 'anvex.schedule'

function nextOccurrence(now, date, time, recurrence) {
  const base = new Date(`${date}T${time || '00:00'}`)
  if (!Number.isNaN(base.getTime()) && base.getTime() > now.getTime()) {
    return base
  }
  const next = new Date(now)
  if (Number.isNaN(base.getTime())) {
    return null
  }
  next.setHours(base.getHours(), base.getMinutes(), 0, 0)
  if (next.getTime() <= now.getTime()) {
    if (recurrence === 'Hourly') {
      next.setHours(next.getHours() + 1)
    } else if (recurrence === 'Weekly') {
      next.setDate(next.getDate() + 7)
    } else {
      next.setDate(next.getDate() + 1)
    }
  }
  return next
}

function formatNextRun(date) {
  return date.toLocaleString('en-GB', {
    day: 'short',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

export default function SchedulingAutomation() {
  const { config, startSimulation, simulation } = useLab()
  const [schedule, setSchedule] = useState(() => {
    try {
      const saved = localStorage.getItem(SCHEDULE_KEY)
      if (saved) return JSON.parse(saved)
    } catch {
      /* fall through to defaults */
    }
    return {
      enabled: false,
      date: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      time: '14:30',
      recurrence: 'Daily',
      endTime: '15:00',
      window: 15,
      autoStop: true,
      notify: true
    }
  })
  const [nextRun, setNextRun] = useState(null)
  const [savedNote, setSavedNote] = useState('')
  const [lastNotification, setLastNotification] = useState('')
  const timerRef = useRef(null)
  const scheduleRef = useRef(schedule)
  scheduleRef.current = schedule
  const simRef = useRef(simulation)
  simRef.current = simulation
  const startRef = useRef(startSimulation)
  startRef.current = startSimulation
  const configRef = useRef(config)
  configRef.current = config

  const armTimer = useCallback((current) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    if (!current.enabled) {
      setNextRun(null)
      return
    }
    const when = nextOccurrence(Date.now(), current.date, current.time, current.recurrence)
    if (!when) {
      setNextRun(null)
      return
    }
    setNextRun(when)
    const delay = Math.max(0, when.getTime() - Date.now())
    timerRef.current = setTimeout(() => {
      const s = scheduleRef.current
      if (!s.enabled) return
      const active = simRef.current && simRef.current.status === 'running'
      if (!active) {
        startRef.current({
          name: `Scheduled Run (${s.recurrence})`,
          type: 'Scheduled Run',
          sourceId: configRef.current.sourceId,
          targetUser: 'lab_target',
          attempts: Number(configRef.current.attempts) || 10,
          interval: Number(configRef.current.interval) || 5,
          mode: configRef.current.mode,
          autoStopRun: s.autoStop
        }).catch(() => {})
        setLastNotification(`Scheduled run started at ${new Date().toLocaleTimeString('en-GB', { hour12: false })}`)
      }
      if (s.recurrence !== 'Once') {
        armTimer(s)
      } else {
        setNextRun(null)
      }
    }, delay)
  }, [])

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  useEffect(() => {
    if (simulation && simulation.status === 'completed' && schedule.notify) {
      setLastNotification(
        `Simulation "${simulation.name}" completed at ${new Date(simulation.completedAt || Date.now()).toLocaleTimeString('en-GB', { hour12: false })}`
      )
    }
  }, [simulation, schedule.notify])

  function handleSave(e) {
    e.preventDefault()
    const updated = schedule
    localStorage.setItem(SCHEDULE_KEY, JSON.stringify(updated))
    armTimer(updated)
    setSavedNote(
      updated.enabled
        ? `Saved locally. Next run: ${nextRun ? formatNextRun(nextRun) : '—'}`
        : 'Saved locally. Scheduling is disabled — timer not armed.'
    )
  }

  return (
    <PanelShell
      title="9"
      titleAccent="SCHEDULING / AUTOMATION"
      className="schedule-panel"
    >
      <form className="schedule-form" onSubmit={handleSave}>
        <Toggle
          label="Schedule Recurrence"
          sub="Start a scheduled simulation automatically."
          on={schedule.enabled}
          onChange={(v) => setSchedule((p) => ({ ...p, enabled: v }))}
        />
        <div className={`schedule-fields ${schedule.enabled ? '' : 'schedule-fields-off'}`}>
          <div className="schedule-row schedule-row-3">
            <TextField label="Start Date" value={schedule.date} onChange={(v) => setSchedule((p) => ({ ...p, date: v }))} type="date" />
            <TextField label="Time" value={schedule.time} onChange={(v) => setSchedule((p) => ({ ...p, time: v }))} type="time" />
            <SelectField label="Recurrence" value={schedule.recurrence} onChange={(v) => setSchedule((p) => ({ ...p, recurrence: v }))} options={RECURRENCES} />
          </div>
          <div className="schedule-row schedule-row-2">
            <TextField label="Time Window (min)" value={schedule.window} onChange={(v) => setSchedule((p) => ({ ...p, window: v }))} type="number" />
            <TextField label="End Time" value={schedule.endTime} onChange={(v) => setSchedule((p) => ({ ...p, endTime: v }))} type="time" />
          </div>
          <div className="schedule-toggles">
            <Toggle
              label="Auto-stop after completion"
              on={schedule.autoStop}
              onChange={(v) => setSchedule((p) => ({ ...p, autoStop: v }))}
            />
            <Toggle
              label="Send notification on completion"
              on={schedule.notify}
              onChange={(v) => setSchedule((p) => ({ ...p, notify: v }))}
            />
          </div>
        </div>
        {lastNotification && (
          <p className="schedule-notification">NOTIFICATION: {lastNotification}</p>
        )}
        <div className="schedule-summary">
          <div className="schedule-summary-row">
            <span className="schedule-summary-label">Next Run</span>
            <span className="schedule-summary-value">
              {schedule.enabled && nextRun ? formatNextRun(nextRun) : '—'}
            </span>
          </div>
          <div className="schedule-summary-row">
            <span className="schedule-summary-label">Recurrence</span>
            <span className="schedule-summary-value">{schedule.enabled ? schedule.recurrence : 'Disabled'}</span>
          </div>
        </div>
        {savedNote && <p className="schedule-saved">{savedNote}</p>}
        <p className="schedule-local-note">
          Schedules are stored locally in this browser and run only while this page is open. They are not
          persisted on the ANVEX backend.
        </p>
        <div className="schedule-actions">
          <PrimaryButton>SAVE SCHEDULE</PrimaryButton>
        </div>
      </form>
    </PanelShell>
  )
}
