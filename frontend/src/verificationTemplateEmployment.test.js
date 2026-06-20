import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildReviewResult } from './fdhMockData.js'
import { buildVerificationTemplate } from './verificationTemplate.js'

function recognizedField(key, label, normalizedValue, withSource = false) {
  return {
    key,
    category: 'materials',
    label,
    required: false,
    normalizedValue,
    status: 'pass',
    issue: '',
    blocking: false,
    sources: withSource
      ? [{ documentName: 'ID 988A', section: 'page_2', fieldName: label, value: normalizedValue, confidence: 95 }]
      : [],
    rule: 'Recognized from uploaded material.'
  }
}

test('verification template excludes employment period fields but keeps total duration', () => {
  const result = buildReviewResult('change_employer', 'pass')
  const fields = [
    ...result.fields,
    recognizedField('extracted.employer_1_name', 'Name of employer (s)', 'Mrs. Linda CHEN'),
    recognizedField('extracted.employer_1_address', 'Address', 'Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong'),
    recognizedField('extracted.employer_2_period_from', 'From (mm/yy)', '08/22'),
    recognizedField('extracted.total_duration_years', 'total duration years', '6', true),
    recognizedField('extracted.total_duration_months', 'total duration months', '9', true)
  ]

  const template = buildVerificationTemplate({ ...result, fields })
  const keys = template.fieldRows.map((row) => row.key)

  assert.equal(keys.includes('extracted.employer_1_name'), false)
  assert.equal(keys.includes('extracted.employer_1_address'), false)
  assert.equal(keys.includes('extracted.employer_2_period_from'), false)
  assert.equal(keys.includes('extracted.total_duration_years'), true)
  assert.equal(keys.includes('extracted.total_duration_months'), true)
})
