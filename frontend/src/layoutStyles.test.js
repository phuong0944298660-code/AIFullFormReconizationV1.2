import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import assert from 'node:assert/strict'

const css = readFileSync(new URL('./styles.css', import.meta.url), 'utf8')
const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')
const viteConfig = readFileSync(new URL('../vite.config.js', import.meta.url), 'utf8')
const packageJson = readFileSync(new URL('../package.json', import.meta.url), 'utf8')
const startLocal = readFileSync(new URL('../../start-local.ps1', import.meta.url), 'utf8')

test('FDH result page keeps review sidebar and field evidence areas distinct', () => {
  assert.match(css, /\.review-workspace\s*\{[^}]*grid-template-columns:\s*390px minmax\(0, 1fr\)/s)
  assert.match(css, /\.review-sidebar,[\s\S]*?\.review-main\s*\{[^}]*display:\s*grid/s)
  assert.match(css, /\.field-card-body\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1\.15fr\) minmax\(300px, 0\.85fr\)/s)
  assert.doesNotMatch(css, /body\s*\{[^}]*overflow:\s*hidden/s)
})

test('standardized field cards render source evidence and normalized values', () => {
  assert.match(app, /v-for="source in field\.sources"/)
  assert.match(app, /source\.documentName/)
  assert.match(app, /source\.section/)
  assert.match(app, /source\.fieldName/)
  assert.match(app, /field\.normalizedValue/)
  assert.match(css, /\.snapshot-card\s*\{/)
  assert.match(css, /\.evidence-card\s*\{/)
})

test('upload page supports application type selection and mock scenario switching', () => {
  assert.match(app, /applicationTypes/)
  assert.match(app, /selectedApplicationTypeId/)
  assert.match(app, /scenarios/)
  assert.match(app, /selectedScenarioId/)
  assert.match(app, /function simulateUpload/)
  assert.match(app, /function startRecognition/)
})

test('review output prioritizes overall decision, material completeness, and field findings', () => {
  assert.match(app, /整体结论/)
  assert.match(app, /材料完整性/)
  assert.match(app, /逐条结论与出处/)
  assert.match(app, /标准化字段核验/)
  assert.match(app, /材料 1-3 纳入最终判定/)
  assert.match(app, /材料 4-12 只展示是否上传/)
})

test('field filters expose all, issue, review, and required views', () => {
  assert.match(app, /fieldFilter === 'all'/)
  assert.match(app, /fieldFilter === 'issues'/)
  assert.match(app, /fieldFilter === 'review'/)
  assert.match(app, /fieldFilter === 'required'/)
})

test('frontend dev server defaults to the new copied-project port', () => {
  assert.match(viteConfig, /port:\s*Number\(process\.env\.FRONTEND_PORT\s*\|\|\s*5197\)/)
  assert.match(packageJson, /node \.\/node_modules\/vite\/bin\/vite\.js --host 127\.0\.0\.1/)
  assert.doesNotMatch(packageJson, /--port 5186/)
  assert.match(viteConfig, /hmr:\s*false/)
})

test('FDH backend polling does not timeout before the backend LLM request budget', () => {
  assert.match(app, /const FDH_JOB_POLL_INTERVAL_MS = 1000/)
  assert.match(app, /const FDH_JOB_POLL_LIMIT = 1500/)
  assert.doesNotMatch(app, /attempt < 240/)
  assert.match(app, /识别任务仍在处理中/)
})

test('local startup uses stable port ownership and conservative FDH LLM defaults', () => {
  assert.match(startLocal, /function Get-PortOwnerProcessIds/)
  assert.match(startLocal, /netstat -ano/)
  assert.match(startLocal, /\$env:LLM_PAGE_CONCURRENCY = "2"/)
  assert.match(startLocal, /\$env:FDH_REVIEW_FILE_CONCURRENCY = "2"/)
  assert.match(startLocal, /\$env:OCR_PAGE_MAX_IMAGE_LONG_SIDE = "1800"/)
  assert.match(startLocal, /\$env:LLM_TIMEOUT_SECONDS = "120"/)
})
