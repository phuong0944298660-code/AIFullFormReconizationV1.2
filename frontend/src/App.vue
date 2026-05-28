<script setup>
import { computed, ref, watch } from 'vue'
import {
  applicationTypes,
  buildReviewResult,
  buildUploadedFiles,
  materials,
  scenarios
} from './fdhMockData.js'

const selectedApplicationTypeId = ref(applicationTypes[0].id)
const selectedScenarioId = ref('missing_core')
const uploadedFiles = ref([])
const uploadedFileObjects = ref([])
const reviewResult = ref(null)
const processing = ref(false)
const fieldFilter = ref('all')
const fileInput = ref(null)
const jobStatus = ref(null)
const apiError = ref('')
const dragActive = ref(false)
let uploadSequence = 0
let uploadBatchSequence = 0
const FDH_JOB_POLL_INTERVAL_MS = 1000
const FDH_JOB_POLL_LIMIT = 1500

const selectedApplicationType = computed(() => {
  return applicationTypes.find((item) => item.id === selectedApplicationTypeId.value) || applicationTypes[0]
})

const selectedScenario = computed(() => {
  return scenarios.find((item) => item.id === selectedScenarioId.value) || scenarios[0]
})

const checklistPreview = computed(() => {
  return buildReviewResult(selectedApplicationTypeId.value, selectedScenarioId.value).materials
})

const fieldRows = computed(() => reviewResult.value?.fields || [])

const filteredFields = computed(() => {
  const rows = fieldRows.value
  if (fieldFilter.value === 'issues') return rows.filter((field) => field.status === 'fail')
  if (fieldFilter.value === 'review') return rows.filter((field) => field.status === 'review')
  if (fieldFilter.value === 'required') return rows.filter((field) => field.required)
  return rows
})

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

  const fieldFindings = reviewResult.value.fields
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

watch([selectedApplicationTypeId, selectedScenarioId], () => {
  fieldFilter.value = 'all'
  apiError.value = ''
  if (uploadedFiles.value.length && !uploadedFileObjects.value.length) {
    uploadedFiles.value = buildUploadedFiles(selectedScenarioId.value)
  }
  if (reviewResult.value?.scenarioId) {
    reviewResult.value = buildReviewResult(selectedApplicationTypeId.value, selectedScenarioId.value)
  }
})

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
  uploadedFiles.value = buildUploadedFiles(selectedScenarioId.value)
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
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
    reviewResult.value = buildReviewResult(selectedApplicationTypeId.value, selectedScenarioId.value)
    processing.value = false
  }, 700)
}

