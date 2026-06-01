import test from 'node:test'
import assert from 'node:assert/strict'
import { displaySourceValue, sourceValueSegments } from './fieldDiff.js'

test('sourceValueSegments marks differing characters for review and fail fields', () => {
  const field = {
    status: 'review',
    sources: [
      { value: 'C8923745' },
      { value: 'C892374S' }
    ]
  }

  const first = sourceValueSegments(field, field.sources[0])
  const second = sourceValueSegments(field, field.sources[1])

  assert.equal(first.map((segment) => segment.text).join(''), 'C8923745')
  assert.equal(second.map((segment) => segment.text).join(''), 'C892374S')
  assert.deepEqual(first.map((segment) => segment.diff), [false, false, false, false, false, false, false, true])
  assert.deepEqual(second.map((segment) => segment.diff), [false, false, false, false, false, false, false, true])
})

test('sourceValueSegments highlights inserted trailing characters', () => {
  const field = {
    status: 'fail',
    sources: [
      { value: 'SITI NURHALIZA' },
      { value: 'SITI NURHALIZA BINTI' }
    ]
  }

  const second = sourceValueSegments(field, field.sources[1])

  assert.equal(second.slice(0, 'SITI NURHALIZA'.length).some((segment) => segment.diff), false)
  assert.equal(second.slice('SITI NURHALIZA'.length).every((segment) => segment.diff), true)
})

test('sourceValueSegments does not highlight pass fields with normalized raw format differences', () => {
  const field = {
    status: 'pass',
    sources: [
      { value: '27/11/1992' },
      { value: '27 NOV 1992' }
    ]
  }

  assert.equal(sourceValueSegments(field, field.sources[0]).some((segment) => segment.diff), false)
  assert.equal(sourceValueSegments(field, field.sources[1]).some((segment) => segment.diff), false)
})

test('displaySourceValue falls back to snapshot text for blank values', () => {
  assert.equal(displaySourceValue({ value: '', snapshotText: 'blank' }), 'blank')
  assert.equal(displaySourceValue({ value: null, snapshotText: '' }), '未识别')
})
