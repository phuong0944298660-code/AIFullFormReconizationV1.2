export function deriveReviewDecision(result = {}) {
  const materials = Array.isArray(result.materials) ? result.materials : []
  const fields = Array.isArray(result.fields) ? result.fields : []

  const blockingMaterialFail = materials.find((item) => item?.blocking && item.status === 'fail')
  if (blockingMaterialFail) {
    return {
      decision: 'FAIL',
      decisionText: issueText(blockingMaterialFail.shortName, blockingMaterialFail.issue)
    }
  }

  const blockingFieldFail = fields.find((field) => field?.blocking && field.status === 'fail')
  if (blockingFieldFail) {
    return {
      decision: 'FAIL',
      decisionText: issueText(blockingFieldFail.label, blockingFieldFail.issue || blockingFieldFail.rule)
    }
  }

  const blockingReview = [
    ...materials.filter((item) => item?.blocking && item.status === 'review')
      .map((item) => issueText(item.shortName, item.issue || item.statusText)),
    ...fields.filter((field) => field?.blocking && field.status === 'review')
      .map((field) => issueText(field.label, field.issue || field.suggestionReason || field.rule))
  ].filter(Boolean)

  if (blockingReview.length) {
    return {
      decision: 'REVIEW',
      decisionText: blockingReview[0]
    }
  }

  return {
    decision: 'PASS',
    decisionText: '核心材料 1-3 齐全，必填字段可识别，关键字段跨文件一致，允许通过。'
  }
}

export function deriveFieldStats(fields = []) {
  const rows = Array.isArray(fields) ? fields : []
  return {
    total: rows.length,
    pass: rows.filter((field) => field.status === 'pass').length,
    fail: rows.filter((field) => field.status === 'fail').length,
    review: rows.filter((field) => field.status === 'review').length,
    required: rows.filter((field) => field.required).length
  }
}

function issueText(label, issue) {
  const title = String(label || '').trim()
  const detail = String(issue || '').trim()
  if (title && detail) return `${title}：${detail}`
  return title || detail
}
