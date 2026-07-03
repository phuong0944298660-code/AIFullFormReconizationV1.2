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

test('FDH material checklist has English mappings for every mock material id', () => {
  const source = appSource()

  assert.match(source, /testimonial:\s*\{\s*en:\s*\{[^}]*Helper's testimonial/s)
  assert.match(source, /continuousLetter:\s*\{\s*en:\s*\{[^}]*Employer's confirmation letter for continued employment/s)
  assert.match(source, /id407:\s*\{\s*en:\s*\{[^}]*Online renewal applications still require the original contract/s)
  assert.match(source, /testimonial:\s*\{\s*en:\s*\{[^}]*templateId:\s*'Letter'[^}]*expectedPages:\s*'As evidenced'/s)
  assert.match(source, /continuousLetter:\s*\{\s*en:\s*\{[^}]*templateId:\s*'Letter'[^}]*expectedPages:\s*'As evidenced'/s)
  assert.doesNotMatch(source, /referenceLetter:\s*\{\s*en:\s*\{[^}]*Helper's reference letter/s)
  assert.doesNotMatch(source, /continueEmploymentLetter:\s*\{\s*en:\s*\{[^}]*Employer's confirmation letter for continued employment/s)
})

test('FDH material status and scope text are localized for English result views', () => {
  const source = appSource()

  assert.match(source, /function localizedMaterialStatusText\(material\)/)
  assert.match(source, /Uploaded and recognised/)
  assert.match(source, /Conditionally required; not uploaded/)
  assert.match(source, /function localizedMaterialScopeText\(material\)/)
  assert.match(source, /t\('officialChecklistNonBlocking'\)/)
})

test('Traditional Chinese conversion covers visible FDH checklist wording', () => {
  const source = appSource()

  assert.match(source, /签:\s*'簽'/)
  assert.match(source, /证:\s*'證'/)
  assert.match(source, /请:\s*'請'/)
  assert.match(source, /荐:\s*'薦'/)
  assert.match(source, /书:\s*'書'/)
  assert.match(source, /继:\s*'繼'/)
  assert.match(source, /续:\s*'續'/)
  assert.match(source, /页:\s*'頁'/)
  assert.match(source, /银:\s*'銀'/)
  assert.match(source, /饷:\s*'餉'/)
  assert.match(source, /\['标签', '標籤'\]/)
})
