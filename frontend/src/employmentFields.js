const EMPLOYMENT_PERIOD_FIELD_RE =
  /^extracted\.employer_(\d+)_(name|address|period_from|period_to)$/i
const EMPLOYMENT_PERIOD_ANCHOR_KEYS = new Set([
  'extracted.address_of_current_employer',
  'extracted.address_of_current_employer_if_applicable'
])

export function isEmploymentPeriodField(fieldOrKey) {
  const key = typeof fieldOrKey === 'string' ? fieldOrKey : fieldOrKey?.key
  return EMPLOYMENT_PERIOD_FIELD_RE.test(String(key || ''))
}

export function reviewableFields(fields = []) {
  return (Array.isArray(fields) ? fields : []).filter((field) => !isEmploymentPeriodField(field))
}

export function isEmploymentPeriodAnchorField(fieldOrKey) {
  const key = typeof fieldOrKey === 'string' ? fieldOrKey : fieldOrKey?.key
  return EMPLOYMENT_PERIOD_ANCHOR_KEYS.has(String(key || '').toLowerCase())
}

export function employmentPeriodsFromFields(fields = []) {
  const map = new Map()
  for (const field of Array.isArray(fields) ? fields : []) {
    const match = String(field?.key || '').match(EMPLOYMENT_PERIOD_FIELD_RE)
    if (!match) continue
    const n = Number(match[1])
    const part = match[2].toLowerCase()
    if (!map.has(n)) {
      map.set(n, { n, nameField: null, addressField: null, periodFromField: null, periodToField: null })
    }
    const period = map.get(n)
    if (part === 'name') period.nameField = field
    else if (part === 'address') period.addressField = field
    else if (part === 'period_from') period.periodFromField = field
    else if (part === 'period_to') period.periodToField = field
  }
  return [...map.values()].sort((a, b) => a.n - b.n)
}
