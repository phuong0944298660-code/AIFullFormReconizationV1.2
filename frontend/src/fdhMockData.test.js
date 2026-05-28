import test from 'node:test'
import assert from 'node:assert/strict'
import { buildReviewResult } from './fdhMockData.js'

test('PASS scenario allows approval when core documents and fields pass', () => {
  const result = buildReviewResult('entry_visa', 'pass')

  assert.equal(result.decision, 'PASS')
  assert.equal(result.materials.find((item) => item.id === 'id988a').status, 'pass')
  assert.equal(result.materials.find((item) => item.id === 'id988b').status, 'pass')
  assert.equal(result.materials.find((item) => item.id === 'id407').status, 'pass')
  assert.equal(result.fields.some((field) => field.status === 'fail' && field.blocking), false)
})

test('missing core document fails approval', () => {
  const result = buildReviewResult('entry_visa', 'missing_core')
  const id407 = result.materials.find((item) => item.id === 'id407')

  assert.equal(result.decision, 'FAIL')
  assert.equal(id407.status, 'fail')
  assert.equal(id407.blocking, true)
})

test('non-core checklist gaps do not fail approval', () => {
  const result = buildReviewResult('entry_visa', 'pass')
  const financialProof = result.materials.find((item) => item.id === 'financialProof')

  assert.equal(financialProof.status, 'warn')
  assert.equal(financialProof.blocking, false)
  assert.equal(result.decision, 'PASS')
})

test('obvious cross-document mismatch fails approval', () => {
  const result = buildReviewResult('entry_visa', 'field_mismatch')
  const helperName = result.fields.find((field) => field.key === 'helper.name.full_en')

  assert.equal(result.decision, 'FAIL')
  assert.equal(helperName.status, 'fail')
  assert.equal(helperName.blocking, true)
})

test('low confidence or tiny differences require review instead of fail', () => {
  const result = buildReviewResult('entry_visa', 'review_low_confidence')
  const travelDoc = result.fields.find((field) => field.key === 'helper.travel_doc.number')

  assert.equal(result.decision, 'REVIEW')
  assert.equal(travelDoc.status, 'review')
})
