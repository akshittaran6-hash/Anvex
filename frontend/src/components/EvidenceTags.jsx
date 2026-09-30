import { useMemo } from 'react'
import './evidence-tags.css'

export default function EvidenceTags({ evidence }) {
  const tags = useMemo(() => {
    if (!evidence) return []
    const list = []
    if (evidence.failedAttempts !== undefined && evidence.failedAttempts !== null) {
      list.push({ label: `${evidence.failedAttempts} failed`, accent: 'orange' })
    }
    if (evidence.windowMs) {
      list.push({ label: `${(evidence.windowMs / 1000).toFixed(0)}s window`, accent: 'cyan' })
    }
    if (evidence.frequency) {
      list.push({ label: `${evidence.frequency.toFixed(1)}/s`, accent: 'cyan' })
    }
    if (evidence.thresholdExceeded) {
      list.push({ label: 'threshold exceeded', accent: 'red' })
    }
    return list
  }, [evidence])

  if (tags.length === 0) return null
  return (
    <div className="evidence-tags">
      {tags.map((tag, i) => (
        <span key={i} className={`evidence-tag tag-${tag.accent}`}>{tag.label}</span>
      ))}
    </div>
  )
}
