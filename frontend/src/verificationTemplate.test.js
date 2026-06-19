import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildReviewResult } from './fdhMockData.js'
import { applyFieldAdjudications } from './fieldAdjudication.js'
import {
  buildVerificationTemplate,
  TEMPLATE_FIELD_ORDER,
  TEMPLATE_STATUS_LABELS
} from './verificationTemplate.js'

test('verification template includes recognized fields outside the official core order', () => {
  const result = buildReviewResult('change_employer', 'pass')
  const extraField = {
    key: 'extracted.mobile_phone_number',
    category: 'ID 988A',
    label: 'mobile phone number',
    required: false,
    normalizedValue: '91234567',
    status: 'pass',
    issue: '',
    blocking: false,
    sources: [
      {
        documentName: 'ID 988A',
        filename: 'ID988A.pdf',
        section: 'page_1.part_2_personal_particulars',
        fieldName: 'mobile phone no',
        value: '91234567',
        confidence: 78
      }
    ],
    rule: 'Recognized from uploaded material.'
  }

  const template = buildVerificationTemplate({
    ...result,
    fields: [...result.fields, extraField]
  })

  const row = template.fieldRows.find((item) => item.key === 'extracted.mobile_phone_number')
  assert.equal(row.status, 'pass')
  assert.equal(row.displayValue, '91234567')
  assert.notEqual(row.displayValue, TEMPLATE_STATUS_LABELS.unrecognized)
  assert.ok(template.sections.some((section) => section.rows.some((item) => item.key === extraField.key)))
})

test('verification template keeps the official standardized field order', () => {
  const result = buildReviewResult('change_employer', 'pass')
  const template = buildVerificationTemplate(result, { applicationTypeLabel: '转换雇主' })

  assert.deepEqual(
    template.fieldRows.map((row) => row.key),
    TEMPLATE_FIELD_ORDER
  )
  assert.equal(template.sections[0].title, '案件信息')
  assert.equal(template.sections.at(-1).title, '合约信息')
})

test('verification template maps field outcomes to the four demo statuses', () => {
  const result = buildReviewResult('change_employer', 'missing_required_field')
  const template = buildVerificationTemplate(result, { applicationTypeLabel: '转换雇主' })

  const signature = template.fieldRows.find((row) => row.key === 'helper.signature.present')
  assert.equal(signature.status, 'required_missing')
  assert.equal(signature.statusLabel, TEMPLATE_STATUS_LABELS.required_missing)
  assert.equal(signature.displayValue, '未填写')

  const passRow = template.fieldRows.find((row) => row.key === 'helper.name.full_en')
  assert.equal(passRow.status, 'pass')
  assert.equal(passRow.statusLabel, TEMPLATE_STATUS_LABELS.pass)
})

test('verification template marks cross-document conflicts for review with a drafted value', () => {
  const result = buildReviewResult('change_employer', 'field_mismatch')
  const template = buildVerificationTemplate(result, { applicationTypeLabel: '转换雇主' })

  const helperName = template.fieldRows.find((row) => row.key === 'helper.name.full_en')
  assert.equal(helperName.status, 'review')
  assert.equal(helperName.statusLabel, TEMPLATE_STATUS_LABELS.review)
  assert.equal(helperName.displayValue, 'SITI NURHALIZA')
  assert.ok(helperName.note.includes('跨文件不一致'))
  assert.ok(helperName.conflicts.length >= 2)
})

test('verification template summarizes material and required field issues', () => {
  const result = buildReviewResult('change_employer', 'missing_core')
  const template = buildVerificationTemplate(result, { applicationTypeLabel: '转换雇主' })

  assert.equal(template.summary.missingCoreMaterials, 1)
  assert.equal(template.summary.expectedMaterials, template.materialRows.length)
  assert.equal(template.summary.uploadedMaterials, result.uploadedFiles.length)
  assert.equal(template.summary.requiredFields, result.stats.required)
  assert.ok(template.summaryText.includes('核心材料缺失 1 份'))
  assert.ok(template.materialRows.every((row) => row.status !== 'muted'))
})

test('verification template merges case summary into overall conclusion bullets', () => {
  const result = buildReviewResult('change_employer', 'field_mismatch')
  const template = buildVerificationTemplate(result, { applicationTypeLabel: '转换雇主' })

  assert.deepEqual(
    template.overallBullets.map((item) => item.label),
    ['申请类别', '细分类别', '应上传/已上传材料', '字段填写情况']
  )
  assert.equal(template.overallBullets[0].value, '外籍家庭佣工入境审核')
  assert.equal(template.overallBullets[1].value, '转换雇主')
  assert.match(template.overallBullets[2].value, /^\d+\/\d+$/)
  assert.match(template.overallBullets[3].value, /必填字段\(\d+\)，必填未填写\(\d+\)，未识别\(\d+\)，待复核\(\d+\)/)
})

test('verification template displays suggested value and keeps corrected fields in review', () => {
  const field = {
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
  const [adjudicatedField] = applyFieldAdjudications([field], [])

  const template = buildVerificationTemplate({
    decision: 'REVIEW',
    decisionText: '字段需复核。',
    uploadedFiles: [],
    materials: [],
    fields: [adjudicatedField],
    stats: { required: 1 }
  })

  const contractRow = template.fieldRows.find((row) => row.key === 'contract.dh_contract_no')
  assert.equal(contractRow.status, 'review')
  assert.equal(contractRow.displayValue, 'FH-CON-IDN-2026-0612')
  assert.match(contractRow.note, /建议采用/)
})
