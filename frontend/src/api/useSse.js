import { useEffect, useRef, useState } from 'react'

export default function useSse(path, onEvent) {
  const [connected, setConnected] = useState(false)
  const handlerRef = useRef(onEvent)
  handlerRef.current = onEvent

  useEffect(() => {
    let source = null
    let retryTimer = null
    let disposed = false

    function connect() {
      if (disposed) return
      source = new EventSource(path)
      source.onopen = () => setConnected(true)
      source.onmessage = (message) => {
        try {
          const event = JSON.parse(message.data)
          handlerRef.current && handlerRef.current(event)
        } catch {
          /* ignore malformed frames */
        }
      }
      source.onerror = () => {
        setConnected(false)
        source.close()
        retryTimer = setTimeout(connect, 3000)
      }
    }

    connect()
    return () => {
      disposed = true
      clearTimeout(retryTimer)
      if (source) source.close()
    }
  }, [path])

  return connected
}
