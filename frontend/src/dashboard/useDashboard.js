import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import useSse from '../api/useSse.js'

export default function useDashboard(revision) {
  const [data, setData] = useState({ status: null, sources: [], runs: [], metrics: null, events: [] })
  const [pending, setPending] = useState([])
  const [error, setError] = useState('')
  const [connected, setConnected] = useState(false)
  const [authRequired, setAuthRequired] = useState(false)
  const cursor = useRef(0)
  const refreshRef = useRef(() => {})
  const timer = useRef(null)
  const serial = useRef(0)
  const sse = useSse('/api/events/stream', event => {
    setPending(previous => [{ ...event, liveKey: ++serial.current }, ...previous].slice(0, 100))
    clearTimeout(timer.current)
    timer.current = setTimeout(() => refreshRef.current(), 150)
  }, Boolean(data.status) && !authRequired, revision)

  useEffect(() => {
    let disposed = false
    let running = false
    async function refresh() {
      if (running || disposed) return
      running = true
      try {
        const [status, sources, runs, metrics] = await Promise.all([
          api.get('/api/status'), api.get('/api/sources'), api.get('/api/runs'), api.get('/api/metrics')
        ])
        // Drain canonical history by backend ID, including any gap after reconnect.
        let collected = []
        let next = cursor.current
        let page
        do {
          page = await api.get(`/api/events?after=${next}&limit=200`)
          collected.push(...page)
          if (page.length) next = page.at(-1).id
        } while (page.length === 200 && !disposed)
        if (disposed) return
        cursor.current = next
        setData(previous => ({ status, sources, runs, metrics,
          events: [...previous.events, ...collected].slice(-200) }))
        // Persisted history replaces provisional SSE frames; missing SSE metadata stays absent.
        setPending(previous => {
          const signature = event => JSON.stringify([event.type, event.source, event.outcome, event.level, event.message])
          const counts = new Map()
          collected.forEach(event => { const key = signature(event); counts.set(key, (counts.get(key) || 0) + 1) })
          return previous.slice().reverse().filter(event => {
            const key = signature(event)
            const count = counts.get(key) || 0
            if (!count) return true
            counts.set(key, count - 1)
            return false
          }).reverse()
        })
        setConnected(true)
        setAuthRequired(false)
        setError('')
      } catch (failure) {
        if (disposed) return
        setConnected(false)
        setAuthRequired(failure.status === 401)
        setError(failure.status === 401 ? 'API token required or invalid.' : failure.message)
      } finally { running = false }
    }
    refreshRef.current = refresh
    refresh()
    const interval = setInterval(refresh, 5000)
    return () => { disposed = true; clearInterval(interval); clearTimeout(timer.current) }
  }, [revision])
  useEffect(() => { if (sse === 'connected') refreshRef.current() }, [sse])
  const refresh = useCallback(() => refreshRef.current(), [])
  return { ...data, events: [...pending, ...data.events.slice().reverse()].slice(0, 200),
    connected, authRequired, error, sse, refresh }
}
