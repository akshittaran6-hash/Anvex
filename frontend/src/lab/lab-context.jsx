import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { SCENARIO_TEMPLATES, SOURCE_IDENTITIES } from './data/defaults.js'
import { api, setToken, clearToken } from '../api/client.js'

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
    mode: 'realistic'
  })
  const [safety, setSafety] = useState({
    safeMode: true,
    rateLimiting: true,
    targetValidation: true,
    payloadSanitization: true
  })
  const stopRef = useRef(false)
  const timerRef = useRef(null)

  const updateConfig = useCallback((partial) => {
    setConfig((prev) => ({ ...prev, ...partial }))
  }, [])

  const updateSafety = useCallback((partial) => {
    setSafety((prev) => ({ ...prev, ...partial }))
  }, [])

  const sanitize = useCallback((value) => {
    return String(value).replace(/[|\r\n]/g, '').trim()
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
    setSimulation((prev) => {
      if (!prev || prev.status !== 'running') return prev
      if (prev.runId) {
        api.post('/api/runs/end', null).catch(() => {})
      }
      return { ...prev, status: 'stopped', completedAt: Date.now() }
    })
  }, [])

  const startSimulation = useCallback(async (startConfig) => {
    if (safety.safeMode !== true) {
      throw new Error('Safe mode is required before running simulations. Enable it in Safety Controls.')
    }
    if (safety.targetValidation && String(startConfig.targetUser).trim() !== 'lab_target') {
      throw new Error('Target validation is ON: only the designated target (lab_target) is approved.')
    }

    const { name, type, sourceId, targetUser, attempts, interval, mode } = startConfig
    const run = await api.post('/api/runs', { label: sanitize(name) })
    let effectiveInterval = mode === 'burst' ? Math.min(interval, 0.5) : interval
    if (safety.rateLimiting) {
      effectiveInterval = Math.max(effectiveInterval, 0.3)
    }

    stopRef.current = false

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
      log: []
    })

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
        })
        entry = {
          time: new Date().toLocaleTimeString('en-GB', { hour12: false }),
          type: OUTCOME_TYPES[response.outcome] || 'LOGIN_FAILURE',
          outcome: response.outcome,
          protectionAction: response.protectionAction || null
        }
      } catch (error) {
        entry = {
          time: new Date().toLocaleTimeString('en-GB', { hour12: false }),
          type: 'ERROR',
          outcome: 'ERROR',
          error: error.message
        }
      }

      setSimulation((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          sent: i,
          failed: prev.failed + (entry.outcome === 'FAILURE' ? 1 : 0),
          blocked: prev.blocked + (entry.outcome === 'BLOCKED' ? 1 : 0),
          log: [...prev.log, entry]
        }
      })

      if (i < attempts && effectiveInterval > 0) {
        await new Promise((resolve) => {
          timerRef.current = setTimeout(resolve, effectiveInterval * 1000)
        })
      }
    }

    timerRef.current = null
    if (!stopRef.current) {
      if (startConfig.autoStopRun !== false) {
        api.post('/api/runs/end', null).catch(() => {})
      }
      setSimulation((prev) => (prev ? { ...prev, status: 'completed', completedAt: Date.now() } : prev))
    }
  }, [sources, safety, sanitize])

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
    signIn,
    signOut,
    startSimulation,
    stopSimulation,
    addSource
  }), [authed, operator, templates, sources, simulation, config, updateConfig, safety, updateSafety, signIn, signOut, startSimulation, stopSimulation, addSource])

  return <LabContext.Provider value={value}>{children}</LabContext.Provider>
}

export function useLab() {
  const context = useContext(LabContext)
  if (!context) {
    throw new Error('useLab must be used inside LabProvider')
  }
  return context
}
