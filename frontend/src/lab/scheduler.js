const SCHEDULE_KEY = 'anvex.schedule'

export const DEFAULT_SCHEDULE = {
  enabled: false,
  date: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
  time: '14:30',
  recurrence: 'Daily',
  autoStop: true,
  notify: true
}

export const RECURRENCES = ['Once', 'Hourly', 'Daily', 'Weekly'].map((r) => ({ value: r, label: r }))

export function loadSchedule() {
  try {
    const saved = localStorage.getItem(SCHEDULE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      return { ...DEFAULT_SCHEDULE, ...parsed }
    }
  } catch {
    /* fall through to defaults */
  }
  return { ...DEFAULT_SCHEDULE }
}

export function saveSchedule(schedule) {
  localStorage.setItem(SCHEDULE_KEY, JSON.stringify(schedule))
}

export function parseScheduleDate(date, time) {
  const base = new Date(`${date}T${time || '00:00'}`)
  return Number.isNaN(base.getTime()) ? null : base
}

export function nextOccurrence(nowMs, schedule) {
  const base = parseScheduleDate(schedule.date, schedule.time)
  if (!base) return null

  if (base.getTime() > nowMs) {
    return base
  }

  const next = new Date(nowMs)
  next.setHours(base.getHours(), base.getMinutes(), 0, 0)
  if (next.getTime() <= nowMs) {
    if (schedule.recurrence === 'Hourly') {
      next.setHours(next.getHours() + 1)
    } else if (schedule.recurrence === 'Weekly') {
      const targetWeekday = base.getDay()
      const currentWeekday = next.getDay()
      let delta = (targetWeekday - currentWeekday + 7) % 7
      if (delta === 0) {
        delta = 7
      }
      next.setDate(next.getDate() + delta)
    } else {
      next.setDate(next.getDate() + 1)
    }
  }
  return next
}

export function formatScheduleTime(value) {
  if (!value) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

export function formatClock(value) {
  const date = value instanceof Date ? value : new Date(value)
  return date.toLocaleTimeString('en-GB', { hour12: false })
}
