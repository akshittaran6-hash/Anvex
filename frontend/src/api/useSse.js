import { useEffect, useRef, useState } from 'react'
import { getToken } from './client.js'

// Fetch streaming allows the same bearer authentication as REST, without URL tokens.
export default function useSse(path, onEvent, enabled = true, revision = 0) {
  const [state, setState] = useState('disconnected')
  const handlerRef = useRef(onEvent)
  handlerRef.current = onEvent
  useEffect(() => {
    if (!enabled) { setState('disconnected'); return }
    const controller = new AbortController()
    let retryTimer
    let disposed = false
    async function connect() {
      setState('reconnecting')
      try {
        const token = getToken()
        const response = await fetch(path, {
          headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          signal: controller.signal,
          cache: 'no-store'
        })
        if (!response.ok) {
          if (response.status === 401) { setState('unauthorized'); return }
          throw new Error(`SSE ${response.status}`)
        }
        setState('connected')
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        try {
          while (!disposed) {
            const { value, done } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            let boundary
            while ((boundary = buffer.search(/\r?\n\r?\n/)) >= 0) {
              const frame = buffer.slice(0, boundary)
              buffer = buffer.slice(boundary + (buffer[boundary] === '\r' ? 4 : 2))
              const data = frame.split(/\r?\n/).filter(line => line.startsWith('data:'))
                .map(line => line.slice(5).replace(/^ /, '')).join('\n')
              if (data) {
                let event
                try { event = JSON.parse(data) } catch { continue }
                handlerRef.current?.(event)
              }
            }
          }
        } finally { reader.releaseLock() }
      } catch (error) {
        if (controller.signal.aborted) return
      }
      if (!disposed) {
        setState('reconnecting')
        retryTimer = setTimeout(connect, 3000)
      }
    }
    connect()
    return () => {
      disposed = true
      clearTimeout(retryTimer)
      controller.abort()
    }
  }, [path, enabled, revision])
  return state
}
