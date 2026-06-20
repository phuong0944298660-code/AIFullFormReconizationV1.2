import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  employmentPeriodsFromFields,
  isEmploymentPeriodAnchorField,
  isEmploymentPeriodField,
  reviewableFields
} from './employmentFields.js'

const fields = [
  {
    key: 'extracted.employer_2_address',
    normalizedValue: 'Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK',
    status: 'pass'
  },
  {
    key: 'extracted.total_duration_years',
    normalizedValue: '6',
    status: 'pass'
  },
  {
    key: 'extracted.name_of_current_employer_if_applicable',
    normalizedValue: 'Mrs. Karen WALKER',
    status: 'pass'
  },
  {
    key: 'extracted.address_of_current_employer_if_applicable',
    normalizedValue: 'Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK',
    status: 'pass'
  },
  {
    key: 'extracted.employer_1_name',
    normalizedValue: 'Mrs. Linda CHEN',
    status: 'pass'
  },
  {
    key: 'extracted.employer_1_period_from',
    normalizedValue: '06/19',
    status: 'pass'
  },
  {
    key: 'extracted.employer_1_period_to',
    normalizedValue: '05/22',
    status: 'pass'
  },
  {
    key: 'extracted.employer_2_name',
    normalizedValue: 'Mrs. Karen WALKER',
    status: 'pass'
  },
  {
    key: 'extracted.employer_2_period_from',
    normalizedValue: '08/22',
    status: 'pass'
  },
  {
    key: 'extracted.employer_2_period_to',
    normalizedValue: '03/26',
    status: 'pass'
  },
  {
    key: 'extracted.total_duration_months',
    normalizedValue: '9',
    status: 'pass'
  },
  {
    key: 'extracted.employer_1_address',
    normalizedValue: 'Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong',
    status: 'pass'
  }
]

test('employment period field detection excludes only indexed experience columns', () => {
  assert.equal(isEmploymentPeriodField('extracted.employer_1_name'), true)
  assert.equal(isEmploymentPeriodField('extracted.employer_2_address'), true)
  assert.equal(isEmploymentPeriodField('extracted.employer_1_period_from'), true)
  assert.equal(isEmploymentPeriodField('extracted.employer_2_period_to'), true)
  assert.equal(isEmploymentPeriodField('extracted.total_duration_years'), false)
  assert.equal(isEmploymentPeriodField('extracted.total_duration_months'), false)
  assert.equal(isEmploymentPeriodField('extracted.address'), false)
})

test('current employer address variants can anchor the merged employment period section', () => {
  assert.equal(isEmploymentPeriodAnchorField('extracted.address_of_current_employer'), true)
  assert.equal(isEmploymentPeriodAnchorField('extracted.address_of_current_employer_if_applicable'), true)
  assert.equal(isEmploymentPeriodAnchorField('extracted.employer_1_address'), false)
  assert.equal(isEmploymentPeriodAnchorField('extracted.address'), false)
})

test('reviewable fields omit period columns but keep total duration fields', () => {
  assert.deepEqual(
    reviewableFields(fields).map((field) => field.key),
    [
      'extracted.total_duration_years',
      'extracted.name_of_current_employer_if_applicable',
      'extracted.address_of_current_employer_if_applicable',
      'extracted.total_duration_months'
    ]
  )
})

test('employment periods are grouped by employer sequence without normalizing across rows', () => {
  const periods = employmentPeriodsFromFields(fields)

  assert.equal(periods.length, 2)
  assert.equal(periods[0].n, 1)
  assert.equal(periods[0].nameField.normalizedValue, 'Mrs. Linda CHEN')
  assert.equal(periods[0].addressField.normalizedValue, 'Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong')
  assert.equal(periods[0].periodFromField.normalizedValue, '06/19')
  assert.equal(periods[0].periodToField.normalizedValue, '05/22')
  assert.equal(periods[1].n, 2)
  assert.equal(periods[1].nameField.normalizedValue, 'Mrs. Karen WALKER')
  assert.equal(periods[1].addressField.normalizedValue, 'Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK')
  assert.equal(periods[1].periodFromField.normalizedValue, '08/22')
  assert.equal(periods[1].periodToField.normalizedValue, '03/26')
})
