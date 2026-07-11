import test from 'node:test'
import assert from 'node:assert/strict'

import {
  fieldNeedsReview,
  fieldVerificationScore,
  sourceEvidenceBbox
} from './ocrPresentation.js'

test('uses judge verification score instead of recognition confidence', () => {
  assert.equal(fieldVerificationScore({ verificationScore: 90, confidence: 99 }), 90)
  assert.equal(fieldVerificationScore({ verificationScore: null, confidence: 99 }), null)
})

test('uses OCR label bbox for highlighting', () => {
  assert.deepEqual(
    sourceEvidenceBbox({
      labelBbox: [1, 2, 3, 4],
      evidenceBbox: [0, 0, 30, 30],
      bbox: [9, 9, 19, 19]
    }),
    [1, 2, 3, 4]
  )
  assert.deepEqual(sourceEvidenceBbox({ bbox: [9, 9, 19, 19] }), [9, 9, 19, 19])
})

test('requires review when judge has no score or returns review', () => {
  assert.equal(fieldNeedsReview({ verificationScore: null, verificationStatus: 'review' }), true)
  assert.equal(fieldNeedsReview({ verificationScore: 84, verificationStatus: 'review' }), true)
  assert.equal(fieldNeedsReview({ verificationScore: 90, verificationStatus: 'pass' }), false)
})
