const DIFF_STATUSES = new Set(['fail', 'review'])

export function displaySourceValue(source = {}) {
  const value = source.value ?? ''
  if (String(value).trim()) return String(value)
  const fallback = source.snapshotText ?? ''
  if (String(fallback).trim()) return String(fallback)
  return '未识别'
}

export function sourceValueSegments(field = {}, source = {}) {
  const value = displaySourceValue(source)
  const chars = Array.from(value)
  if (!shouldHighlightDiff(field)) {
    return chars.map((text) => ({ text, diff: false }))
  }

  const values = (field.sources || [])
    .map(displaySourceValue)
    .filter((item) => item.trim())
  const distinct = new Set(values)
  if (distinct.size <= 1) {
    return chars.map((text) => ({ text, diff: false }))
  }

  const otherValues = values.filter((item) => item !== value)
  if (!otherValues.length) {
    return chars.map((text) => ({ text, diff: false }))
  }

  return chars.map((text, index) => ({
    text,
    diff: otherValues.some((other) => Array.from(other)[index] !== text)
  }))
}

function shouldHighlightDiff(field = {}) {
  return DIFF_STATUSES.has(String(field.status || '').toLowerCase())
}
