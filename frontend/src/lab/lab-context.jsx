import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { SCENARIO_TEMPLATES, SOURCE_IDENTITIES } from './data/defaults.js'
import { api, setToken, clearToken } from '../api/client.js'
import { loadSchedule, saveSchedule, nextOccurrence, formatClock } from './scheduler.js'

const LabContext = createContext(null)

export function LabProvider({ children }) {
  const [authed, setAuthed] = useState(false)
  const [operator, setOperator] = useState('')
  const [templates] = useState(SCENARIO_TEMPLATES)
  const [sources, setSources] = useState(SOURCE_IDENTITIES)
  const [simulation, setSimulation] = useState(null)
  const [config, setConfig] = useState({
    name: 'Windows Failed Login Test',
    typeId: 'failed-login',
    sourceId: SOURCE_IDENTITIES[0]?.id || '',
    targetUser: 'lab_target',
    attempts: 10,
    interval: 5,
    mode: 'realistic',
    multiSource: false
  })
  const [safety, setSafety] = useState({
    safeMode: true,
    rateLimiting: true,
    targetValidation: true,
    payloadSanitization: true
  })
  const [schedule, setScheduleState] = useState(loadSchedule)
  const [nextRun, setNextRun] = useState(null)
  const [scheduleNotification, setScheduleNotification] = useState('')
  const [backendStatus, setBackendStatus] = useState(null)
  const [backendLatency, setBackendLatency] = useState(null)

  const stopRef = useRef(false)
  const timerRef = useRef(null)
  const waitResolveRef = useRef(null)
  const abortRef = useRef(null)
  const scheduleRef = useRef(schedule)
  scheduleRef.current = schedule
  const simRef = useRef(simulation)
  simRef.current = simulation
  const startRef = useRef(null)
  const configRef = useRef(config)
  configRef.current = config

  const updateConfig = useCallback((partial) => {
    setConfig((prev) => ({ ...prev, ...partial }))
  }, [])

  const updateSafety = useCallback((partial) => {
    setSafety((prev) => ({ ...prev, ...partial }))
  }, [])

  const sanitize = useCallback((value) => {
    return String(value).replace(/[|\r\n]/g, '').trim()
  }, [])

  const setSchedule = useCallback((partial) => {
    setScheduleState((prev) => {
      const updated = typeof partial === 'function' ? partial(prev) : { ...prev, ...partial }
      saveSchedule(updated)
      return updated
    })
  }, [])

  const armScheduleTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    const current = scheduleRef.current
    if (!current.enabled) {
      setNextRun(null)
      return
    }
    const when = nextOccurrence(Date.now(), current)
    if (!when) {
      setNextRun(null)
      setScheduleNotification('Scheduled start failed: invalid date or time.')
      return
    }
    setNextRun(when)
    const delay = Math.max(0, when.getTime() - Date.now())
    timerRef.current = setTimeout(() => {
      timerRef.current = null
      const s = scheduleRef.current
      if (!s.enabled) return
      const active = simRef.current && simRef.current.status === 'running'
      if (!active) {
        startRef.current({
          name: `Scheduled Run (${s.recurrence})`,
          type: 'Scheduled Run',
          sourceId: configRef.current.sourceId,
          targetUser: configRef.current.targetUser,
          attempts: Number(configRef.current.attempts) || 10,
          interval: Number(configRef.current.interval) || 5,
          mode: configRef.current.mode,
          multiSource: configRef.current.multiSource,
          autoStopRun: s.autoStop
        }, () => {
          setScheduleNotification(`Scheduled run started at ${formatClock(Date.now())}`)
        }).catch((err) => {
          setScheduleNotification(`Scheduled start failed: ${err.message}`)
        })
      }
      if (s.recurrence !== 'Once') {
        armScheduleTimer()
      } else {
        setNextRun(null)
      }
    }, delay)
  }, [])

  useEffect(() => {
    armScheduleTimer()
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [armScheduleTimer])

  useEffect(() => {
    let cancelled = false
    async function poll() {
      const started = performance.now()
      try {
        const result = await api.get('/api/status')
        if (cancelled) return
        setBackendStatus(result)
        setBackendLatency(Math.round(performance.now() - started))
      } catch {
        if (!cancelled) setBackendStatus(null)
      }
    }
    poll()
    const timer = setInterval(poll, 5000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  const signIn = useCallback(async (operatorName, token, remember) => {
    setToken(remember ? token : '')
    if (!remember) {
      sessionStorage.setItem('anvex.sessionToken', token)
    }
    await api.get('/api/status')
    setOperator(operatorName)
    setAuthed(true)
  }, [])

  const signOut = useCallback(() => {
    clearToken()
    sessionStorage.removeItem('anvex.sessionToken')
    stopRef.current = true
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    if (waitResolveRef.current) {
      waitResolveRef.current()
      waitResolveRef.current = null
    }
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
    setAuthed(false)
    setOperator('')
    setSimulation(null)
  }, [])

  const stopSimulation = useCallback(() => {
    stopRef.current = true
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    if (waitResolveRef.current) {
      waitResolveRef.current()
      waitResolveRef.current = null
    }
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
    setSimulation((prev) => {
      if (!prev || prev.status !== 'running') return prev
      if (prev.runId) {
        api.post('/api/runs/end', null)
          .catch((err) => {
            setSimulation((p) => (p ? { ...p, endError: `Failed to end run: ${err.message}` } : p))
          })
      }
      return { ...prev, status: 'stopped', completedAt: Date.now() }
    })
  }, [])

  const startSimulation = useCallback(async (startConfig, onStarted) => {
    if (safety.safeMode !== true) {
      throw new Error('Simulation safety guard is OFF. Enable it in LAB SAFETY before launching.')
    }
    if (safety.targetValidation && String(startConfig.targetUser).trim() !== 'lab_target') {
      throw new Error('Target validation is ON: browser simulations are restricted to lab_target.')
    }

    const { name, type, sourceId, targetUser, attempts, interval, mode } = startConfig
    const run = await api.post('/api/runs', { label: sanitize(name) })
    let effectiveInterval = mode === 'burst' ? Math.min(interval, 0.5) : interval
    if (safety.rateLimiting) {
      effectiveInterval = Math.max(effectiveInterval, 0.3)
    }

    stopRef.current = false
    abortRef.current = new AbortController()

    const attemptSources = startConfig.multiSource
      ? sources.map((s) => s.id)
      : [sourceId]

    setSimulation({
      runId: run.runId,
      name,
      type,
      sourceId,
      targetUser,
      attempts,
      interval: effectiveInterval,
      mode,
      sent: 0,
      failed: 0,
      blocked: 0,
      status: 'running',
      startedAt: Date.now(),
      log: [],
      endError: null
    })

    if (onStarted) onStarted()

    const OUTCOME_TYPES = { SUCCESS: 'LOGIN_SUCCESS', FAILURE: 'LOGIN_FAILURE', BLOCKED: 'LOGIN_BLOCKED' }

    for (let i = 1; i <= attempts; i++) {
      if (stopRef.current) break

      const attemptSource = attemptSources[(i - 1) % attemptSources.length]
      let entry
      try {
        const response = await api.post('/api/simulate', {
          username: safety.payloadSanitization ? sanitize(targetUser) : targetUser,
          password: `wrong-guess-${i}`,
          clientType: 'ATTACKER',
          sourceId: safety.payloadSanitization ? sanitize(attemptSource) : attemptSource
        }, abortRef.current.signal)
        entry = {
          time: formatClock(Date.now()),
          type: OUTCOME_TYPES[response.outcome] || 'LOGIN_FAILURE',
          outcome: response.outcome,
          protectionAction: response.protectionAction || null
        }
      } catch (error) {
        if (stopRef.current || error.name === 'AbortError') {
          break
        }
        entry = {
          time: formatClock(Date.now()),
          type: 'ERROR',
          outcome: 'ERROR',
          error: error.message
        }
      }

      setSimulation((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          sent: Math.min(i, attempts),
          failed: prev.failed + (entry && entry.outcome === 'FAILURE' ? 1 : 0),
          blocked: prev.blocked + (entry && entry.outcome === 'BLOCKED' ? 1 : 0),
          log: entry ? [...prev.log, entry] : prev.log
        }
      })

      if (i < attempts && effectiveInterval > 0 && !stopRef.current) {
        await new Promise((resolve) => {
          waitResolveRef.current = resolve
          timerRef.current = setTimeout(() => {
            waitResolveRef.current = null
            resolve()
          }, effectiveInterval * 1000)
        })
      }
    }

    timerRef.current = null
    waitResolveRef.current = null
    abortRef.current = null

    if (!stopRef.current) {
      if (startConfig.autoStopRun !== false) {
        api.post('/api/runs/end', null)
          .catch((err) => {
            setSimulation((p) => (p ? { ...p, endError: `Failed to end run: ${err.message}` } : p))
          })
      }
      setSimulation((prev) => (prev ? { ...prev, status: 'completed', completedAt: Date.now() } : prev))
    }
  }, [sources, safety, sanitize])

  startRef.current = startSimulation

  const addSource = useCallback((source) => {
    setSources((prev) => [...prev, source])
  }, [])

  const value = useMemo(() => ({
    authed,
    operator,
    templates,
    sources,
    simulation,
    config,
    updateConfig,
    safety,
    updateSafety,
    schedule,
    nextRun,
    scheduleNotification,
    setScheduleNotification,
    armScheduleTimer,
    backendStatus,
    backendLatency,
    signIn,
    signOut,
    startSimulation,
    stopSimulation,
    addSource
  }), [authed, operator, templates, sources, simulation, config, updateConfig, safety, updateSafety,
    schedule, nextRun, scheduleNotification, setScheduleNotification, armScheduleTimer, backendStatus, backendLatency,
    signIn, signOut, startSimulation, stopSimulation, addSource])

  return <LabContext.Provider value={value}>{children}</LabContext.Provider>
}

export function useLab() {
  const context = useContext(LabContext)
  if (!context) {
    throw new Error('useLab must be used inside LabProvider')
  }
  return context
}
