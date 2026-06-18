import test from 'node:test'
import assert from 'node:assert/strict'
import { verificationNotice } from './verificationNotice.js'

test('format fallback does not show a client-facing technical notice', () => {
  const message = verificationNotice({ status: 'format_fallback', llmEnabled: true })

  assert.equal(message, '')
})

test('transport fallback does not show a client-facing technical notice', () => {
  const message = verificationNotice({ status: 'error_fallback: timeout', llmEnabled: true })

  assert.equal(message, '')
})
