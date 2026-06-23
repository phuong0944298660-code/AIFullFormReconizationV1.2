<script setup>
import { computed, ref, watch } from 'vue'
import {
  applicationTypes as fdhApplicationTypes,
  buildReviewResult as buildFdhReviewResult,
  buildUploadedFiles as buildFdhUploadedFiles,
  scenarios as fdhScenarios
} from './fdhMockData.js'
import {
  applicationTypes as studentApplicationTypes,
  buildReviewResult as buildStudentReviewResult,
  buildUploadedFiles as buildStudentUploadedFiles,
  scenarios as studentScenarios
} from './studentIangMockData.js'
import { applyFieldAdjudications, localFieldAdjudications } from './fieldAdjudication.js'
import { sourceValueSegments } from './fieldDiff.js'
import { employmentPeriodsFromFields, isEmploymentPeriodAnchorField, reviewableFields } from './employmentFields.js'
import { deriveFieldStats, deriveReviewDecision } from './reviewDecision.js'
import {
  buildVerificationTemplate,
  TEMPLATE_STATUS_LEGEND
} from './verificationTemplate.js'
import { verificationNotice } from './verificationNotice.js'

const demoModes = [
  {
    id: 'student_iang',
    label: 'IANG 应届毕业生在港首次申请',
    shortLabel: '学生 / IANG',
    description: '默认演示学生出入境 IANG 应届毕业生在港首次申请材料识别。'
  },
  {
    id: 'fdh',
    label: '家庭佣工',
    shortLabel: '家庭佣工',
    description: '保留当前外籍家庭佣工材料核验流程。'
  }
]

const selectedDemoModeId = ref('student_iang')
const selectedApplicationTypeId = ref(studentApplicationTypes[0].id)
const selectedScenarioId = ref(studentScenarios[0].id)
const uploadedFiles = ref([])
const uploadedFileObjects = ref([])
const reviewResult = ref(null)
const resultView = ref('recognition')
const verificationConclusion = ref(null)
const verificationLoading = ref(false)
const verificationError = ref('')
const processing = ref(false)
const fieldFilter = ref('all')
const fileInput = ref(null)
const jobStatus = ref(null)
const apiError = ref('')
const dragActive = ref(false)
let uploadSequence = 0
let uploadBatchSequence = 0
let verificationSequence = 0
const FDH_JOB_POLL_INTERVAL_MS = 1000
const FDH_JOB_POLL_LIMIT = 1500
const REVIEW_JOB_START_TIMEOUT_MS = 60000
const demoFlowDescription = '本Demo主要演示「申请材料上传→文档解析识别→字段结构化提取与归一→跨档智能校验→自动生成审核结论」端到端全流程'

const selectedDemoMode = computed(() => {
  return demoModes.find((item) => item.id === selectedDemoModeId.value) || demoModes[0]
})

const isFdhMode = computed(() => selectedDemoModeId.value === 'fdh')

const activeApplicationTypes = computed(() => {
  return isFdhMode.value ? fdhApplicationTypes : studentApplicationTypes
})

const activeScenarios = computed(() => {
  return isFdhMode.value ? fdhScenarios : studentScenarios
})

const activeWorkflowLabel = computed(() => {
  return isFdhMode.value ? '外籍家庭佣工入境审核' : 'IANG 应届毕业生在港首次申请'
})

const uploadHelperText = computed(() => {
  return isFdhMode.value
    ? 'PDF / PNG / JPG · 支持 ID 988A、ID 988B、ID 407 与其他证明材料'
    : 'PDF / PNG / JPG · 支持 ID 990A、毕业证明、港澳通行证 / 护照 / HKID、付款截图'
})

const checklistDescription = computed(() => {
  return isFdhMode.value
    ? '材料 1-3 纳入最终判定；材料 4-12 只展示是否上传，不阻断本 demo 结论。'
    : '展示 IANG 应届毕业生在港首次申请官方材料清单；标记“Demo审批”的材料参与当前结论。'
})

const selectedApplicationType = computed(() => {
  return activeApplicationTypes.value.find((item) => item.id === selectedApplicationTypeId.value) || activeApplicationTypes.value[0]
})

const selectedScenario = computed(() => {
  return activeScenarios.value.find((item) => item.id === selectedScenarioId.value) || activeScenarios.value[0]
})

const checklistPreview = computed(() => {
  return buildActiveReviewResult().materials
})

function buildActiveUploadedFiles() {
  return isFdhMode.value
    ? buildFdhUploadedFiles(selectedScenarioId.value)
    : buildStudentUploadedFiles(selectedScenarioId.value)
}

function buildActiveReviewResult() {
  return isFdhMode.value
    ? buildFdhReviewResult(selectedApplicationTypeId.value, selectedScenarioId.value)
    : buildStudentReviewResult(selectedApplicationTypeId.value, selectedScenarioId.value)
}

const fieldAdjudications = computed(() => {
  const local = localFieldAdjudications(reviewResult.value || {})
  const merged = new Map(local.map((item) => [item.key, item]))
  for (const item of verificationConclusion.value?.fieldAdjudications || []) {
    if (item?.key) merged.set(item.key, item)
  }
  return Array.from(merged.values())
})

const fieldRows = computed(() => {
  return applyFieldAdjudications(reviewResult.value?.fields || [], fieldAdjudications.value)
})

const reviewableFieldRows = computed(() => reviewableFields(fieldRows.value))

const displayReviewResult = computed(() => {
  if (!reviewResult.value) return null
  return withDerivedDecision(reviewResult.value, reviewableFieldRows.value)
})

const displayFieldStats = computed(() => {
  return deriveFieldStats(reviewableFieldRows.value)
})

const filteredFields = computed(() => {
  const rows = reviewableFieldRows.value
  if (fieldFilter.value === 'issues') return rows.filter((field) => field.status === 'fail')
  if (fieldFilter.value === 'review') return rows.filter((field) => field.status === 'review')
  if (fieldFilter.value === 'required') return rows.filter((field) => field.required)
  return rows
})

