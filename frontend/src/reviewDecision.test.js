import test from 'node:test'
import assert from 'node:assert/strict'
import { deriveReviewDecision } from './reviewDecision.js'

test('deriveReviewDecision returns review when only displayed blocking findings need review', () => {
  const result = deriveReviewDecision({
    materials: [
      { shortName: 'ID 988A', blocking: true, status: 'pass', issue: '' },
      { shortName: 'ID 988B', blocking: true, status: 'pass', issue: '' },
      { shortName: 'ID 407', blocking: true, status: 'pass', issue: '' }
    ],
    fields: [
      { label: '佣工英文姓名', blocking: true, status: 'review', issue: '跨文件字段值明显不一致。' },
      { label: '标准雇佣合约编号', blocking: true, status: 'review', issue: '跨文件字段值明显不一致。' },
      { label: '膳食津贴', blocking: true, status: 'review', issue: '未能从已上传材料识别该必填字段。' }
    ]
  })

  assert.equal(result.decision, 'REVIEW')
  assert.match(result.decisionText, /佣工英文姓名|标准雇佣合约编号|膳食津贴/)
})

test('deriveReviewDecision keeps fail when a blocking material is failed', () => {
  const result = deriveReviewDecision({
    materials: [
      { shortName: 'ID 988B', blocking: true, status: 'fail', issue: '缺第 2 页。' }
    ],
    fields: [
      { label: '标准雇佣合约编号', blocking: true, status: 'review', issue: '需人工复核。' }
    ]
  })

  assert.equal(result.decision, 'FAIL')
  assert.match(result.decisionText, /ID 988B/)
})