async function startBackendRecognition() {
  processing.value = true
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
  try {
    const form = new FormData()
    for (const entry of uploadedFileObjects.value) {
      const file = entry.file || entry
      form.append('files', file, file.name)
    }
    form.append('applicationTypeId', selectedApplicationTypeId.value)

    const started = await requestJson('/api/fdh/review/jobs', {
      method: 'POST',
      body: form
    })
    jobStatus.value = started
    const completed = await pollFdhJob(started.jobId)
    jobStatus.value = completed
    if (completed.status !== 'completed') {
      throw new Error(completed.error || completed.message || '识别任务未完成。')
    }
    reviewResult.value = completed.result
    uploadedFiles.value = completed.result?.uploadedFiles || uploadedFiles.value
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
}

function clearUploadedFiles() {
  if (processing.value) return
  uploadedFileObjects.value = []
  uploadedFiles.value = []
  uploadBatchSequence = 0
  reviewResult.value = null
  jobStatus.value = null
  apiError.value = ''
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
    jobStatus.value = latest
    if (['completed', 'failed', 'canceled'].includes(latest.status)) {
      return latest
    }
  }
  throw new Error('识别任务仍在处理中，请稍后刷新任务状态或检查后端日志。')
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  return response.json()
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

function decisionLabel(decision) {
  return {
    PASS: '允许通过',
    REVIEW: '需人工复核',
    FAIL: '不允许通过'
  }[decision] || decision
}

function materialRequirementLabel(material) {
  if (!material.applicable) return '不适用'
  if (material.core) return '核心必交'
  if (material.conditional) return '条件应交'
  return '官方应交'
}
</script>

<template>
  <main class="fdh-app">
    <header class="app-header">
      <div class="brand-block">
        <p class="eyebrow">FDH Entry Visa Review Demo</p>
        <h1>外籍家庭傭工入境簽證材料核验</h1>
        <p class="header-copy">
          先用纯前端模拟多文件上传、材料分类、字段核验和审批结论；后续后端可替换同一数据结构。
        </p>
      </div>

      <label class="scenario-pill" for="scenario-select">
        <span>模拟结果</span>
        <select id="scenario-select" v-model="selectedScenarioId" :disabled="processing">
          <option v-for="scenario in scenarios" :key="scenario.id" :value="scenario.id">
            {{ scenario.label }}
          </option>
        </select>
      </label>
    </header>

    <section v-if="!reviewResult" class="upload-stage">
      <div class="review-upload-panel">
        <section class="case-section" aria-labelledby="case-title">
          <div class="section-heading">
            <div>
              <h2 id="case-title">选择申请类别</h2>
              <p>用户只能选择所属类别；材料清单中的勾选状态不可交互。</p>
            </div>
            <span class="selected-case">{{ selectedApplicationType.checklistKey }}</span>
          </div>

          <div class="case-grid">
            <button
              v-for="applicationType in applicationTypes"
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
              <h2 id="checklist-title">该类别官方材料清单</h2>
              <p>材料 1-3 纳入最终判定；材料 4-12 只展示是否上传，不阻断本 demo 结论。</p>
            </div>
          </div>

          <div class="material-checklist">
            <article
              v-for="material in checklistPreview"
              :key="material.id"
              class="material-row"
              :class="[`status-${material.status}`, { core: material.core }]"
            >
              <span class="fake-checkbox" :class="{ checked: material.applicable }" aria-hidden="true"></span>
              <div class="material-main">
                <strong>{{ material.no }}. {{ material.name }}</strong>
                <span>{{ material.shortName }} · {{ material.templateId }} · {{ material.expectedPages }}{{ material.note ? ` · ${material.note}` : '' }}</span>
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
              <p>当前阶段点击上传会生成模拟多文件列表；文件内容暂不解析。</p>
            </div>
            <span class="scenario-note">{{ selectedScenario.shortLabel }}</span>
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
              <small>PDF / PNG / JPG · 支持 ID 988A、ID 988B、ID 407 与其他证明材料</small>
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
              <div v-else class="empty-panel">尚未选择材料。可上传真实文件；如直接开始识别，将使用右上角模拟结果。</div>
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
          <span class="file-label">申请类别</span>
          <strong>{{ selectedApplicationType.label }}</strong>
          <span>{{ reviewResult.uploadedFiles.length }} 份上传材料</span>
        </div>
        <div class="toolbar-actions">
          <span class="status-chip" :class="`decision-${reviewResult.decision.toLowerCase()}`">
            {{ reviewResult.decision }} · {{ decisionLabel(reviewResult.decision) }}
          </span>
          <button class="secondary-action" type="button" @click="resetDemo">重新上传</button>
        </div>
      </div>

      <div class="review-workspace">
        <aside class="review-sidebar">
          <section class="decision-card" :class="`decision-${reviewResult.decision.toLowerCase()}`">
            <span>整体结论</span>
            <strong>{{ reviewResult.decision }}</strong>
            <p>{{ reviewResult.decisionText }}</p>
          </section>

          <section class="side-panel">
            <div class="panel-heading">
              <h2>材料完整性</h2>
              <p>1-3 为最终判定范围；4-12 为展示项。</p>
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
              <p>当前场景生成的模拟材料包。</p>
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
                <span><strong>{{ reviewResult.stats.total }}</strong>全部字段</span>
                <span><strong>{{ reviewResult.stats.pass }}</strong>通过</span>
                <span><strong>{{ reviewResult.stats.fail }}</strong>问题</span>
                <span><strong>{{ reviewResult.stats.review }}</strong>待复核</span>
              </div>
            </div>

            <div class="filter-row" role="tablist" aria-label="字段筛选">
              <button type="button" :class="{ active: fieldFilter === 'all' }" @click="fieldFilter = 'all'">全部字段</button>
              <button type="button" :class="{ active: fieldFilter === 'issues' }" @click="fieldFilter = 'issues'">仅看问题</button>
              <button type="button" :class="{ active: fieldFilter === 'review' }" @click="fieldFilter = 'review'">仅看待人工审核</button>
              <button type="button" :class="{ active: fieldFilter === 'required' }" @click="fieldFilter = 'required'">仅看必填字段</button>
            </div>

            <div class="field-card-list">
              <article v-for="field in filteredFields" :key="field.key" class="standard-field-card" :class="field.status">
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
                    <article v-for="source in field.sources" :key="`${field.key}:${source.documentName}:${source.fieldName}`" class="evidence-card">
                    <div class="snapshot-card" :class="{ empty: !source.value && !source.snapshotDataUrl }">
                      <img v-if="source.snapshotDataUrl" :src="source.snapshotDataUrl" :alt="`${source.documentName} ${source.fieldName}`">
                      <span v-else>{{ source.snapshotText }}</span>
                    </div>
                      <div class="evidence-meta">
                        <strong>{{ source.documentName }}</strong>
                        <span>{{ source.section }}</span>
                        <span>{{ source.fieldName }}</span>
                        <small>置信度 {{ source.confidence }}%</small>
                      </div>
                    </article>
                    <article v-if="!field.sources.length" class="evidence-card">
                      <div class="snapshot-card empty"><span>missing</span></div>
                      <div class="evidence-meta">
                        <strong>未取得材料证据</strong>
                        <span>材料未上传或模板无法识别</span>
                      </div>
                    </article>
                  </div>

                  <div class="field-value-panel">
                    <div>
                      <span>归一化结果</span>
                      <strong>{{ field.normalizedValue }}</strong>
                    </div>
                    <div>
                      <span>核查标准</span>
                      <p>{{ field.rule }}</p>
                    </div>
                    <div v-if="field.issue" class="issue-box" :class="field.status">
                      {{ field.issue }}
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </section>
      </div>
    </section>
  </main>
</template>
