import { reviewableFields } from './employmentFields.js'

export const TEMPLATE_FIELD_ORDER = [
  'case.application_type',
  'document.footer_id',
  'helper.name.full_en',
  'helper.travel_doc.number',
  'helper.date_of_birth',
  'helper.nationality',
  'helper.signature.present',
  'employer.name.full_en',
  'employer.signature.present',
  'contract.dh_contract_no',
  'contract.monthly_wage_hkd',
  'contract.food.allowance_hkd'
]

export const TEMPLATE_STATUS_LABELS = {
  pass: '通过',
  unrecognized: '未识别',
  required_missing: '必填未填写',
  review: '待复核'
}

export const TEMPLATE_STATUS_LEGEND = [
  { status: 'pass', label: TEMPLATE_STATUS_LABELS.pass, text: '已识别并通过字段规则。' },
  { status: 'unrecognized', label: TEMPLATE_STATUS_LABELS.unrecognized, text: '未取得可靠字段结果。' },
  { status: 'required_missing', label: TEMPLATE_STATUS_LABELS.required_missing, text: '必填位置为空或未检测到填写痕迹。' },
  { status: 'review', label: TEMPLATE_STATUS_LABELS.review, text: '低置信度或跨文件不一致，需人工确认。' }
]

const FIELD_SECTIONS = [
  {
    id: 'case',
    title: '案件信息',
    keys: ['case.application_type', 'document.footer_id']
  },
  {
    id: 'helper',
    title: '雇工信息',
    keys: [
      'helper.name.full_en',
      'helper.travel_doc.number',
      'helper.date_of_birth',
      'helper.nationality',
      'helper.signature.present'
    ]
  },
  {
    id: 'employer',
    title: '雇主信息',
    keys: ['employer.name.full_en', 'employer.signature.present']
  },
  {
    id: 'contract',
    title: '合约信息',
    keys: ['contract.dh_contract_no', 'contract.monthly_wage_hkd', 'contract.food.allowance_hkd']
  }
]

const EMPTY_VALUE_PATTERNS = [
  /^$/,
  /^blank$/i,
  /^missing$/i,
  /^n\/a$/i,
  /^未识别$/,
  /^未检测到$/,
  /^未填写$/
]

export function buildVerificationTemplate(result, options = {}) {
  const resultFields = reviewableFields(result?.fields || [])
  const fieldByKey = new Map(resultFields.map((field) => [field.key, field]))
  const orderedKeys = new Set(TEMPLATE_FIELD_ORDER)
  const orderedFieldRows = TEMPLATE_FIELD_ORDER
    .map((key) => fieldByKey.get(key))
    .filter(Boolean)
    .filter(shouldShowField)
    .map(toTemplateFieldRow)
  const extraFieldRows = resultFields
    .filter((field) => !orderedKeys.has(field.key))
    .filter(shouldShowField)
    .map(toTemplateFieldRow)
  const fieldRows = [...orderedFieldRows, ...extraFieldRows]

  const materialRows = (result?.materials || [])
    .filter((material) => material.applicable && material.status !== 'muted')
    .map(toTemplateMaterialRow)

  const summary = buildSummary(result, fieldRows, materialRows)
  return {
    decision: result?.decision || 'REVIEW',
    decisionText: result?.decisionText || '',
    summary,
    summaryText: buildSummaryText(summary),
    overallBullets: buildOverallBullets(result, options, summary),
    caseRows: buildCaseRows(result, options, summary),
    sections: [
      ...FIELD_SECTIONS.map((section) => ({
        ...section,
        rows: fieldRows.filter((row) => section.keys.includes(row.key))
      })),
      ...extraFieldSections(extraFieldRows)
    ].filter((section) => section.rows.length),
    fieldRows,
    materialRows
  }
}

function extraFieldSections(fieldRows) {
  const sections = new Map()
  for (const row of fieldRows) {
    const title = row.category || '其他识别字段'
    const id = `extra:${title}`
    if (!sections.has(id)) {
      sections.set(id, {
        id,
        title,
        keys: [],
        rows: []
      })
    }
    sections.get(id).keys.push(row.key)
    sections.get(id).rows.push(row)
  }
  return Array.from(sections.values())
}

function shouldShowField(field) {
  if (!field) return false
  if (field.status === 'muted') return false
  if (!field.required && !field.sources?.length && !hasValue(field.normalizedValue)) return false
  return true
}

function toTemplateFieldRow(field) {
  const status = templateFieldStatus(field)
  const conflicts = fieldConflicts(field)
  const recommendedValue = field.suggestedValue || recommendedFieldValue(field, conflicts)
  return {
    key: field.key,
    category: field.category,
    label: field.label,
    required: Boolean(field.required),
    status,
    statusLabel: TEMPLATE_STATUS_LABELS[status],
    displayValue: displayValueForStatus(status, recommendedValue),
    normalizedValue: cleanValue(field.normalizedValue),
    note: fieldNote(field, status, conflicts, recommendedValue),
    rule: field.rule || '',
    sources: field.sources || [],
    conflicts
  }
}

function templateFieldStatus(field) {
  const sources = field.sources || []
  const sourceValues = sources.map((source) => cleanValue(source.value))
  const hasSource = sources.length > 0
  const hasBlankSource = sourceValues.some((value) => !hasValue(value))
  const normalizedValue = cleanValue(field.normalizedValue)

  if (!hasSource) return 'unrecognized'
  if (field.required && hasBlankSource) return 'required_missing'
  if (!hasValue(normalizedValue)) return field.required ? 'required_missing' : 'unrecognized'
  if (field.status === 'review') return 'review'
  if (field.status === 'fail') return 'review'
  return 'pass'
}

