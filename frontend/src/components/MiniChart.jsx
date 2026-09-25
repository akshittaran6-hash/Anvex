import './mini-chart.css'

export default function MiniChart({ values = [], accent = 'blue' }) {
  const width = 120
  const height = 30
  const max = Math.max(...values, 1)
  const step = values.length > 1 ? width / (values.length - 1) : width
  const points = values.map((v, i) => `${(i * step).toFixed(1)},${(height - (v / max) * (height - 4) - 2).toFixed(1)}`)
  return (
    <svg className={`mini-chart chart-${accent}`} width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      {points.length > 1 && <polyline points={points.join(' ')} fill="none" strokeWidth="1.5" />}
      {points.length > 0 && <circle cx={points[points.length - 1].split(',')[0]} cy={points[points.length - 1].split(',')[1]} r="2" />}
    </svg>
  )
}
