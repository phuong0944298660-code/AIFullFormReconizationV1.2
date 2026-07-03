import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'
import assert from 'node:assert/strict'

const appSource = () => readFileSync(resolve('src/App.vue'), 'utf8')

test('global language defaults to English and does not participate in result-reset watcher', () => {
  const source = appSource()

  assert.match(source, /const currentLanguage = ref\('en'\)/)

  const resetWatcher = source.match(/watch\(\[([^\]]+)\]/)
  assert.ok(resetWatcher, 'expected App.vue to keep the existing reset watcher')
  assert.doesNotMatch(resetWatcher[1], /currentLanguage/)
})

test('English copy uses Hong Kong Immigration Department terminology', () => {
  const source = appSource()

  assert.match(source, /Immigration Department/)
  assert.match(source, /Foreign Domestic Helper/)
  assert.match(source, /Application for Extension of Stay/)
  assert.match(source, /Admission Scheme for Mainland Talents and Professionals/)
  assert.match(source, /Payment Status/)
})

test('backend recognition progress messages are localized before rendering', () => {
  const source = appSource()

  assert.match(source, /const progressMessage = computed/)
  assert.match(source, /localizedJobStatusMessage\(jobStatus\.value\?\.message\)/)
  assert.match(source, /正在渲染并识别材料类型/)
  assert.match(source, /Rendering and recognising document type/)
  assert.doesNotMatch(source, /jobStatus\?\.message \|\| t\('progressFallback'\)/)
})