// 匹配所有工作经验字段（employer_N_*，任意后缀），统一进段、不再单独罗列；
// 段内按 key 含 name/address/from/to 分类。不同 N 不同 key，不会归一。
const filteredAllFields = computed(() => {
  const rows = fieldRows.value
  if (fieldFilter.value === 'issues') return rows.filter((field) => field.status === 'fail')
  if (fieldFilter.value === 'review') return rows.filter((field) => field.status === 'review')
  if (fieldFilter.value === 'required') return rows.filter((field) => field.required)
  return rows
})

const nonEmploymentFields = computed(() => filteredFields.value)

const employmentPeriods = computed(() => employmentPeriodsFromFields(filteredAllFields.value))

function employmentValue(field) {
  if (!field) return '未识别'
  return field.suggestedValue || field.normalizedValue || '未识别'
}

const blockingFindings = computed(() => {
  if (!reviewResult.value) return []
  const materialFindings = reviewResult.value.materials
    .filter((item) => item.blocking && (item.status === 'fail' || item.status === 'review'))
    .map((item) => ({
      id: `material:${item.id}`,
      status: item.status,
      title: item.status === 'fail' ? `缺少核心材料：${item.shortName}` : `材料需复核：${item.shortName}`,
      text: item.statusText,
      source: `${item.shortName} · ${item.templateId}`
    }))

  const fieldFindings = reviewableFieldRows.value
    .filter((field) => field.blocking && (field.status === 'fail' || field.status === 'review'))
    .map((field) => ({
      id: `field:${field.key}`,
      status: field.status,
      title: field.label,
      text: field.issue || field.rule,
      source: field.sources.length
        ? field.sources.map((source) => `${source.documentName} · ${source.section} · ${source.fieldName}`).join('；')
        : '未取得可用字段证据'
    }))

  return [...materialFindings, ...fieldFindings]
})

const nonBlockingMaterialHints = computed(() => {
  if (!reviewResult.value) return []
  return reviewResult.value.materials.filter((item) => !item.blocking && item.status === 'warn')
})

const reviewJsonPayload = computed(() => {
  const result = displayReviewResult.value
  if (!result) return {}
  return {
    applicationType: {
      id: result.applicationTypeId,
      label: selectedApplicationType.value.label
    },
    decision: result.decision,
    decisionText: result.decisionText,
    generatedAt: result.generatedAt,
    stats: result.stats,
    documentFieldGroups: result.documentFieldGroups || [],
    materialCompleteness: result.materials.map((material) => ({
      no: material.no,
      id: material.id,
      name: material.name,
      shortName: material.shortName,
      templateId: material.templateId,
      expectedPages: material.expectedPages,
      applicable: material.applicable,
      uploaded: material.uploaded,
      core: material.core,
      blocking: material.blocking,
      status: material.status,
      statusText: material.statusText,
      scopeText: material.scopeText,
      uploadedFilenames: material.uploadedFilenames || [],
      issue: material.issue || ''
    })),
    fieldAdjudications: fieldAdjudications.value,
    fieldRecognitionAndAudit: fieldRows.value.map((field) => ({
      key: field.key,
      category: field.category,
      label: field.label,
      required: field.required,
      normalizedValue: field.rawNormalizedValue || field.normalizedValue,
      suggestedValue: field.suggestedValue || '',
      correctionApplied: Boolean(field.correctionApplied),
      status: field.status,
      issue: field.suggestionReason || field.issue || '',
      blocking: field.blocking,
      rule: field.rule,
      sources: field.sources.map((source) => ({
        documentName: source.documentName,
        filename: source.filename,
        section: source.section,
        fieldName: source.fieldName,
        value: source.value,
        confidence: source.confidence,
        hasSnapshot: Boolean(source.snapshotDataUrl)
      }))
    }))
  }
})

const reviewJsonPreview = computed(() => JSON.stringify(reviewJsonPayload.value, null, 2))

const verificationView = computed(() => {
  return parseVerificationConclusion(verificationConclusion.value?.text || '')
})

const verificationTemplate = computed(() => {
  const result = displayReviewResult.value
  if (!result) return null
  return buildVerificationTemplate({
    ...result,
    fields: reviewableFieldRows.value
  }, {
    applicationTypeLabel: selectedApplicationType.value.label,
    workflowLabel: activeWorkflowLabel.value
  })
})

const templateStatusLegend = TEMPLATE_STATUS_LEGEND

watch([selectedDemoModeId, selectedApplicationTypeId, selectedScenarioId], () => {
  fieldFilter.value = 'all'
  apiError.value = ''
  verificationSequence += 1
  resultView.value = 'recognition'
  verificationConclusion.value = null
  verificationError.value = ''
  if (uploadedFiles.value.length && !uploadedFileObjects.value.length) {
    uploadedFiles.value = buildActiveUploadedFiles()
  }
  if (reviewResult.value?.scenarioId) {
    reviewResult.value = buildActiveReviewResult()
  }
})

function selectDemoMode(modeId) {
  if (processing.value || selectedDemoModeId.value === modeId) return
  selectedDemoModeId.value = modeId
  if (modeId === 'fdh') {
    selectedApplicationTypeId.value = fdhApplicationTypes[0].id
    selectedScenarioId.value = 'missing_core'
  } else {
    selectedApplicationTypeId.value = studentApplicationTypes[0].id
    selectedScenarioId.value = studentScenarios[0].id
  }
  resetDemo()
}

function openFilePicker() {
  fileInput.value?.click()
}

function handleFileSelection(event) {
  const files = Array.from(event.target.files || [])
  handleSelectedFiles(files)
}

function handleFileDrop(event) {
  dragActive.value = false
  if (processing.value) return
  const files = Array.from(event.dataTransfer?.files || [])
  handleSelectedFiles(files)
}