function fieldConflicts(field) {
  const groups = new Map()
  for (const source of field.sources || []) {
    const value = cleanValue(source.value)
    if (!hasValue(value)) continue
    if (!groups.has(value)) {
      groups.set(value, {
        value,
        confidence: 0,
        sources: []
      })
    }
    const group = groups.get(value)
    group.confidence = Math.max(group.confidence, Number(source.confidence) || 0)
    group.sources.push(`${source.documentName} / ${source.section} / ${source.fieldName}`)
  }
  return Array.from(groups.values())
}

function recommendedFieldValue(field, conflicts) {
  const normalizedParts = cleanValue(field.normalizedValue)
    .split(/\s+\/\s+/)
    .map((part) => cleanValue(part))
    .filter(hasValue)
  if (conflicts.length > 1) {
    return [...conflicts].sort((left, right) => right.confidence - left.confidence)[0].value
  }
  if (normalizedParts.length) return normalizedParts[0]
  if (conflicts.length) return conflicts[0].value
  return cleanValue(field.normalizedValue)
}

function displayValueForStatus(status, value) {
  if (status === 'required_missing') return '未填写'
  if (status === 'unrecognized') return '未识别'
  return hasValue(value) ? value : '未识别'
}

function fieldNote(field, status, conflicts, recommendedValue) {
  if (status === 'pass') return '已识别并通过。'
  if (status === 'required_missing') return field.issue || '必填字段未填写，需退回补正。'
  if (status === 'unrecognized') return field.issue || '未取得可靠识别结果，需要人工查看原件。'
  if (field.correctionApplied) {
    return field.suggestionReason || `建议采用“${recommendedValue}”，该字段仍需人工复核。`
  }
  if (conflicts.length > 1) {
    return `跨文件不一致，Minutes 草拟建议采用“${recommendedValue}”，需人工复核。`
  }
  return field.issue || '置信度偏低，需人工复核。'
}

function toTemplateMaterialRow(material) {
  return {
    id: material.id,
    no: material.no,
    name: material.name,
    shortName: material.shortName,
    templateId: material.templateId,
    status: material.status,
    statusLabel: materialStatusLabel(material),
    uploaded: Boolean(material.uploaded),
    blocking: Boolean(material.blocking),
    issue: material.issue || material.statusText || '',
    uploadedFilenames: material.uploadedFilenames || []
  }
}

function materialStatusLabel(material) {
  if (material.status === 'pass') return '已识别'
  if (material.status === 'fail') return '材料缺失'
  if (material.status === 'review') return '待复核'
  return '已记录'
}

function buildSummary(result, fieldRows, materialRows) {
  return {
    expectedMaterials: materialRows.length,
    uploadedMaterials: result?.uploadedFiles?.length || 0,
    missingCoreMaterials: materialRows.filter((row) => row.blocking && row.status === 'fail').length,
    materialReview: materialRows.filter((row) => row.status === 'review').length,
    requiredFields: result?.stats?.required ?? fieldRows.filter((row) => row.required).length,
    passFields: fieldRows.filter((row) => row.status === 'pass').length,
    unrecognizedFields: fieldRows.filter((row) => row.status === 'unrecognized').length,
    requiredMissingFields: fieldRows.filter((row) => row.status === 'required_missing').length,
    reviewFields: fieldRows.filter((row) => row.status === 'review').length
  }
}

function buildSummaryText(summary) {
  return `核心材料缺失 ${summary.missingCoreMaterials} 份；必填字段 ${summary.requiredFields} 项，必填未填写 ${summary.requiredMissingFields} 项，未识别 ${summary.unrecognizedFields} 项，待复核 ${summary.reviewFields} 项。`
}

function buildOverallBullets(result, options, summary) {
  return [
    {
      label: '申请类别',
      value: '外籍家庭佣工入境审核'
    },
    {
      label: '细分类别',
      value: options.applicationTypeLabel || result?.applicationTypeId || '未识别'
    },
    {
      label: '应上传/已上传材料',
      value: `${summary.expectedMaterials}/${summary.uploadedMaterials}`
    },
    {
      label: '字段填写情况',
      value: `必填字段(${summary.requiredFields})，必填未填写(${summary.requiredMissingFields})，未识别(${summary.unrecognizedFields})，待复核(${summary.reviewFields})`
    }
  ]
}

function buildCaseRows(result, options, summary) {
  return [
    {
      label: '申请类别',
      value: '外籍家庭佣工入境审核',
      status: 'pass'
    },
    {
      label: '细分类别',
      value: options.applicationTypeLabel || result?.applicationTypeId || '未识别',
      status: options.applicationTypeLabel ? 'pass' : 'unrecognized'
    },
    {
      label: '已上传材料',
      value: `${result?.uploadedFiles?.length || 0} 份`,
      status: 'pass'
    },
    {
      label: '字段核验概况',
      value: `通过 ${summary.passFields} 项 / 未识别 ${summary.unrecognizedFields} 项 / 必填未填写 ${summary.requiredMissingFields} 项 / 待复核 ${summary.reviewFields} 项`,
      status: summary.requiredMissingFields || summary.unrecognizedFields || summary.reviewFields ? 'review' : 'pass'
    }
  ]
}

function cleanValue(value) {
  return String(value ?? '').trim()
}

function hasValue(value) {
  const cleaned = cleanValue(value)
  return !EMPTY_VALUE_PATTERNS.some((pattern) => pattern.test(cleaned))
}
