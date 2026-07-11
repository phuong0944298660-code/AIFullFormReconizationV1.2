import assert from 'node:assert/strict'
import test from 'node:test'
import { judgeDisplayState, localizedJudgeReason } from './judgePresentation.js'

test('missing value bbox is review rather than unfinished', () => {
  assert.deepEqual(
    judgeDisplayState({ verificationScore: null, verificationStatus: 'not_run', verificationReason: 'value_bbox_missing' }),
    { status: 'review', reasonKey: 'llmValueBboxMissing' }
  )
})

test('non-applicant values do not require a judge', () => {
  assert.deepEqual(
    judgeDisplayState({ verificationScore: null, verificationStatus: 'not_run', verificationReason: 'not_applicant_value' }),
    { status: 'not_required', reasonKey: 'judgeNotRequired' }
  )
})

test('document metadata does not require a judge', () => {
  assert.deepEqual(
    judgeDisplayState({ label: 'Source file', verificationScore: null, verificationStatus: 'not_run' }),
    { status: 'not_required', reasonKey: 'judgeNotRequired' }
  )
  assert.deepEqual(
    judgeDisplayState({ label: 'No applicant input', verificationScore: null, verificationStatus: 'not_run' }),
    { status: 'not_required', reasonKey: 'judgeNotRequired' }
  )
})

test('judge transport and response errors remain review with a call failure reason', () => {
  assert.deepEqual(
    judgeDisplayState({ verificationScore: null, verificationStatus: 'not_run', verificationReason: 'invalid_response' }),
    { status: 'review', reasonKey: 'judgeCallFailed' }
  )
})

test('completed review and fail states retain their own status colors', () => {
  assert.deepEqual(
    judgeDisplayState({ verificationScore: 0, verificationStatus: 'review' }),
    { status: 'review', reasonKey: '' }
  )
  assert.deepEqual(
    judgeDisplayState({ verificationScore: 0, verificationStatus: 'fail' }),
    { status: 'fail', reasonKey: '' }
  )
})

test('uses the selected language from a bilingual judge reason payload', () => {
  const reason = '{"en":"The value matches exactly.","zh-Hant":"填寫值完全一致。"}'
  assert.equal(localizedJudgeReason(reason, 'en'), 'The value matches exactly.')
  assert.equal(localizedJudgeReason(reason, 'zh-Hant'), '填寫值完全一致。')
  assert.equal(localizedJudgeReason(reason, 'zh'), '填寫值完全一致。')
})