function handleSelectedFiles(files) {
  if (!files.length) return
  const batchNo = uploadBatchSequence + 1
  uploadBatchSequence = batchNo
  const entries = files.map((file) => ({
    uploadId: `upload-${Date.now()}-${uploadSequence += 1}`,
    batchNo,
    file
  }))
  uploadedFileObjects.value = [...uploadedFileObjects.value, ...entries]
  uploadedFiles.value = [
    ...uploadedFiles.value,
    ...entries.map((entry) => ({
      uploadId: entry.uploadId,
      batchNo: entry.batchNo,
      materialId: 'pending',
      documentName: '待识别',
      filename: entry.file.name,
      pages: '待识别',
      footerId: '等待开始识别',
      size: entry.file.size
    }))
  ]
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
  verificationSequence += 1
  resultView.value = 'recognition'
  verificationConclusion.value = null
  verificationError.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function handleDragEnter() {
  if (!processing.value) {
    dragActive.value = true
  }
}

function handleDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) {
    dragActive.value = false
  }
}

function simulateUpload() {
  uploadedFileObjects.value = []
  uploadBatchSequence = 0
  uploadedFiles.value = buildActiveUploadedFiles()
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
  verificationSequence += 1
}

async function startRecognition() {
  if (processing.value) return
  if (uploadedFileObjects.value.length) {
    await startBackendRecognition()
    return
  }
  if (!uploadedFiles.value.length) simulateUpload()
  processing.value = true
  reviewResult.value = null
  window.setTimeout(() => {
    const result = buildActiveReviewResult()
    reviewResult.value = result
    resultView.value = 'recognition'
    verificationConclusion.value = null
    verificationError.value = ''
    startVerificationConclusion(result)
    processing.value = false
  }, 700)
}

async function startBackendRecognition() {
  processing.value = true
  reviewResult.value = null
  apiError.value = ''
  jobStatus.value = {
    status: 'uploading',
    totalFiles: uploadedFileObjects.value.length,
    processedFiles: 0,
    progress: 1,
    activeFilename: '',
    message: '正在上传材料并创建识别任务。',
    error: '',
    result: null
  }
  try {
    const form = new FormData()
    for (const entry of uploadedFileObjects.value) {
      const file = entry.file || entry
      form.append('files', file, file.name)
    }
    form.append('applicationTypeId', selectedApplicationTypeId.value)

    const started = await requestJson('/api/fdh/review/jobs', {
      method: 'POST',
      body: form,
      timeoutMs: REVIEW_JOB_START_TIMEOUT_MS
    })
    jobStatus.value = mergeJobStatus(started)
    const completed = await pollFdhJob(started.jobId)
    jobStatus.value = completed
    if (completed.status !== 'completed') {
      throw new Error(completed.error || completed.message || '识别任务未完成。')
    }
    const result = completed.result
    reviewResult.value = result
    resultView.value = 'recognition'
    verificationConclusion.value = null
    verificationError.value = ''
    uploadedFiles.value = result?.uploadedFiles || uploadedFiles.value
    startVerificationConclusion(result)
  } catch (error) {
    apiError.value = error?.message || '后端识别失败。'
  } finally {
    processing.value = false
  }
}

function removeUploadedFile(uploadId) {
  if (processing.value) return
  uploadedFileObjects.value = uploadedFileObjects.value.filter((entry) => entry.uploadId !== uploadId)
  uploadedFiles.value = uploadedFiles.value.filter((file) => file.uploadId !== uploadId)
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
  verificationSequence += 1
  resultView.value = 'recognition'
  verificationConclusion.value = null
  verificationError.value = ''
}

