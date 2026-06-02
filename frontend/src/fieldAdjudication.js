const EMPTY_VALUE_PATTERNS = [
  /^$/,
  /^blank$/i,
  /^missing$/i,
  /^n\/a$/i,
  /^未识别$/,
  /^未检测到$/,
  /^未填写$/
]

export function localFieldAdjudications(result = {}) {
  return (result.fields || [])
    .map(localFieldAdjudication)
    .filter(Boolean)
}

export function applyFieldAdjudications(fields = [], adjudications = []) {
  const localMap = new Map(localFieldAdjudications({ fields }).map((item) => [item.key, item]))
  const remoteMap = new Map((adjudications || [])
    .filter((item) => item?.key)
    .map((item) => [item.key, normalizeAdjudication(item)]))

  return fields.map((field) => {
    const adjudication = remoteMap.get(field.key) || localMap.get(field.key)
    const rawNormalizedValue = cleanValue(field.normalizedValue)
    if (!adjudication) {
      return {
        ...field,
        rawNormalizedValue,
        suggestedValue: rawNormalizedValue,
        suggestionReason: '',
        correctionApplied: false
      }
    }

    const corrected = Boolean(adjudication.corrected)
    return {
      ...field,
      status: corrected ? 'review' : adjudication.status || field.status,
      rawNormalizedValue,
      suggestedValue: adjudication.suggestedValue || rawNormalizedValue,
      suggestionReason: adjudication.reason || '',
      correctionApplied: corrected
    }
  })
}

function localFieldAdjudication(field = {}) {
  if (!['fail', 'review'].includes(cleanValue(field.status))) return null
  const conflicts = sourceValueGroups(field)
  if (conflicts.length <= 1) return null

  const suggested = chooseSuggestedValue(field, conflicts)
  return {
    key: field.key,
    suggestedValue: suggested.value,
    status: 'review',
    corrected: true,
    source: 'rules_fallback',
    reason: `规则兜底建议采用值“${suggested.value}”；该字段跨文件不一致，仍需人工复核。`
  }
}

function normalizeAdjudication(item = {}) {
  return {
    key: cleanValue(item.key),
    suggestedValue: cleanValue(item.suggestedValue),
    status: cleanValue(item.status) || 'review',
    corrected: Boolean(item.corrected),
    source: cleanValue(item.source),
    reason: cleanValue(item.reason)
  }
}

function sourceValueGroups(field = {}) {
  const groups = new Map()
  for (const source of field.sources || []) {
    const value = cleanValue(source.value)
    if (!hasValue(value)) continue
    if (!groups.has(value)) {
      groups.set(value, {
        value,
        confidence: 0,
        priority: 0,
        sources: []
      })
    }
    const group = groups.get(value)
    group.confidence = Math.max(group.confidence, Number(source.confidence) || 0)
    group.priority = Math.max(group.priority, sourcePriority(field.key, source.documentName))
    group.sources.push(source)
  }
  return Array.from(groups.values())
}

function chooseSuggestedValue(field = {}, groups = []) {
  return [...groups].sort((left, right) => {
    if (right.priority !== left.priority) return right.priority - left.priority
    if (right.confidence !== left.confidence) return right.confidence - left.confidence
    return right.sources.length - left.sources.length
  })[0]
}

function sourcePriority(fieldKey, documentName) {
  const key = cleanValue(fieldKey)
  const document = cleanValue(documentName)
  if (key === 'contract.dh_contract_no') {
    if (document.includes('ID 407')) return 80
    if (document.includes('ID 988A')) return 70
    if (document.includes('ID 988B')) return 60
  }
  if (document.includes('ID 407')) return 40
  if (document.includes('ID 988A')) return 35
  if (document.includes('ID 988B')) return 30
  return 10
}

function cleanValue(value) {
  return String(value ?? '').trim()
}

function hasValue(value) {
  const cleaned = cleanValue(value)
  return !EMPTY_VALUE_PATTERNS.some((pattern) => pattern.test(cleaned))
}
