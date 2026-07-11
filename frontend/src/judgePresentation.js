const VALUE_BBOX_MISSING_REASONS = new Set([
  '',
  'judge_not_run',
  'value_bbox_missing',
  'value_crop_failed',
  'evidence_missing'
])

const JUDGE_CALL_FAILURE_REASONS = new Set([
  'field_processing_failed',
  'invalid_response',
  'network_error',
  'interrupted',
  'judge_disabled',
  'judge_not_configured'
])

export function judgeDisplayState(field = {}) {
  const reason = String(field.verificationReason || '').trim().toLowerCase()
  if (reason === 'not_applicant_value' || isDocumentMetadata(field)) {
    return { status: 'not_required', reasonKey: 'judgeNotRequired' }
  }

  const score = field.verificationScore
  if (score !== null && score !== undefined && Number.isFinite(Number(score))) {
    const status = String(field.verificationStatus || field.status || 'review').toLowerCase()
    return { status: ['pass', 'review', 'fail'].includes(status) ? status : 'review', reasonKey: '' }
  }

  if (JUDGE_CALL_FAILURE_REASONS.has(reason) || reason.startsWith('http_')) {
    return { status: 'review', reasonKey: 'judgeCallFailed' }
  }
  if (VALUE_BBOX_MISSING_REASONS.has(reason)) {
    return { status: 'review', reasonKey: 'llmValueBboxMissing' }
  }
  return { status: 'review', reasonKey: 'judgeCallFailed' }
}

function isDocumentMetadata(field) {
  const label = String(field.label || field.fieldName || '').trim().toLowerCase()
  return ['source file', 'total pages', 'source_file', 'total_pages', 'no applicant input', 'no_applicant_input'].includes(label)
}

export function localizedJudgeReason(reason, language) {
  const value = String(reason || '').trim()
  if (!value.startsWith('{')) return value
  try {
    const localized = JSON.parse(value)
    if (!localized || typeof localized !== 'object') return value
    return language === 'zh-Hant' || language === 'zh'
      ? String(localized['zh-Hant'] || localized.en || value)
      : String(localized.en || localized['zh-Hant'] || value)
  } catch {
    return value
  }
}
