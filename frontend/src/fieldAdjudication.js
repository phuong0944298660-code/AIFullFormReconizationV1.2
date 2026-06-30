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
  if (cleanValue(result.applicationTypeId) === 'iang_recent_in_hk') {
    return []
  }
  return (result.fields || [])
    .map(localFieldAdjudication)
    .filter(Boolean)
}

export function applyFieldAdjudications(fields = [], adjudications = [], options = {}) {
  const localMap = new Map(localFieldAdjudications({
    applicationTypeId: options.applicationTypeId,
    fields
  }).map((item) => [item.key, item]))
  const remoteMap = new Map((adjudications || [])
    .filter((item) => item?.key)
    .map((item) => [item.key, normalizeAdjudication(item)]))

  return fields.map((field) => {
    const adjudication = chooseAdjudication(field.key, remoteMap.get(field.key), localMap.get(field.key))
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

function chooseAdjudication(fieldKey, remote, local) {
  if (
    cleanValue(fieldKey) === 'contract.dh_contract_no' &&
    remote &&
    local &&
    remote.suggestedValue !== local.suggestedValue
  ) {
    return {
      ...remote,
      suggestedValue: local.suggestedValue,
      status: 'review',
      corrected: true,
      reason: local.reason || remote.reason
    }
  }
  return remote || local
}

function localFieldAdjudication(field = {}) {
  if (!['fail', 'review'].includes(cleanValue(field.status))) return null
  const conflicts = sourceValueGroups(field)
  if (conflicts.length <= 1) {
    if (
      cleanValue(field.key) !== 'contract.dh_contract_no' ||
      conflicts.length !== 1 ||
      !hasContractNumberPrefixRepair(field)
    ) {
      return null
    }
  }

  const suggested = chooseSuggestedValue(field, conflicts)
  return {
    key: field.key,
    suggestedValue: suggested.value,
    status: 'review',
    corrected: true,
    source: 'rules_fallback',
    reason: suggestionReason(field.key, suggested.value)
  }
}

function hasContractNumberPrefixRepair(field = {}) {
  return (field.sources || []).some((source) => {
    const raw = cleanValue(source.value)
    return hasValue(raw) && raw !== normalizeFieldValue(field.key, raw)
  })
}

function normalizeAdjudication(item = {}) {
  const key = cleanValue(item.key)
  return {
    key,
    suggestedValue: normalizeFieldValue(key, item.suggestedValue),
    status: cleanValue(item.status) || 'review',
    corrected: Boolean(item.corrected),
    source: cleanValue(item.source),
    reason: cleanValue(item.reason)
  }
}

function sourceValueGroups(field = {}) {
  const groups = new Map()
  for (const source of field.sources || []) {
    const value = normalizeFieldValue(field.key, source.value)
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

function normalizeFieldValue(fieldKey, value) {
  const cleaned = cleanValue(value)
  if (cleanValue(fieldKey) === 'contract.dh_contract_no') {
    return normalizeDhContractNumber(cleaned)
  }
  return cleaned
}

function normalizeDhContractNumber(value) {
  const compact = value.replace(/\s+/g, '')
  const upper = compact.toUpperCase()
  const requiredPrefix = 'FH-CON-'
  const prefixIndex = upper.indexOf(requiredPrefix)
  if (prefixIndex === 0) {
    return requiredPrefix + compact.slice(requiredPrefix.length)
  }
  if (prefixIndex > 0 && prefixIndex <= 3) {
    return requiredPrefix + compact.slice(prefixIndex + requiredPrefix.length)
  }
  if (/^[A-Z0-9]H-CON-[A-Z]{2,3}-?\d{2,4}-\d{3,}$/i.test(compact)) {
    return `F${compact.slice(1)}`
  }
  return value
}

function suggestionReason(fieldKey, suggestedValue) {
  if (cleanValue(fieldKey) === 'contract.dh_contract_no') {
    return `建议采用值“${suggestedValue}”；标准雇佣合约编号前缀必须为 FH-CON-，年份优先选择不超过当前年份且最接近当前年份的值；该字段跨文件不一致，仍需人工复核。`
  }
  return `建议采用值“${suggestedValue}”；该字段跨文件不一致，仍需人工复核。`
}

function chooseSuggestedValue(field = {}, groups = []) {
  return [...groups].sort((left, right) => {
    if (cleanValue(field.key) === 'contract.dh_contract_no') {
      const currentYear = new Date().getFullYear()
      const leftYear = contractYearRank(left.value, currentYear)
      const rightYear = contractYearRank(right.value, currentYear)
      if (rightYear !== leftYear) return rightYear > leftYear ? 1 : -1
    }
    return compareByReliability(left, right)
  })[0]
}

function compareByReliability(left, right) {
  if (right.priority !== left.priority) return right.priority - left.priority
  if (right.confidence !== left.confidence) return right.confidence - left.confidence
  return right.sources.length - left.sources.length
}

function contractYearRank(value, currentYear) {
  const year = contractYear(value)
  return Number.isInteger(year) && year <= currentYear ? year : Number.NEGATIVE_INFINITY
}

function contractYear(value) {
  const match = cleanValue(value).match(/(?:^|[^0-9])((?:19|20)\d{2})(?=$|[^0-9])/)
  return match ? Number(match[1]) : null
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
