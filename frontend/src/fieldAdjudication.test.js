import test from 'node:test'
import assert from 'node:assert/strict'
import { applyFieldAdjudications, localFieldAdjudications } from './fieldAdjudication.js'

const conflictField = {
  key: 'contract.dh_contract_no',
  category: '合约字段',
  label: '标准雇佣合约编号',
  required: true,
  normalizedValue: 'FH-CON-IDN2026-0612 / FH-CON-IDN2016-0612 / RFH-CON-IDN-2026-0612',
  status: 'fail',
  issue: '跨文件字段值明显不一致。',
  sources: [
    { documentName: 'ID 988A', section: '承诺', fieldName: 'Employment contract no.', value: 'FH-CON-IDN2026-0612', confidence: 98 },
    { documentName: 'ID 988B', section: '承诺', fieldName: 'Employment contract no.', value: 'FH-CON-IDN2016-0612', confidence: 95 },
    { documentName: 'ID 407', section: '合约首页', fieldName: 'Contract No.', value: 'RFH-CON-IDN-2026-0612', confidence: 98 }
  ],
  rule: 'ID 988A、ID 988B 与 ID 407 的标准雇佣合约编号必须完整填写并保持一致。'
}

test('localFieldAdjudications keeps corrected conflict fields in review with a suggested value', () => {
  const [adjudication] = localFieldAdjudications({ fields: [conflictField] })

  assert.equal(adjudication.key, 'contract.dh_contract_no')
  assert.equal(adjudication.status, 'review')
  assert.equal(adjudication.corrected, true)
  assert.equal(adjudication.suggestedValue, 'FH-CON-IDN-2026-0612')
  assert.match(adjudication.reason, /建议采用值/)
})

test('applyFieldAdjudications lets backend LLM suggestion override local rule fallback', () => {
  const [field] = applyFieldAdjudications([conflictField], [
    {
      key: 'contract.dh_contract_no',
      suggestedValue: 'RFH-CON-IDN-2026-0612',
      status: 'review',
      corrected: true,
      reason: 'LLM 结合 ID 407 合约首页判断该值最可靠。'
    }
  ])

  assert.equal(field.status, 'review')
  assert.equal(field.suggestedValue, 'FH-CON-IDN-2026-0612')
  assert.equal(field.correctionApplied, true)
  assert.match(field.suggestionReason, /ID 407/)
  assert.equal(field.rawNormalizedValue, conflictField.normalizedValue)
})

test('applyFieldAdjudications keeps the contract year rule when backend suggestion uses a future year', () => {
  const currentYear = new Date().getFullYear()
  const futureYear = currentYear + 10
  const fieldWithFutureCandidate = {
    ...conflictField,
    normalizedValue: `FH-CON-IDN-${currentYear}-0612 / RFH-CON-IDN-${futureYear}-0612`,
    sources: [
      { documentName: 'ID 988A', section: 'Undertaking', fieldName: 'Employment contract no.', value: `FH-CON-IDN-${currentYear}-0612`, confidence: 98 },
      { documentName: 'ID 407', section: 'Contract cover', fieldName: 'Contract No.', value: `RFH-CON-IDN-${futureYear}-0612`, confidence: 98 }
    ]
  }

  const [field] = applyFieldAdjudications([fieldWithFutureCandidate], [
    {
      key: 'contract.dh_contract_no',
      suggestedValue: `RFH-CON-IDN-${futureYear}-0612`,
      status: 'review',
      corrected: true,
      reason: 'LLM suggested the future-year value.'
    }
  ])

  assert.equal(field.suggestedValue, `FH-CON-IDN-${currentYear}-0612`)
  assert.equal(field.status, 'review')
  assert.equal(field.correctionApplied, true)
  assert.match(field.suggestionReason, /current year|当前年份|褰撳墠骞翠唤/)
})

test('contract number adjudication normalizes smudged leading R to the required FH-CON prefix', () => {
  const [field] = applyFieldAdjudications([conflictField], [])

  assert.equal(field.suggestedValue, 'FH-CON-IDN-2026-0612')
  assert.equal(field.status, 'review')
  assert.equal(field.correctionApplied, true)
})

test('contract number adjudication prefers the closest non-future year', () => {
  const currentYear = new Date().getFullYear()
  const oldYear = currentYear - 10
  const [adjudication] = localFieldAdjudications({
    fields: [{
      ...conflictField,
      normalizedValue: `FH-COW-PH${currentYear}-0708 / FH-CW-PH${currentYear}-0708 / RFH-CON-IDN-${oldYear}-0612`,
      sources: [
        { documentName: 'ID 988A', section: 'Undertaking', fieldName: 'Employment contract no.', value: `FH-COW-PH${currentYear}-0708`, confidence: 98 },
        { documentName: 'ID 988B', section: 'Undertaking', fieldName: 'Employment contract no.', value: `FH-CW-PH${currentYear}-0708`, confidence: 98 },
        { documentName: 'ID 407', section: 'Contract cover', fieldName: 'Contract No.', value: `RFH-CON-IDN-${oldYear}-0612`, confidence: 98 }
      ]
    }]
  })

  assert.equal(adjudication.suggestedValue, `FH-COW-PH${currentYear}-0708`)
  assert.equal(adjudication.status, 'review')
  assert.match(adjudication.reason, /current year|当前年份|褰撳墠骞翠唤/)
})

test('contract number adjudication ignores future years when choosing the suggested value', () => {
  const currentYear = new Date().getFullYear()
  const futureYear = currentYear + 10
  const [adjudication] = localFieldAdjudications({
    fields: [{
      ...conflictField,
      normalizedValue: `FH-CON-IDN-${currentYear}-0612 / RFH-CON-IDN-${futureYear}-0612`,
      sources: [
        { documentName: 'ID 988A', section: 'Undertaking', fieldName: 'Employment contract no.', value: `FH-CON-IDN-${currentYear}-0612`, confidence: 98 },
        { documentName: 'ID 407', section: 'Contract cover', fieldName: 'Contract No.', value: `RFH-CON-IDN-${futureYear}-0612`, confidence: 98 }
      ]
    }]
  })

  assert.equal(adjudication.suggestedValue, `FH-CON-IDN-${currentYear}-0612`)
  assert.equal(adjudication.status, 'review')
})