function clearUploadedFiles() {
  if (processing.value) return
  uploadedFileObjects.value = []
  uploadedFiles.value = []
  uploadBatchSequence = 0
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
  verificationSequence += 1
  resultView.value = 'recognition'
  verificationConclusion.value = null
  verificationError.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function fileSizeLabel(size) {
  if (!Number.isFinite(size) || size <= 0) return ''
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${Math.round(size / 1024)} KB`
  return `${size} B`
}

function uploadedFileProgressLabel() {
  const progress = Number(jobStatus.value?.progress)
  if (processing.value && Number.isFinite(progress)) {
    return `${Math.max(0, Math.min(100, Math.round(progress)))}%`
  }
  return '待识别'
}

async function pollFdhJob(jobId) {
  let latest = jobStatus.value
  for (let attempt = 0; attempt < FDH_JOB_POLL_LIMIT; attempt += 1) {
    await delay(FDH_JOB_POLL_INTERVAL_MS)
    latest = await requestJson(`/api/fdh/review/jobs/${jobId}`)
    jobStatus.value = mergeJobStatus(latest)
    latest = jobStatus.value
    if (['completed', 'failed', 'canceled'].includes(latest.status)) {
      return latest
    }
  }
  throw new Error('识别任务仍在处理中，请稍后刷新任务状态或检查后端日志。')
}

function mergeJobStatus(nextStatus) {
  if (!nextStatus) return nextStatus
  const currentProgress = Number(jobStatus.value?.progress)
  const nextProgress = Number(nextStatus.progress)
  if (!Number.isFinite(currentProgress) || !Number.isFinite(nextProgress)) {
    return nextStatus
  }
  if (nextProgress >= currentProgress || ['completed', 'failed', 'canceled'].includes(nextStatus.status)) {
    return nextStatus
  }
  return {
    ...nextStatus,
    progress: currentProgress
  }
}

async function requestJson(url, options = {}) {
  const { timeoutMs, signal, ...fetchOptions } = options
  let timeoutId = null
  if (timeoutMs && !signal) {
    const controller = new AbortController()
    fetchOptions.signal = controller.signal
    timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)
  } else if (signal) {
    fetchOptions.signal = signal
  }
  try {
    const response = await fetch(url, fetchOptions)
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `HTTP ${response.status}`)
    }
    return response.json()
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error('请求超时，请检查后端服务后重试。')
    }
    throw error
  } finally {
    if (timeoutId) window.clearTimeout(timeoutId)
  }
}

function delay(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function resetDemo() {
  uploadedFiles.value = []
  uploadedFileObjects.value = []
  uploadBatchSequence = 0
  reviewResult.value = null
  processing.value = false
  fieldFilter.value = 'all'
  verificationSequence += 1
  resultView.value = 'recognition'
  verificationConclusion.value = null
  verificationLoading.value = false
  verificationError.value = ''
  jobStatus.value = null
  apiError.value = ''
  dragActive.value = false
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function statusLabel(status) {
  return {
    pass: 'PASS',
    fail: 'FAIL',
    review: 'REVIEW',
    warn: 'WARN',
    muted: 'N/A'
  }[status] || status
}

function templateStatusIconPath(status) {
  return {
    pass: 'M20 6 9 17l-5-5',
    unrecognized: 'M9.2 9a3 3 0 1 1 4.9 2.3c-.9.6-1.6 1.2-1.6 2.7 M12 17.8h.01',
    required_missing: 'M12 7v6 M12 17h.01 M10.3 4.5 3.3 17a2 2 0 0 0 1.7 3h14a2 2 0 0 0 1.7-3l-7-12.5a2 2 0 0 0-3.4 0Z',
    review: 'M12 6v6l4 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z'
  }[status] || 'M12 5v7 M12 17h.01'
}

function templateSourceValue(source) {
  return source?.value || source?.snapshotText || ''
}

function templateSourceConfidence(source) {
  const confidence = Number(source?.confidence)
  if (!Number.isFinite(confidence)) return '-'
  return `${Math.max(0, Math.min(100, Math.round(confidence)))}%`
}

function decisionLabel(decision) {
  return {
    PASS: '允许通过',
    REVIEW: '需人工复核',
    FAIL: '不允许通过'
  }[decision] || decision
}

function materialRequirementLabel(material) {
  if (material.requirementLabel) return material.requirementLabel
  if (!material.applicable) return '不适用'
  if (material.core) return '核心必交'
  if (material.conditional) return '条件应交'
  return '官方应交'
}

async function openVerificationPage() {
  if (!reviewResult.value) return
  resultView.value = 'verification'
  startVerificationConclusion(reviewResult.value)
}

async function startVerificationConclusion(result) {
  if (!result || verificationLoading.value) return
  if (verificationConclusion.value) return

  const sequence = verificationSequence + 1
  verificationSequence = sequence
  verificationLoading.value = true
  verificationError.value = ''
  if (!isFdhMode.value) {
    verificationConclusion.value = {
      llmEnabled: false,
      status: 'student_demo_frontend',
      model: '',
      text: localVerificationConclusion(withLocalFieldAdjudications(result))
    }
    verificationLoading.value = false
    return
  }
  try {
    const conclusion = await requestJson('/api/fdh/review/conclusion', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reviewResultForConclusion(result))
    })
    if (verificationSequence === sequence) {
      verificationConclusion.value = conclusion
      verificationError.value = verificationNotice(conclusion)
    }
  } catch (error) {
    if (verificationSequence === sequence) {
      verificationError.value = ''
      verificationConclusion.value = {
        llmEnabled: false,
        status: 'frontend_fallback',
        model: '',
        text: localVerificationConclusion(withLocalFieldAdjudications(result))
      }
    }
  } finally {
    if (verificationSequence === sequence) {
      verificationLoading.value = false
    }
  }
}

function reviewResultForConclusion(result) {
  const adjusted = withLocalFieldAdjudications(result)
  return {
    ...adjusted,
    fields: reviewableFields(adjusted.fields).map((field) => ({
      ...field,
      sources: field.sources.map((source) => ({
        ...source,
        snapshotDataUrl: ''
      }))
    }))
  }
}

function withLocalFieldAdjudications(result) {
  if (!result) return result
  const fields = applyFieldAdjudications(result.fields || [], localFieldAdjudications(result))
  return withDerivedDecision(result, reviewableFields(fields))
}

function withDerivedDecision(result, fields) {
  const normalizedFields = fields || result.fields || []
  const decision = deriveReviewDecision({
    materials: result.materials || [],
    fields: normalizedFields
  })
  return {
    ...result,
    fields: normalizedFields,
    decision: decision.decision,
    decisionText: decision.decisionText || result.decisionText || '',
    stats: deriveFieldStats(normalizedFields)
  }
}

function localVerificationConclusion(result) {
  const lines = [
    `整体结论：${result.decision} - ${result.decisionText}`,
    '材料识别结果：'
  ]
  result.materials
    .filter((material) => material.applicable)
    .forEach((material) => {
      lines.push(`- ${material.shortName}：${material.statusText}；${material.blocking ? '影响最终通过' : '不影响最终通过'}；出处：${material.templateId || material.shortName}`)
    })
  lines.push('字段识别结果：')
  result.fields.forEach((field) => {
    const statusText = field.status === 'pass'
      ? 'PASS'
      : field.status === 'review'
        ? '需人工审核'
        : field.issue?.includes('不一致')
          ? '不通过，跨文件字段不一致，需人工审核'
          : '不通过'
    const sources = field.sources.length
      ? field.sources.map((source) => `${source.documentName} / ${source.section} / ${source.fieldName}`).join('；')
      : '未取得可用字段证据'
    lines.push(`- ${field.label}：${field.normalizedValue || '未识别'}；${statusText}${field.issue ? `；${field.issue}` : ''}；出处：${sources}`)
  })
  return lines.join('\n')
}

function parseVerificationConclusion(text) {
  const view = {
    overall: '',
    materials: [],
    fields: [],
    other: []
  }
  let section = 'other'
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) continue
    if (line.startsWith('整体结论：')) {
      view.overall = line.replace(/^整体结论：/, '')
      section = 'other'
      continue
    }
    if (line.startsWith('材料识别结果')) {
      section = 'materials'
      continue
    }
    if (line.startsWith('字段识别结果')) {
      section = 'fields'
      continue
    }
    const item = {
      text: line.replace(/^[-•]\s*/, ''),
      status: verificationLineStatus(line)
    }
    if (section === 'materials') {
      view.materials.push(item)
    } else if (section === 'fields') {
      view.fields.push(item)
    } else {
      view.other.push(item)
    }
  }
  return view
}

function verificationLineStatus(line) {
  if (/不通过|FAIL|缺|未识别|不一致/.test(line)) return 'fail'
  if (/需人工审核|人工复核|REVIEW|低置信|无法确认/.test(line)) return 'review'
  return 'pass'
}
</script>

<template>
  <main class="fdh-app">
    <header class="app-header">
      <div class="brand-block">
        <p class="eyebrow">Immigration Document Review Demo</p>
        <h1>香港出入境申请材料识别与核验Demo</h1>
        <p class="header-copy">
          {{ demoFlowDescription }}
        </p>
      </div>

      <div class="workflow-tabs" role="tablist" aria-label="申请场景切换">
        <button
          v-for="mode in demoModes"
          :key="mode.id"
          type="button"
          role="tab"
          :aria-selected="selectedDemoModeId === mode.id"
          :class="{ active: selectedDemoModeId === mode.id }"
          :disabled="processing"
          @click="selectDemoMode(mode.id)"
        >
          <strong>{{ mode.label }}</strong>
          <span>{{ mode.description }}</span>
        </button>
      </div>

    </header>

    <section v-if="!reviewResult" class="upload-stage">
      <div class="review-upload-panel">
        <section class="case-section" aria-labelledby="case-title">
          <div class="section-heading">
            <div>
              <h2 id="case-title">{{ isFdhMode ? '选择申请类别' : '当前学生出入境场景' }}</h2>
              <p>{{ isFdhMode ? '用户只能选择所属类别；材料清单中的勾选状态不可交互。' : '默认展示 IANG 应届毕业生在港首次申请，材料清单标明官方要求和 Demo 审批范围。' }}</p>
            </div>
            <span class="selected-case">{{ selectedApplicationType.checklistKey }}</span>
          </div>

          <div class="case-grid" :class="{ single: !isFdhMode }">
            <button
              v-for="applicationType in activeApplicationTypes"
              :key="applicationType.id"
              type="button"
              class="case-card"
              :class="{ active: selectedApplicationTypeId === applicationType.id }"
              :disabled="processing"
              @click="selectedApplicationTypeId = applicationType.id"
            >
              <strong>{{ applicationType.label }}</strong>
              <span>{{ applicationType.description }}</span>
            </button>
          </div>
        </section>

        <section class="checklist-section" aria-labelledby="checklist-title">
          <div class="section-heading">
            <div>
              <h2 id="checklist-title">{{ isFdhMode ? '该类别官方材料清单' : 'IANG 应届毕业生在港首次申请材料清单' }}</h2>
              <p>{{ checklistDescription }}</p>
            </div>
          </div>

          <div class="material-checklist">
            <article
              v-for="material in checklistPreview"
              :key="material.id"
              class="material-row"
              :class="[`status-${material.status}`, { core: material.core }]"
            >
              <span
                class="material-readonly-marker"
                :class="{ applicable: material.applicable }"
                aria-hidden="true"
              ></span>
              <div class="material-main">
                <strong>{{ material.no }}. {{ material.name }}</strong>
                <span>{{ material.shortName }} · {{ material.templateId }} · {{ material.expectedPages }}{{ material.note ? ` · ${material.note}` : '' }}</span>
                <small v-if="!isFdhMode && material.scopeText" class="material-scope-note">{{ material.scopeText }}</small>
              </div>
              <span class="requirement-badge" :class="{ core: material.core }">
                {{ materialRequirementLabel(material) }}
              </span>
            </article>
          </div>
        </section>

        <section class="upload-section" aria-labelledby="upload-title">
          <div class="section-heading">
            <div>
              <h2 id="upload-title">上传申请材料包</h2>
              <p>{{ isFdhMode ? '家庭佣工流程保持现有真实上传识别能力；未选择文件时使用内置演示数据。' : '上传真实学生材料时走后端识别；未选择文件时使用内置演示数据。' }}</p>
            </div>
          </div>

          <div class="upload-grid">
            <button
              class="dropzone"
              type="button"
              :class="{ active: dragActive }"
              :disabled="processing"
              @click="openFilePicker"
              @drop.prevent.stop="handleFileDrop"
              @dragover.prevent.stop="handleDragEnter"
              @dragenter.prevent.stop="handleDragEnter"
              @dragleave.prevent.stop="handleDragLeave"
            >
              <span class="upload-glyph" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M12 16V4" />
                  <path d="m7 9 5-5 5 5" />
                  <path d="M5 20h14" />
                </svg>
              </span>
              <strong>选择并上传多份申请材料</strong>
              <small>{{ uploadHelperText }}</small>
            </button>
            <input
              ref="fileInput"
              class="file-input"
              type="file"
              multiple
              accept=".pdf,.png,.jpg,.jpeg,.webp,.bmp,application/pdf,image/*"
              @change="handleFileSelection"
            >

            <div class="uploaded-panel">
              <div class="uploaded-header">
                <div>
                  <strong>已选择材料</strong>
                  <span>{{ uploadedFiles.length }} 份{{ uploadedFiles.length > 3 ? ' · 列表可滚动' : '' }}</span>
                </div>
                <button
                  v-if="uploadedFiles.length"
                  class="clear-files-button"
                  type="button"
                  :disabled="processing"
                  @click="clearUploadedFiles"
                >
                  清空全部
                </button>
              </div>

              <div v-if="uploadedFiles.length" class="uploaded-list" :class="{ scrollable: uploadedFiles.length > 6 }">
                <article
                  v-for="file in uploadedFiles"
                  :key="file.uploadId || `${file.filename}:${file.materialId}`"
                  class="uploaded-file"
                >
                  <button
                    v-if="file.uploadId"
                    class="remove-file-icon"
                    type="button"
                    :disabled="processing"
                    :aria-label="`删除 ${file.filename}`"
                    @click="removeUploadedFile(file.uploadId)"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M18 6 6 18" />
                      <path d="m6 6 12 12" />
                    </svg>
                  </button>
                  <div>
                    <strong>{{ file.filename }}</strong>
                    <span>
                      {{ fileSizeLabel(file.size) || '大小未知' }} · {{ uploadedFileProgressLabel() }}
                    </span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-panel">尚未选择材料。可上传真实文件；如直接开始识别，将使用内置演示数据。</div>
            </div>
          </div>

          <div v-if="processing" class="progress-box" aria-live="polite">
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: uploadedFileObjects.length ? `${jobStatus?.progress || 8}%` : '72%' }"></div>
            </div>
            <div class="progress-meta">
              <span>{{ jobStatus?.message || '正在识别页尾标识、页面结构和字段证据' }}</span>
              <strong>{{ uploadedFileObjects.length ? `${jobStatus?.progress || 0}%` : '模拟中' }}</strong>
            </div>
          </div>

          <div v-if="apiError" class="api-error" role="alert">
            {{ apiError }}
          </div>

          <button v-else class="primary-action" type="button" @click="startRecognition">
            开始识别
          </button>
        </section>
      </div>
    </section>

    <section v-else class="result-stage">
      <div class="result-toolbar">
        <div class="file-summary">
          <span class="file-label">{{ selectedDemoMode.shortLabel }}</span>
          <strong>{{ selectedApplicationType.label }}</strong>
          <span>{{ reviewResult.uploadedFiles.length }} 份上传材料</span>
        </div>
        <div class="result-view-controls">
          <div class="result-tabs" role="tablist" aria-label="结果视图切换">
            <button
              type="button"
              :class="{ active: resultView === 'recognition' }"
              @click="resultView = 'recognition'"
            >
              识别结果
            </button>
            <button
              type="button"
              :class="{ active: resultView === 'json' }"
              @click="resultView = 'json'"
            >
              JSON
            </button>
          </div>
          <div class="toolbar-actions">
            <span class="status-chip" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
              {{ displayReviewResult.decision }} · {{ decisionLabel(displayReviewResult.decision) }}
            </span>
            <button
              v-if="resultView !== 'verification'"
              class="secondary-action"
              type="button"
              :disabled="verificationLoading"
              @click="openVerificationPage"
            >
              进入核验结果页
            </button>
            <button v-else class="secondary-action" type="button" @click="resultView = 'recognition'">返回识别结果</button>
            <button class="secondary-action" type="button" @click="resetDemo">重新上传</button>
          </div>
        </div>
      </div>

      <div v-if="resultView === 'recognition'" class="review-workspace">
        <aside class="review-sidebar">
          <section class="decision-card" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
            <span>整体结论</span>
            <strong>{{ displayReviewResult.decision }}</strong>
            <p>{{ displayReviewResult.decisionText }}</p>
          </section>

          <section class="side-panel">
            <div class="panel-heading">
              <h2>材料完整性</h2>
              <p>{{ isFdhMode ? '1-3 为最终判定范围；4-12 为展示项。' : '标记“Demo审批”的材料参与当前结论，其余官方应交材料作为提示展示。' }}</p>
            </div>
            <div class="compact-material-list">
              <article
                v-for="material in reviewResult.materials"
                :key="material.id"
                class="compact-material"
                :class="[`status-${material.status}`, { core: material.core }]"
              >
                <div>
                  <strong>{{ material.no }}. {{ material.shortName }}</strong>
                  <span>{{ material.scopeText }}</span>
                </div>
                <span class="status-badge" :class="material.status">{{ material.statusText }}</span>
              </article>
            </div>
          </section>

          <section class="side-panel">
            <div class="panel-heading">
              <h2>上传文件</h2>
              <p>{{ isFdhMode ? '当前场景生成的模拟材料包。' : 'IANG 应届毕业生在港首次申请的演示材料包。' }}</p>
            </div>
            <div class="sidebar-file-list">
              <article v-for="file in reviewResult.uploadedFiles" :key="file.filename">
                <strong>{{ file.documentName }}</strong>
                <span>{{ file.filename }}</span>
              </article>
            </div>
          </section>
        </aside>

        <section class="review-main">
          <section v-if="reviewResult.documentFieldGroups?.length" class="document-fields-panel">
            <div class="panel-heading">
              <div>
                <h2>材料逐页字段识别</h2>
                <p>按材料和页码顺序展示可填写字段；ID 990A 当前仅识别前 5 页。</p>
              </div>
            </div>
            <div class="document-group-list">
              <article
                v-for="group in reviewResult.documentFieldGroups"
                :key="group.materialId"
                class="document-group-card"
              >
                <header>
                  <div>
                    <strong>{{ group.materialName }}</strong>
                    <span>{{ group.templateId }}{{ group.note ? ` · ${group.note}` : '' }}</span>
                  </div>
                </header>
                <div class="document-page-list">
                  <section v-for="page in group.pages" :key="`${group.materialId}:${page.pageNo}`" class="document-page-card">
                    <h3>第 {{ page.pageNo }} 页 · {{ page.title }}</h3>
                    <div class="document-field-table">
                      <div class="document-field-row document-field-head">
                        <span>字段</span>
                        <span>填写内容 / 识别值</span>
                        <span>状态</span>
                      </div>
                      <div
                        v-for="item in page.fields"
                        :key="`${group.materialId}:${page.pageNo}:${item.label}`"
                        class="document-field-row"
                      >
                        <span>{{ item.label }}</span>
                        <strong>{{ item.value || '未识别' }}</strong>
                        <span class="status-badge" :class="item.status">{{ statusLabel(item.status) }}</span>
                      </div>
                    </div>
                  </section>
                </div>
              </article>
            </div>
          </section>

          <section class="findings-panel">
            <div class="panel-heading">
              <h2>逐条结论与出处</h2>
              <p>先列阻断或待复核问题；出处精确到材料名称、章节和字段名称。</p>
            </div>

            <div v-if="blockingFindings.length" class="finding-list">
              <article v-for="finding in blockingFindings" :key="finding.id" class="finding-item" :class="finding.status">
                <span class="status-badge" :class="finding.status">{{ statusLabel(finding.status) }}</span>
                <div>
                  <strong>{{ finding.title }}</strong>
                  <p>{{ finding.text }}</p>
                  <small>出处：{{ finding.source }}</small>
                </div>
              </article>
            </div>
            <div v-else class="finding-pass">
              核心材料和关键字段未发现阻断或待人工复核问题。
            </div>

            <div v-if="nonBlockingMaterialHints.length" class="nonblocking-box">
              <strong>非阻断提示</strong>
              <span>
                {{ nonBlockingMaterialHints.map((item) => `${item.shortName}：${item.statusText}`).join('；') }}
              </span>
            </div>
          </section>

          <section class="fields-panel">
            <div class="panel-heading fields-heading">
              <div>
                <h2>标准化字段核验</h2>
                <p>字段按统一 key 聚合；同一字段可展示多份材料的局部快照证据。</p>
              </div>
              <div class="field-metrics" aria-label="字段统计">
                <span><strong>{{ displayFieldStats.total }}</strong>全部字段</span>
                <span><strong>{{ displayFieldStats.pass }}</strong>通过</span>
                <span><strong>{{ displayFieldStats.fail }}</strong>问题</span>
                <span><strong>{{ displayFieldStats.review }}</strong>待复核</span>
              </div>
            </div>

            <div class="filter-row" role="tablist" aria-label="字段筛选">
              <button type="button" :class="{ active: fieldFilter === 'all' }" @click="fieldFilter = 'all'">全部字段</button>
              <button type="button" :class="{ active: fieldFilter === 'issues' }" @click="fieldFilter = 'issues'">仅看问题</button>
              <button type="button" :class="{ active: fieldFilter === 'review' }" @click="fieldFilter = 'review'">仅看待人工审核</button>
              <button type="button" :class="{ active: fieldFilter === 'required' }" @click="fieldFilter = 'required'">仅看必填字段</button>
            </div>

            <div class="field-card-list">
              <template v-for="field in nonEmploymentFields" :key="field.key">
                <article class="standard-field-card" :class="field.status">
                <header class="field-card-header">
                  <div>
                    <span>{{ field.category }}</span>
                    <h3>{{ field.label }}</h3>
                    <code>{{ field.key }}</code>
                  </div>
                  <div class="field-card-actions">
                    <span v-if="field.required" class="required-pill">必填</span>
                    <span class="status-badge" :class="field.status">{{ statusLabel(field.status) }}</span>
                  </div>
                </header>

                <div class="field-card-body">
                  <div class="evidence-column">
                    <article
                      v-for="source in field.sources"
                      :key="`${field.key}:${source.documentName}:${source.fieldName}`"
                      class="evidence-card"
                      :class="{ 'without-crop': !source.snapshotDataUrl }"
                    >
                      <div v-if="source.snapshotDataUrl" class="snapshot-card">
                        <img :src="source.snapshotDataUrl" :alt="`${source.documentName} ${source.fieldName}`">
                      </div>
                      <div class="evidence-meta">
                        <strong>{{ source.documentName }}</strong>
                        <span>{{ source.section }}</span>
                        <span>{{ source.fieldName }}</span>
                        <small v-if="!source.snapshotDataUrl" class="evidence-crop-missing">未取得原始裁剪</small>
                        <div class="evidence-value-block">
                          <span class="evidence-value-label">识别值</span>
                          <strong class="evidence-value">
                            <template
                              v-for="(segment, index) in sourceValueSegments(field, source)"
                              :key="`${index}:${segment.text}:${segment.diff}`"
                            >
                              <mark v-if="segment.diff" class="value-diff-char">{{ segment.text }}</mark>
                              <span v-else>{{ segment.text }}</span>
                            </template>
                          </strong>
                        </div>
                        <small>置信度 {{ source.confidence }}%</small>
                      </div>
                    </article>
                    <article v-if="!field.sources.length" class="evidence-card without-crop">
                      <div class="evidence-meta">
                        <strong>未取得材料证据</strong>
                        <span>材料未上传或模板无法识别</span>
                      </div>
                    </article>
                  </div>

                  <div class="field-value-panel">
                    <div>
                      <span>{{ field.correctionApplied ? '建议采用值' : '归一化结果' }}</span>
                      <strong>{{ field.suggestedValue || field.normalizedValue }}</strong>
                      <small v-if="field.correctionApplied" class="field-original-value">
                        原始归一结果：{{ field.rawNormalizedValue }}
                      </small>
                    </div>
                    <div>
                      <span>核查标准</span>
                      <p>{{ field.rule }}</p>
                    </div>
                    <div v-if="field.correctionApplied" class="issue-box review">
                      {{ field.suggestionReason }}
                    </div>
                    <div v-else-if="field.issue" class="issue-box" :class="field.status">
                      {{ field.issue }}
                    </div>
                  </div>
                </div>
              </article>

              <section v-if="isEmploymentPeriodAnchorField(field) && employmentPeriods.length" class="employment-period-group">
                <h3 class="employment-period-title">家庭佣工的工作经验</h3>
                <article v-for="period in employmentPeriods" :key="period.n" class="employment-period-card">
                  <div class="employment-period-header">雇主{{ period.n }}</div>
                  <dl class="employment-period-body">
                    <div v-if="period.nameField" class="employment-period-row">
                      <dt>雇主{{ period.n }}名称</dt>
                      <dd>{{ employmentValue(period.nameField) }}</dd>
                    </div>
                    <div v-if="period.addressField" class="employment-period-row">
                      <dt>地址</dt>
                      <dd>{{ employmentValue(period.addressField) }}</dd>
                    </div>
                    <div v-if="period.periodFromField || period.periodToField" class="employment-period-row">
                      <dt>任职日期</dt>
                      <dd>由 {{ employmentValue(period.periodFromField) }} 至 {{ employmentValue(period.periodToField) }}</dd>
                    </div>
                  </dl>
                </article>
              </section>
              </template>
            </div>
          </section>
        </section>
      </div>
      <section v-else-if="resultView === 'json'" class="json-result-panel">
        <div class="panel-heading">
          <div>
            <h2>JSON</h2>
            <p>包含材料完整性、字段清单识别结果，以及字段审核结论；图片快照仅保留是否存在，不输出 base64。</p>
          </div>
        </div>
        <pre class="json-preview">{{ reviewJsonPreview }}</pre>
      </section>
      <section v-else class="verification-page">
        <div class="panel-heading">
          <div>
            <h2>核验结果页</h2>
            <p>{{ demoFlowDescription }}</p>
          </div>
          <span class="status-chip" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
            {{ displayReviewResult.decision }} · {{ decisionLabel(displayReviewResult.decision) }}
          </span>
        </div>

        <div v-if="verificationLoading" class="verification-loading" aria-live="polite">
          正在生成 Minutes 草拟建议...
        </div>
        <div v-if="verificationError" class="verification-note" role="status">
          {{ verificationError }}
        </div>
        <div v-if="verificationTemplate" class="verification-output">
          <div class="verification-summary-card" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
            <span>整体结论</span>
            <strong>{{ displayReviewResult.decision }} - {{ displayReviewResult.decisionText }}</strong>
            <p>{{ verificationTemplate.summaryText }}</p>
            <ul class="overall-bullet-list">
              <li v-for="item in verificationTemplate.overallBullets" :key="item.label">
                <span>{{ item.label }}：</span>
                <strong>{{ item.value }}</strong>
              </li>
            </ul>
          </div>

          <div class="template-status-legend" aria-label="字段状态图例">
            <span v-for="item in templateStatusLegend" :key="item.status" class="template-status-pill" :class="item.status">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path :d="templateStatusIconPath(item.status)" />
              </svg>
              <strong>{{ item.label }}</strong>
              <small>{{ item.text }}</small>
            </span>
          </div>

          <section class="verification-section">
            <div class="panel-heading">
              <h3>材料层核验</h3>
              <p>材料缺失、缺页、模板错误属于材料层面；不展示当前类别不要求的材料。</p>
            </div>
            <table class="material-template-table">
              <thead>
                <tr>
                  <th>材料</th>
                  <th>模板 / 页尾标识</th>
                  <th>核验状态</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="material in verificationTemplate.materialRows" :key="material.id">
                  <td>{{ material.no }}. {{ material.shortName }}</td>
                  <td>{{ material.templateId }}</td>
                  <td>
                    <span class="status-badge" :class="material.status">{{ material.statusLabel }}</span>
                  </td>
                  <td>{{ material.issue || '已纳入材料完整性判断。' }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section v-for="section in verificationTemplate.sections" :key="section.id" class="verification-section">
            <div class="panel-heading">
              <h3>{{ section.title }}</h3>
              <p>按香港入境处材料字段清单顺序回填；待复核字段保留建议值和冲突来源。</p>
            </div>
            <table class="minutes-template-table">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>回填值</th>
                  <th>状态</th>
                  <th>出处与草拟备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="fieldRow in section.rows" :key="fieldRow.key" :class="`field-${fieldRow.status}`">
                  <td>
                    <strong>{{ fieldRow.label }}</strong>
                    <code>{{ fieldRow.key }}</code>
                  </td>
                  <td>
                    <strong class="template-field-value">{{ fieldRow.displayValue }}</strong>
                    <small v-if="fieldRow.normalizedValue" class="template-normalized-result">
                      归一结果：{{ fieldRow.normalizedValue }}
                    </small>
                  </td>
                  <td>
                    <span class="template-status-pill compact" :class="fieldRow.status">
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path :d="templateStatusIconPath(fieldRow.status)" />
                      </svg>
                      {{ fieldRow.statusLabel }}
                    </span>
                  </td>
                  <td>
                    <p>{{ fieldRow.note }}</p>
                    <ul v-if="fieldRow.conflicts.length > 1" class="conflict-list">
                      <li v-for="conflict in fieldRow.conflicts" :key="`${fieldRow.key}:${conflict.value}`">
                        <strong>{{ conflict.value }}</strong>
                        <span>{{ conflict.sources.join('；') }}</span>
                      </li>
                    </ul>
                    <div v-if="fieldRow.sources.length" class="template-evidence-list">
                      <article
                        v-for="source in fieldRow.sources"
                        :key="`${fieldRow.key}:${source.documentName}:${source.section}:${source.fieldName}`"
                        class="template-evidence-card"
                        :class="{ 'without-crop': !source.snapshotDataUrl }"
                      >
                        <div
                          v-if="source.snapshotDataUrl"
                          class="template-evidence-snapshot"
                        >
                          <img
                            :src="source.snapshotDataUrl"
                            :alt="`${source.documentName} ${source.fieldName}`"
                          >
                        </div>
                        <div class="template-evidence-body">
                          <strong>{{ source.documentName }}</strong>
                          <span>{{ source.section }}</span>
                          <span>{{ source.fieldName }}</span>
                          <small v-if="!source.snapshotDataUrl" class="template-evidence-crop-missing">未取得原始裁剪</small>
                          <div class="template-evidence-value">
                            <span>识别值</span>
                            <strong>{{ templateSourceValue(source) }}</strong>
                          </div>
                          <small>置信度 {{ templateSourceConfidence(source) }}</small>
                        </div>
                      </article>
                    </div>
                    <small v-else class="template-empty-evidence">未取得可用字段证据</small>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
      </section>
    </section>
  </main>
</template>
