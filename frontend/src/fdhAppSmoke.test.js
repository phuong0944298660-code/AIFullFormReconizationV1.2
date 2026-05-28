import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'
import assert from 'node:assert/strict'
import { applicationTypes, buildReviewResult, buildUploadedFiles, scenarios } from './fdhMockData.js'

test('FDH smoke flow covers upload, recognition result, and field filters', () => {
  const applicationTypeId = applicationTypes[0].id
  const scenarioId = 'field_mismatch'
  const uploadedFiles = buildUploadedFiles(scenarioId)
  const result = buildReviewResult(applicationTypeId, scenarioId)

  assert.ok(uploadedFiles.length >= 3)
  assert.equal(result.decision, 'FAIL')
  assert.ok(result.materials.some((material) => material.core && material.status === 'pass'))
  assert.ok(result.fields.some((field) => field.status === 'fail' && field.key === 'helper.name.full_en'))

  const allFields = result.fields
  const issueFields = result.fields.filter((field) => field.status === 'fail')
  const reviewFields = result.fields.filter((field) => field.status === 'review')
  const requiredFields = result.fields.filter((field) => field.required)

  assert.ok(allFields.length > issueFields.length)
  assert.ok(issueFields.length >= 1)
  assert.ok(reviewFields.length === 0)
  assert.ok(requiredFields.length >= issueFields.length)
})

test('FDH mock scenarios exercise pass, fail, and review outcomes', () => {
  const outcomes = new Set(scenarios.map((scenario) => buildReviewResult('entry_visa', scenario.id).decision))

  assert.ok(outcomes.has('PASS'))
  assert.ok(outcomes.has('FAIL'))
  assert.ok(outcomes.has('REVIEW'))
})

test('built frontend assets exist after production build', () => {
  const distIndex = resolve('dist/index.html')
  assert.equal(existsSync(distIndex), true)

  const indexHtml = readFileSync(distIndex, 'utf8')
  const assetMatches = [...indexHtml.matchAll(/(?:src|href)="([^"]+)"/g)]
    .map((match) => match[1])
    .filter((assetPath) => !assetPath.startsWith('data:'))
  assert.ok(assetMatches.length >= 2)

  for (const assetPath of assetMatches) {
    const normalizedPath = assetPath.startsWith('/') ? assetPath.slice(1) : assetPath
    assert.equal(existsSync(resolve('dist', normalizedPath)), true, `${assetPath} should exist in dist`)
  }
})
