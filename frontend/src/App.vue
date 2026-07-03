<script setup>
import { computed, nextTick, ref, watch } from 'vue'
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

const LANGUAGES = [
  { id: 'en', label: 'English' },
  { id: 'zh', label: '繁體中文' }
]

const UI_TEXT = {
  en: {
    eyebrow: 'Immigration Department Document Review Demo',
    title: 'Recognition and Verification of Hong Kong Immigration Application Documents',
    demoFlowDescription: 'This demo covers application document upload, document recognition, structured field extraction and normalisation, cross-document intelligent checking, and automatic drafting of the assessment result.',
    language: 'Language',
    caseSwitch: 'Application stream switch',
    caseTitleFdh: 'Select Application Type',
    caseTitleStudent: 'Current Student Admission Scheme Scenario',
    caseHintFdh: 'Select the application type only. Checklist items are read-only.',
    caseHintStudent: 'Default: IANG application by a recent graduate staying in Hong Kong. The checklist marks official requirements and the current demo approval scope.',
    checklistTitleFdh: 'Official Checklist for This Application Type',
    checklistTitleStudent: 'Checklist for IANG Application by a Recent Graduate Staying in Hong Kong',
    checklistDescriptionFdh: 'Items 1-3 affect the final demo decision. Items 4-12 show upload status only and do not block this demo result.',
    checklistDescriptionStudent: 'Shows the official checklist for IANG application by a recent graduate staying in Hong Kong. Items marked "Demo approval" are included in the current decision.',
    uploadTitle: 'Upload Application Documents',
    uploadHintFdh: 'After the Foreign Domestic Helper application documents are uploaded, the backend recognition and verification flow will start.',
    uploadHintStudent: 'After the student application documents are uploaded, the backend recognition and verification flow will start.',
    dropzoneTitle: 'Select and upload multiple application documents',
    uploadHelperFdh: 'PDF / PNG / JPG · Supports ID 988A, ID 988B, ID 407 and other supporting documents',
    uploadHelperStudent: 'PDF / PNG / JPG · Supports ID 990A for IANG / Admission Scheme for Mainland Talents and Professionals, graduation proof, Exit-entry Permit / passport / HKID and payment screenshot',
    selectedFiles: 'Selected Documents',
    filesUnit: 'documents',
    listScrollable: 'List scrolls',
    clearAll: 'Clear all',
    unknownSize: 'Size unknown',
    pendingRecognition: 'Pending recognition',
    emptyUpload: 'No documents selected. Upload application documents before starting recognition.',
    progressFallback: 'Recognising footer identifiers, page structure and field evidence',
    startRecognition: 'Start Recognition',
    recognizing: 'Recognising...',
    uploadedMaterials: 'uploaded documents',
    resultTabs: 'Result view switch',
    recognitionResult: 'Recognition Result',
    jsonDescription: 'Includes material completeness, recognised field list, and field review conclusion. Image snapshots only indicate whether an image exists; base64 is not exported.',
    openVerification: 'Open Assessment Result',
    backToRecognition: 'Back to Recognition Result',
    reupload: 'Upload Again',
    recognizedMaterials: 'Recognised Documents',
    recognizedMaterialsCount: 'documents included in the current recognition result',
    sourcePagesTitle: 'Source Documents',
    sourcePagesHint: 'Shows original pages by document and page number. Click a field source on the right to locate it.',
    sourceThumbs: 'Document page thumbnails',
    page: 'Page',
    pagesPending: 'Pages pending recognition',
    noSourcePages: 'No source pages are available. Upload documents and finish recognition first.',
    documentFieldsTitle: 'Per-page Field Recognition',
    documentFieldsHint: 'Shows original fields by document and page number. Click a field row to locate and highlight that field on the source page.',
    field: 'Field',
    filledOrRecognisedValue: 'Completed content / recognised value',
    status: 'Status',
    valueConfidence: 'Value',
    normalizedFieldsTitle: 'Standardised Field Verification',
    normalizedFieldsHint: 'Fields are grouped by standard key. Click a field or source to locate the source evidence on the left.',
    fieldStats: 'Field statistics',
    allFields: 'All fields',
    passed: 'Passed',
    issues: 'Issues',
    pendingReview: 'Pending review',
    fieldFilter: 'Field filter',
    onlyIssues: 'Issues only',
    onlyReview: 'Manual review only',
    onlyRequired: 'Required fields only',
    required: 'Required',
    overallConfidence: 'Overall confidence',
    sources: 'sources',
    locatorConfidence: 'Location',
    notLocated: 'Not located',
    noEvidence: 'No document evidence available',
    recommendedValue: 'Recommended value',
    normalizedResult: 'Normalised result',
    originalNormalizedResult: 'Original normalised result',
    employmentExperience: 'Foreign Domestic Helper Employment Experience',
    employer: 'Employer',
    employerName: 'Employer name',
    address: 'Address',
    employmentPeriod: 'Employment period',
    from: 'From',
    to: 'to',
    findingsTitle: 'Itemised Findings and Sources',
    findingsHint: 'Blocking or review findings are listed first. Sources identify the document, section and field.',
    source: 'Source',
    noBlockingFindings: 'No blocking or manual-review issues were found in the core documents and key fields.',
    nonBlockingHint: 'Non-blocking notes',
    verificationPage: 'Assessment Result',
    generatingMinutes: 'Drafting assessment notes...',
    overallConclusion: 'Overall Conclusion',
    fieldStatusLegend: 'Field status legend',
    materialVerification: 'Document-level Verification',
    materialVerificationHint: 'Missing documents, missing pages and template mismatch are document-level issues. Documents not required for the current type are hidden.',
    material: 'Document',
    templateOrFooter: 'Template / footer identifier',
    verificationStatus: 'Verification status',
    remarks: 'Remarks',
    includedInCompleteness: 'Included in the material completeness check.',
    sectionHint: 'Filled in according to the Immigration Department document field order. Fields pending review keep the recommended value and conflict sources.',
    fillValue: 'Fill-in value',
    sourcesAndDraftRemarks: 'Sources and draft remarks',
    normalized: 'Normalised',
    cropMissing: 'Original crop not available',
    recognisedValue: 'Recognised value',
    confidence: 'Confidence',
    noUsableEvidence: 'No usable field evidence available',
    workflowFdh: 'Foreign Domestic Helper Entry Visa Review',
    workflowStudent: 'IANG Application by a Recent Graduate Staying in Hong Kong',
    applicationType: 'Application type',
    detailType: 'Sub-type',
    expectedUploadedMaterials: 'Required/uploaded documents',
    fieldCompletion: 'Field completion',
    uploadFirstError: 'Upload application documents before starting recognition.',
    jobUploadMessage: 'Uploading documents and creating a recognition job.',
    jobIncomplete: 'The recognition job has not completed.',
    backendFailed: 'Backend recognition failed.',
    requestTimeout: 'Request timed out. Check the backend service and try again.',
    jobStillProcessing: 'The recognition job is still processing. Refresh later or check backend logs.',
    failMissingCore: 'Missing core document',
    materialNeedsReview: 'Document requires review',
    noFieldEvidence: 'No usable field evidence available',
    currentLocatorEmpty: 'Click a field source on the right to locate the corresponding source document page on the left.',
    currentLocator: 'Current location',
    documentFallback: 'Document',
    unrecognised: 'Not recognised',
    statusPass: 'Pass',
    statusFail: 'Fail',
    statusReview: 'Pending review',
    statusWarn: 'Note',
    statusMuted: 'N/A',
    decisionPass: 'May approve',
    decisionReview: 'Manual review required',
    decisionFail: 'Do not approve',
    notApplicable: 'Not applicable',
    coreRequired: 'Core required',
    conditionallyRequired: 'Conditionally required',
    officialRequired: 'Officially required',
    demoApproval: 'Demo approval',
    subsequentStage: 'Subsequent stage',
    affectsFinalDecision: 'Affects final decision',
    officialChecklistNonBlocking: 'Official checklist item; non-blocking in this demo',
    notApplicableCurrentType: 'Not applicable to the current type'
  },
  zh: {
    eyebrow: 'Immigration Document Review Demo',
    title: '香港出入境申请材料识别与核验Demo',
    demoFlowDescription: '本Demo主要演示「申请材料上传→文档解析识别→字段结构化提取与归一→跨档智能校验→自动生成审核结论」端到端全流程',
    language: '语言',
    caseSwitch: '申请场景切换',
    caseTitleFdh: '选择申请类别',
    caseTitleStudent: '当前学生出入境场景',
    caseHintFdh: '用户只能选择所属类别；材料清单中的勾选状态不可交互。',
    caseHintStudent: '默认展示 IANG 应届毕业生在港首次申请，材料清单标明官方要求和 Demo 审批范围。',
    checklistTitleFdh: '该类别官方材料清单',
    checklistTitleStudent: 'IANG 应届毕业生在港首次申请材料清单',
    checklistDescriptionFdh: '材料 1-3 纳入最终判定；材料 4-12 只展示是否上传，不阻断本 demo 结论。',
    checklistDescriptionStudent: '展示 IANG 应届毕业生在港首次申请官方材料清单；标记“Demo审批”的材料参与当前结论。',
    uploadTitle: '上传申请材料包',
    uploadHintFdh: '上传家庭佣工申请材料后，系统将调用后端进行识别与核验。',
    uploadHintStudent: '上传真实学生申请材料后，系统将调用后端进行识别与核验。',
    dropzoneTitle: '选择并上传多份申请材料',
    uploadHelperFdh: 'PDF / PNG / JPG · 支持 ID 988A、ID 988B、ID 407 与其他证明材料',
    uploadHelperStudent: 'PDF / PNG / JPG · 支持 ID 990A、毕业证明、港澳通行证 / 护照 / HKID、付款截图',
    selectedFiles: '已选择材料',
    filesUnit: '份',
    listScrollable: '列表可滚动',
    clearAll: '清空全部',
    unknownSize: '大小未知',
    pendingRecognition: '待识别',
    emptyUpload: '尚未选择材料。请先上传申请材料后再开始识别。',
    progressFallback: '正在识别页尾标识、页面结构和字段证据',
    startRecognition: '开始识别',
    recognizing: '识别中...',
    uploadedMaterials: '份上传材料',
    resultTabs: '结果视图切换',
    recognitionResult: '识别结果',
    jsonDescription: '包含材料完整性、字段清单识别结果，以及字段审核结论；图片快照仅保留是否存在，不输出 base64。',
    openVerification: '进入核验结果页',
    backToRecognition: '返回识别结果',
    reupload: '重新上传',
    recognizedMaterials: '已识别材料',
    recognizedMaterialsCount: '份材料参与当前识别结果',
    sourcePagesTitle: '材料原文',
    sourcePagesHint: '按材料和页码展示参与识别的原始页面；点击右侧字段来源后自动定位。',
    sourceThumbs: '材料页缩略图',
    page: '第 {page} 页',
    pagesPending: '页数待识别',
    noSourcePages: '暂未取得原文页面。请先上传并完成材料识别。',
    documentFieldsTitle: '材料逐页字段识别',
    documentFieldsHint: '按材料和页码展示参与识别的原始字段；点击字段行后，左侧原文自动定位并只高亮当前字段。',
    field: '字段',
    filledOrRecognisedValue: '填写内容 / 识别值',
    status: '状态',
    valueConfidence: '值',
    normalizedFieldsTitle: '标准化字段核验',
    normalizedFieldsHint: '字段按统一 key 聚合；点击字段或来源可定位左侧原文证据。',
    fieldStats: '字段统计',
    allFields: '全部字段',
    passed: '通过',
    issues: '问题',
    pendingReview: '待复核',
    fieldFilter: '字段筛选',
    onlyIssues: '仅看问题',
    onlyReview: '仅看待人工审核',
    onlyRequired: '仅看必填字段',
    required: '必填',
    overallConfidence: '综合置信度',
    sources: '条来源',
    locatorConfidence: '定位',
    notLocated: '未定位',
    noEvidence: '未取得材料证据',
    recommendedValue: '建议采用值',
    normalizedResult: '归一化结果',
    originalNormalizedResult: '原始归一结果',
    employmentExperience: '家庭佣工的工作经验',
    employer: '雇主',
    employerName: '雇主名称',
    address: '地址',
    employmentPeriod: '任职日期',
    from: '由',
    to: '至',
    findingsTitle: '逐条结论与出处',
    findingsHint: '先列阻断或待复核问题；出处精确到材料名称、章节和字段名称。',
    source: '出处',
    noBlockingFindings: '核心材料和关键字段未发现阻断或待人工复核问题。',
    nonBlockingHint: '非阻断提示',
    verificationPage: '核验结果页',
    generatingMinutes: '正在生成 Minutes 草拟建议...',
    overallConclusion: '整体结论',
    fieldStatusLegend: '字段状态图例',
    materialVerification: '材料层核验',
    materialVerificationHint: '材料缺失、缺页、模板错误属于材料层面；不展示当前类别不要求的材料。',
    material: '材料',
    templateOrFooter: '模板 / 页尾标识',
    verificationStatus: '核验状态',
    remarks: '备注',
    includedInCompleteness: '已纳入材料完整性判断。',
    sectionHint: '按香港入境处材料字段清单顺序回填；待复核字段保留建议值和冲突来源。',
    fillValue: '回填值',
    sourcesAndDraftRemarks: '出处与草拟备注',
    normalized: '归一结果',
    cropMissing: '未取得原始裁剪',
    recognisedValue: '识别值',
    confidence: '置信度',
    noUsableEvidence: '未取得可用字段证据',
    workflowFdh: '外籍家庭佣工入境审核',
    workflowStudent: 'IANG 应届毕业生在港首次申请',
    applicationType: '申请类别',
    detailType: '细分类别',
    expectedUploadedMaterials: '应上传/已上传材料',
    fieldCompletion: '字段填写情况',
    uploadFirstError: '请先上传申请材料后再开始识别',
    jobUploadMessage: '正在上传材料并创建识别任务。',
    jobIncomplete: '识别任务未完成。',
    backendFailed: '后端识别失败。',
    requestTimeout: '请求超时，请检查后端服务后重试。',
    jobStillProcessing: '识别任务仍在处理中，请稍后刷新任务状态或检查后端日志。',
    failMissingCore: '缺少核心材料',
    materialNeedsReview: '材料需复核',
    noFieldEvidence: '未取得可用字段证据',
    currentLocatorEmpty: '点击右侧字段来源后，左侧将定位到对应材料页面。',
    currentLocator: '当前定位',
    documentFallback: '材料',
    unrecognised: '未识别',
    statusPass: '通过',
    statusFail: '不通过',
    statusReview: '待复核',
    statusWarn: '提示',
    statusMuted: '不适用',
    decisionPass: '允许通过',
    decisionReview: '需人工复核',
    decisionFail: '不允许通过',
    notApplicable: '不适用',
    coreRequired: '核心必交',
    conditionallyRequired: '条件应交',
    officialRequired: '官方应交',
    demoApproval: 'Demo审批',
    subsequentStage: '后续阶段',
    affectsFinalDecision: '影响最终结论',
    officialChecklistNonBlocking: '官方清单项，本 demo 不阻断',
    notApplicableCurrentType: '当前类别不适用'
  }
}

const demoModes = [
  {
    id: 'student_iang',
    label: {
      en: 'Immigration Arrangements for Non-local Graduates (IANG)',
      zh: 'IANG 应届毕业生在港首次申请'
    },
    shortLabel: {
      en: 'IANG',
      zh: '学生 / IANG'
    },
    description: {
      en: 'Default stream: IANG application by a recent graduate staying in Hong Kong.',
      zh: '默认演示学生出入境 IANG 应届毕业生在港首次申请材料识别。'
    }
  },
  {
    id: 'fdh',
    label: {
      en: 'Foreign Domestic Helper',
      zh: '家庭佣工'
    },
    shortLabel: {
      en: 'FDH',
      zh: '家庭佣工'
    },
    description: {
      en: 'Keeps the current Foreign Domestic Helper document review flow.',
      zh: '保留当前外籍家庭佣工材料核验流程。'
    }
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
const selectedFieldSourceKey = ref('')
const fileInput = ref(null)
const jobStatus = ref(null)
const apiError = ref('')
const dragActive = ref(false)
const currentLanguage = ref('en')
let uploadSequence = 0
let uploadBatchSequence = 0
let verificationSequence = 0
const FDH_JOB_POLL_INTERVAL_MS = 1000
const FDH_JOB_POLL_LIMIT = 1500
const REVIEW_JOB_START_TIMEOUT_MS = 60000

const APPLICATION_TYPE_TEXT = {
  iang_recent_in_hk: {
    en: {
      label: 'IANG application by a recent graduate staying in Hong Kong',
      shortLabel: 'IANG recent graduate',
      description: 'Non-local graduates who obtained an undergraduate or higher qualification in Hong Kong apply under IANG within six months after graduation.',
      checklistKey: 'IANG application by a recent graduate staying in Hong Kong'
    }
  },
  entry_visa: {
    en: {
      label: 'Entry Visa',
      shortLabel: 'Entry Visa',
      description: 'Visa application for a Foreign Domestic Helper coming to Hong Kong from abroad.',
      checklistKey: 'Entry Visa'
    }
  },
  renewal: {
    en: {
      label: 'Renewal upon expiry of a two-year contract',
      shortLabel: 'Renewal',
      description: 'Renewal with the same employer after expiry of the two-year contract.',
      checklistKey: 'Renewal upon expiry of a two-year contract'
    }
  },
  remaining_period: {
    en: {
      label: 'Completion of the remaining period of current contract',
      shortLabel: 'Remaining period',
      description: 'Application to complete the remaining period of an existing contract.',
      checklistKey: 'Completion of the remaining period of current contract'
    }
  },
  change_employer: {
    en: {
      label: 'Change of employer',
      shortLabel: 'Change employer',
      description: 'Application related to renewal with the same employer or change of employer.',
      checklistKey: 'Change of employer'
    }
  }
}

const MATERIAL_TEXT = {
  id988a: {
    en: {
      name: 'Application for Visa / Application for Extension of Stay for Foreign Domestic Helper',
      shortName: 'ID 988A'
    }
  },
  id988b: {
    en: {
      name: 'Application for Employment of Domestic Helper from Abroad',
      shortName: 'ID 988B'
    }
  },
  id407: {
    en: {
      name: 'Original copy of the Standard Employment Contract',
      shortName: 'ID 407'
    }
  },
  helperTravelOriginal: {
    en: {
      name: "Helper's travel document (original)",
      shortName: 'Travel document (original)'
    }
  },
  helperTravelCopy: {
    en: {
      name: "Helper's travel document (copy)",
      shortName: 'Travel document copy'
    }
  },
  helperHkid: {
    en: {
      name: "Helper's Hong Kong identity card copy, if applicable",
      shortName: "Helper's HKID"
    }
  },
  employerId: {
    en: {
      name: "Employer's Hong Kong permanent identity card / Hong Kong identity card / passport copy",
      shortName: "Employer's identity document"
    }
  },
  financialProof: {
    en: {
      name: "Employer's proof of financial position (copy)",
      shortName: 'Proof of financial position'
    }
  },
  addressProof: {
    en: {
      name: "Employer's proof of residential address (copy)",
      shortName: 'Proof of address'
    }
  },
  referenceLetter: {
    en: {
      name: "Helper's reference letter",
      shortName: 'Reference letter'
    }
  },
  releaseLetter: {
    en: {
      name: 'Release letter from the current employer showing the contract expiry / termination date',
      shortName: 'Release letter'
    }
  },
  continueEmploymentLetter: {
    en: {
      name: "Employer's confirmation letter for continued employment",
      shortName: 'Continued employment confirmation'
    }
  },
  id990a: {
    en: {
      name: 'Application for Entry for Employment as Professionals in Hong Kong',
      shortName: 'ID 990A',
      expectedPages: 'First 5 pages',
      statusText: 'First 5 pages recognised',
      requirementLabel: 'Demo approval',
      scopeText: 'Demo approval; recognises the first 5 pages of ID 990A by footer page number'
    }
  },
  educationProof: {
    en: {
      name: 'Proof of academic qualification / graduation eligibility',
      shortName: 'Graduation proof',
      expectedPages: 'As evidenced',
      statusText: 'Graduation eligibility can be verified',
      requirementLabel: 'Demo approval',
      scopeText: 'Demo approval; checks qualification level and six-month recent graduate window'
    }
  },
  identityDocs: {
    en: {
      name: 'Exit-entry Permit / passport / Hong Kong identity card',
      shortName: 'Identity and travel document',
      expectedPages: 'Bio-data page and HKID',
      statusText: 'Identity fields can be verified',
      requirementLabel: 'Demo approval',
      scopeText: 'Demo approval; cross-checks the application form, graduation proof and payment record'
    }
  },
  paymentStatus: {
    en: {
      name: 'Payment Status / application fee payment screenshot',
      shortName: 'Payment Status',
      expectedPages: '1 page',
      statusText: 'Payment is not yet complete',
      issue: 'The payment page shows "NOT YET COMPLETE". Payment completion must be confirmed before approval.',
      requirementLabel: 'Demo approval',
      scopeText: 'Demo approval; keep REVIEW when payment is not complete'
    }
  },
  photo: {
    en: {
      name: 'Recent photograph of applicant',
      shortName: 'Applicant photo',
      expectedPages: '1 photo',
      statusText: 'Not uploaded',
      requirementLabel: 'Officially required',
      scopeText: 'Not blocking in the current demo'
    }
  },
  currentStayEvidence: {
    en: {
      name: 'Latest arrival record / landing slip / e-Visa',
      shortName: 'Current stay record',
      expectedPages: 'As recorded',
      statusText: 'Not uploaded',
      requirementLabel: 'Officially required',
      scopeText: 'Confirms current stay in Hong Kong and stay limit; non-blocking in the current demo'
    }
  },
  mainlandConsent: {
    en: {
      name: 'Consent letter for Mainland Chinese residents taking up employment in Hong Kong',
      shortName: 'Mainland consent letter',
      expectedPages: '1 page',
      statusText: 'Conditionally required for Mainland residents',
      requirementLabel: 'Conditionally required',
      scopeText: 'Applies to Mainland residents; shown as conditionally required and non-blocking in the current demo'
    }
  },
  visaIssueFee: {
    en: {
      name: 'Payment proof for visa issue fee / downloaded e-Visa after approval',
      shortName: 'Post-approval result document',
      expectedPages: 'Generated after approval',
      statusText: 'Post-approval stage document',
      requirementLabel: 'Subsequent stage',
      scopeText: 'Used for result filing after approval; outside the blocking scope of the current first-round review'
    }
  }
}

const FIELD_TEXT = {
  'case.application_type': { en: { category: 'Case and document', label: 'Application type' } },
  'document.footer_id': { en: { category: 'Case and document', label: 'Footer template identifier' } },
  'helper.name.full_en': { en: { category: 'Helper fields', label: "Helper's full name in English" } },
  'helper.travel_doc.number': { en: { category: 'Helper fields', label: "Helper's travel document number" } },
  'helper.date_of_birth': { en: { category: 'Helper fields', label: "Helper's date of birth" } },
  'helper.nationality': { en: { category: 'Helper fields', label: "Helper's nationality" } },
  'helper.signature.present': { en: { category: 'Helper fields', label: "Helper's signature" } },
  'employer.name.full_en': { en: { category: 'Employer fields', label: "Employer's full name in English" } },
  'employer.signature.present': { en: { category: 'Employer fields', label: "Employer's signature" } },
  'contract.dh_contract_no': { en: { category: 'Contract fields', label: 'Standard Employment Contract number' } },
  'contract.monthly_wage_hkd': { en: { category: 'Contract fields', label: 'Monthly wages' } },
  'contract.food.allowance_hkd': { en: { category: 'Contract fields', label: 'Food allowance' } },
  'applicant.name.full_en': { en: { category: 'Applicant identity', label: "Applicant's name in English" } },
  'applicant.hkid.number': { en: { category: 'Applicant identity', label: 'Hong Kong identity card number' } },
  'applicant.travel_doc.number': { en: { category: 'Applicant identity', label: 'Exit-entry Permit / passport number' } },
  'applicant.date_of_birth': { en: { category: 'Applicant identity', label: 'Date of birth' } },
  'education.institution': { en: { category: 'Academic qualification and graduation eligibility', label: 'Institution' } },
  'education.program': { en: { category: 'Academic qualification and graduation eligibility', label: 'Programme / major' } },
  'education.graduation_date': { en: { category: 'Academic qualification and graduation eligibility', label: 'Graduation / programme completion date' } },
  'education.recent_graduate_window': { en: { category: 'Academic qualification and graduation eligibility', label: 'Six-month recent graduate window' } },
  'payment.application_fee.status': { en: { category: 'Payment Status', label: 'Application fee payment status' } },
  'applicant.declaration.signature.present': { en: { category: 'IANG / professionals application form', label: "Applicant's declaration and signature" } }
}

const TEMPLATE_TEXT = {
  pass: { en: { label: 'Passed', text: 'Recognised and passed field rules.' } },
  unrecognized: { en: { label: 'Not recognised', text: 'No reliable field result was obtained.' } },
  required_missing: { en: { label: 'Required field blank', text: 'The required field is blank or no completion mark was detected.' } },
  review: { en: { label: 'Pending review', text: 'Low confidence or cross-document inconsistency; manual confirmation is required.' } }
}

function t(key, replacements = {}) {
  const template = UI_TEXT[currentLanguage.value]?.[key] || UI_TEXT.en[key] || key
  const rendered = Object.entries(replacements).reduce(
    (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
    template
  )
  return currentLanguage.value === 'zh' ? toTraditional(rendered) : rendered
}

function localizedText(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const text = value[currentLanguage.value] || value.en || value.zh || ''
    return currentLanguage.value === 'zh' ? toTraditional(text) : text
  }
  return currentLanguage.value === 'zh' ? toTraditional(value) : value
}

function localizedDemoMode(mode) {
  return {
    ...mode,
    label: localizedText(mode.label),
    shortLabel: localizedText(mode.shortLabel),
    description: localizedText(mode.description)
  }
}

function withLocalizedFields(item, dictionary) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(item)
  return {
    ...item,
    ...(dictionary[item?.id]?.[currentLanguage.value] || {})
  }
}

function localizedApplicationType(applicationType) {
  return withLocalizedFields(applicationType, APPLICATION_TYPE_TEXT)
}

function localizedMaterial(material) {
  return withLocalizedFields(material, MATERIAL_TEXT)
}

function localizedField(field) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(field)
  const text = FIELD_TEXT[field?.key]?.en || {}
  return {
    ...field,
    ...text,
    sources: (field?.sources || []).map(localizedSource)
  }
}

function localizedSource(source) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(source)
  return {
    ...source,
    documentName: localizedDocumentName(source.documentName, source.materialId),
    section: localizedSection(source.section),
    fieldName: localizedFieldName(source.fieldName)
  }
}

function localizedDocumentName(name, materialId = '') {
  if (currentLanguage.value === 'zh') return toTraditional(name)
  if (materialId && MATERIAL_TEXT[materialId]?.en?.shortName) return MATERIAL_TEXT[materialId].en.shortName
  const matched = Object.values(MATERIAL_TEXT).find((item) => item.en?.shortName === name || item.en?.name === name)
  return matched?.en?.shortName || COMMON_TEXT_TRANSLATIONS[name]?.en || name
}

function localizedFieldName(name) {
  if (currentLanguage.value === 'zh') return toTraditional(name)
  const matched = Object.values(FIELD_TEXT).find((item) => item.en?.label === name)
  return matched?.en?.label || COMMON_TEXT_TRANSLATIONS[name]?.en || name
}

function localizedSection(section) {
  if (currentLanguage.value === 'zh') return toTraditional(section)
  return String(section || '')
    .replace(/第\s*(\d+)\s*页/g, (_, page) => `Page ${page}`)
    .replaceAll('基础资料', 'Basic information')
    .replaceAll('身份资料', 'Identity information')
    .replaceAll('学历证明', 'Academic qualification proof')
    .replaceAll('付款截图', 'Payment screenshot')
}

function localizeReviewResult(result) {
  if (!result) return result
  return {
    ...result,
    decisionText: localizedDecisionText(result.decisionText),
    uploadedFiles: (result.uploadedFiles || []).map(localizedUploadedFile),
    materials: (result.materials || []).map(localizedMaterial),
    fields: (result.fields || []).map(localizedField),
    reviewPages: (result.reviewPages || []).map(localizedReviewPage),
    documentFieldGroups: (result.documentFieldGroups || []).map(localizedDocumentFieldGroup)
  }
}

function localizedUploadedFile(file) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(file)
  return {
    ...file,
    documentName: localizedDocumentName(file.documentName, file.materialId),
    footerId: localizedTextValue(file.footerId)
  }
}

function localizedReviewPage(page) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(page)
  return {
    ...page,
    documentName: localizedDocumentName(page.documentName, page.materialId),
    title: localizedSection(page.title)
  }
}

function localizedDocumentFieldGroup(group) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(group)
  return {
    ...group,
    materialName: localizedDocumentName(group.materialName, group.materialId),
    pages: (group.pages || []).map((page) => ({
      ...page,
      title: localizedSection(page.title),
      fields: (page.fields || []).map((item) => ({
        ...item,
        label: localizedFieldName(item.label)
      }))
    }))
  }
}

function localizedTextValue(value) {
  if (currentLanguage.value === 'zh') return toTraditional(value)
  return COMMON_TEXT_TRANSLATIONS[value]?.en || value
}

function localizedDecisionText(value) {
  if (currentLanguage.value === 'zh') return toTraditional(value)
  if (!value) return value
  if (String(value).includes('NOT YET COMPLETE')) {
    return 'The Payment Status shows that the application process is not yet complete. Confirm payment completion before final approval.'
  }
  return value
}

function localizedFindings(findings) {
  if (currentLanguage.value === 'zh') return toTraditionalObject(findings)
  return findings.map((finding) => ({
    ...finding,
    title: localizedTextValue(finding.title),
    text: localizedTextValue(finding.text),
    source: localizedSection(localizedTextValue(finding.source))
  }))
}

function localizedTemplate(template) {
  if (!template) return template
  if (currentLanguage.value === 'zh') return toTraditionalObject(template)
  return {
    ...template,
    summaryText: `Missing core documents: ${template.summary.missingCoreMaterials}; required fields: ${template.summary.requiredFields}; required fields blank: ${template.summary.requiredMissingFields}; not recognised: ${template.summary.unrecognizedFields}; pending review: ${template.summary.reviewFields}.`,
    overallBullets: (template.overallBullets || []).map((item) => ({
      ...item,
      label: localizedTextValue(item.label),
      value: localizedTextValue(item.value)
    })),
    materialRows: (template.materialRows || []).map(localizedTemplateMaterialRow),
    sections: (template.sections || []).map((section) => ({
      ...section,
      title: localizedTextValue(section.title),
      rows: (section.rows || []).map(localizedTemplateFieldRow)
    }))
  }
}

function localizedTemplateMaterialRow(row) {
  return {
    ...row,
    ...((MATERIAL_TEXT[row.id] || {}).en || {}),
    statusLabel: localizedMaterialStatusLabel(row.status),
    issue: localizedTextValue(row.issue)
  }
}

function localizedTemplateFieldRow(row) {
  const fieldText = FIELD_TEXT[row.key]?.en || {}
  return {
    ...row,
    ...fieldText,
    statusLabel: TEMPLATE_TEXT[row.status]?.en?.label || row.statusLabel,
    displayValue: localizedTextValue(row.displayValue),
    note: localizedTextValue(row.note),
    sources: (row.sources || []).map(localizedSource),
    conflicts: (row.conflicts || []).map((conflict) => ({
      ...conflict,
      sources: (conflict.sources || []).map(localizedSection)
    }))
  }
}

function localizedMaterialStatusLabel(status) {
  return {
    pass: 'Recognised',
    fail: 'Missing document',
    review: 'Pending review',
    warn: 'Recorded'
  }[status] || status
}

function localizedTemplateStatusLegend() {
  if (currentLanguage.value === 'zh') return toTraditionalObject(TEMPLATE_STATUS_LEGEND)
  return TEMPLATE_STATUS_LEGEND.map((item) => ({
    ...item,
    ...(TEMPLATE_TEXT[item.status]?.en || {})
  }))
}

function setLanguage(languageId) {
  if (LANGUAGES.some((language) => language.id === languageId)) {
    currentLanguage.value = languageId
  }
}

const COMMON_TEXT_TRANSLATIONS = {
  '未识别': { en: 'Not recognised' },
  '未填写': { en: 'Blank' },
  '已识别并通过。': { en: 'Recognised and passed.' },
  '必填字段未填写，需退回补正。': { en: 'Required field is blank; follow-up is required.' },
  '未取得可靠识别结果，需要人工查看原件。': { en: 'No reliable recognition result was obtained. Manual inspection of the original is required.' },
  '置信度偏低，需人工复核。': { en: 'Confidence is low. Manual review is required.' },
  '案件信息': { en: 'Case information' },
  '雇工信息': { en: 'Helper information' },
  '雇主信息': { en: 'Employer information' },
  '合约信息': { en: 'Contract information' },
  '申请类别': { en: 'Application type' },
  '细分类别': { en: 'Sub-type' },
  '应上传/已上传材料': { en: 'Required/uploaded documents' },
  '字段填写情况': { en: 'Field completion' },
  '允许通过': { en: 'May approve' },
  '需人工复核': { en: 'Manual review required' },
  '不允许通过': { en: 'Do not approve' },
  '付款状态显示申请流程尚未完成，需确认付款完成后再进入最终通过。': {
    en: 'The Payment Status shows that the application process is not yet complete. Confirm payment completion before final approval.'
  },
  '付款页显示 “NOT YET COMPLETE”，需要补缴或确认付款完成状态。': {
    en: 'The payment page shows "NOT YET COMPLETE". Payment completion must be confirmed before approval.'
  },
  '材料需复核：付款状态': { en: 'Document requires review: Payment Status' },
  '付款状态 · Online payment page': { en: 'Payment Status · Online payment page' },
  '缺少核心材料': { en: 'Missing core document' },
  '材料需复核': { en: 'Document requires review' },
  '毕业证明': { en: 'Graduation proof' },
  '身份及旅行证件': { en: 'Identity and travel document' },
  '付款状态': { en: 'Payment Status' }
}

const TRADITIONAL_PHRASES = [
  ['香港出入境', '香港入境事務處'],
  ['香港入境处', '香港入境事務處'],
  ['入境处', '入境事務處'],
  ['出入境', '入境事務'],
  ['港澳通行证', '往來港澳通行證'],
  ['香港身份证', '香港身份證'],
  ['应届毕业生', '應屆畢業生'],
  ['家庭佣工', '家庭傭工'],
  ['外籍家庭佣工', '外籍家庭傭工'],
  ['申请材料', '申請材料'],
  ['申请类别', '申請類別'],
  ['材料清单', '材料清單'],
  ['付款状态', '付款狀態'],
  ['识别结果', '識別結果'],
  ['核验结果', '核驗結果'],
  ['待复核', '待覆核'],
  ['人工复核', '人工覆核'],
  ['核验', '核驗'],
  ['识别', '識別'],
  ['申请', '申請'],
  ['材料', '材料']
]

const TRADITIONAL_CHARS = {
  认: '認',
  识: '識',
  证: '證',
  验: '驗',
  处: '處',
  务: '務',
  资: '資',
  料: '料',
  申: '申',
  请: '請',
  类: '類',
  别: '別',
  场: '場',
  景: '景',
  切: '切',
  换: '換',
  语: '語',
  言: '言',
  默: '默',
  认: '認',
  演: '演',
  示: '示',
  学: '學',
  生: '生',
  应: '應',
  届: '屆',
  毕: '畢',
  业: '業',
  首: '首',
  次: '次',
  后: '後',
  留: '留',
  工: '工',
  作: '作',
  逗: '逗',
  毕: '畢',
  内: '內',
  地: '地',
  非: '非',
  本: '本',
  科: '科',
  或: '或',
  以: '以',
  上: '上',
  课: '課',
  程: '程',
  个: '個',
  月: '月',
  选: '選',
  择: '擇',
  所: '所',
  属: '屬',
  勾: '勾',
  交: '交',
  互: '互',
  官: '官',
  方: '方',
  清: '清',
  单: '單',
  标: '標',
  明: '明',
  审: '審',
  批: '批',
  范: '範',
  围: '圍',
  包: '包',
  真: '真',
  实: '實',
  调: '調',
  用: '用',
  后: '後',
  端: '端',
  进: '進',
  行: '行',
  与: '與',
  多: '多',
  份: '份',
  支: '支',
  持: '持',
  毕: '畢',
  证: '證',
  护: '護',
  照: '照',
  截: '截',
  图: '圖',
  已: '已',
  列: '列',
  表: '表',
  滚: '滾',
  动: '動',
  空: '空',
  全: '全',
  大: '大',
  小: '小',
  未: '未',
  页: '頁',
  尾: '尾',
  结: '結',
  构: '構',
  字: '字',
  段: '段',
  据: '據',
  开: '開',
  始: '始',
  上传: '上傳',
  选: '選',
  份: '份',
  视: '視',
  图: '圖',
  完: '完',
  整: '整',
  性: '性',
  论: '論',
  仅: '僅',
  存: '存',
  在: '在',
  输: '輸',
  出: '出',
  进: '進',
  入: '入',
  返: '返',
  回: '回',
  重: '重',
  新: '新',
  原: '原',
  文: '文',
  按: '按',
  码: '碼',
  参: '參',
  加: '加',
  点: '點',
  击: '擊',
  右: '右',
  侧: '側',
  来: '來',
  源: '源',
  自: '自',
  定: '定',
  位: '位',
  缩: '縮',
  略: '略',
  逐: '逐',
  填: '填',
  写: '寫',
  状: '狀',
  态: '態',
  值: '值',
  准: '準',
  统: '統',
  一: '一',
  聚: '聚',
  合: '合',
  证: '證',
  统: '統',
  计: '計',
  过: '過',
  问: '問',
  题: '題',
  筛: '篩',
  须: '須',
  条: '條',
  建: '建',
  议: '議',
  采: '採',
  纳: '納',
  归: '歸',
  化: '化',
  果: '果',
  历: '歷',
  经: '經',
  雇: '僱',
  主: '主',
  名: '名',
  称: '稱',
  址: '址',
  任: '任',
  职: '職',
  由: '由',
  至: '至',
  逐: '逐',
  条: '條',
  出: '出',
  先: '先',
  阻: '阻',
  断: '斷',
  精: '精',
  确: '確',
  关: '關',
  键: '鍵',
  发: '發',
  现: '現',
  提: '提',
  示: '示',
  整: '整',
  体: '體',
  草: '草',
  拟: '擬',
  层: '層',
  缺: '缺',
  错: '錯',
  误: '誤',
  于: '於',
  义: '義',
  渲: '渲',
  染: '染',
  顺: '順',
  序: '序',
  冲: '衝',
  突: '突',
  备: '備',
  注: '註',
  裁: '裁',
  剪: '剪',
  可: '可',
  置: '置',
  信: '信',
  度: '度',
  个: '個',
  错: '錯',
  检: '檢',
  查: '查',
  稍: '稍',
  刷: '刷',
  迟: '遲',
  状: '狀',
  费: '費',
  录: '錄',
  离: '離',
  书: '書',
  续: '續',
  约: '約',
  转: '轉',
  副: '副',
  份: '份',
  复: '覆',
  户: '戶',
  资: '資',
  济: '濟',
  况: '況',
  码: '碼',
  数: '數',
  评: '評',
  临: '臨',
  顾: '顧',
  拥: '擁'
}

function toTraditional(value) {
  if (typeof value !== 'string') return value
  let text = value
  for (const [source, target] of TRADITIONAL_PHRASES) {
    text = text.replaceAll(source, target)
  }
  return Array.from(text).map((char) => TRADITIONAL_CHARS[char] || char).join('')
}

function toTraditionalObject(value) {
  if (Array.isArray(value)) return value.map(toTraditionalObject)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, toTraditionalObject(item)]))
  }
  return toTraditional(value)
}

const localizedDemoModes = computed(() => demoModes.map(localizedDemoMode))
const demoFlowDescription = computed(() => t('demoFlowDescription'))

const selectedDemoMode = computed(() => {
  return localizedDemoModes.value.find((item) => item.id === selectedDemoModeId.value) || localizedDemoModes.value[0]
})

const isFdhMode = computed(() => selectedDemoModeId.value === 'fdh')

const activeApplicationTypes = computed(() => {
  const types = isFdhMode.value ? fdhApplicationTypes : studentApplicationTypes
  return types.map(localizedApplicationType)
})

const activeScenarios = computed(() => {
  return isFdhMode.value ? fdhScenarios : studentScenarios
})

const activeWorkflowLabel = computed(() => {
  return isFdhMode.value ? t('workflowFdh') : t('workflowStudent')
})

const uploadHelperText = computed(() => {
  return isFdhMode.value ? t('uploadHelperFdh') : t('uploadHelperStudent')
})

const checklistDescription = computed(() => {
  return isFdhMode.value ? t('checklistDescriptionFdh') : t('checklistDescriptionStudent')
})

const selectedApplicationType = computed(() => {
  return activeApplicationTypes.value.find((item) => item.id === selectedApplicationTypeId.value) || activeApplicationTypes.value[0]
})

const selectedScenario = computed(() => {
  return activeScenarios.value.find((item) => item.id === selectedScenarioId.value) || activeScenarios.value[0]
})

const checklistPreview = computed(() => {
  return buildActiveReviewResult().materials.map(localizedMaterial)
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
  return applyFieldAdjudications(
    reviewResult.value?.fields || [],
    fieldAdjudications.value,
    { applicationTypeId: reviewResult.value?.applicationTypeId }
  )
})

const reviewableFieldRows = computed(() => reviewableFields(fieldRows.value))
const localizedReviewableFieldRows = computed(() => reviewableFieldRows.value.map(localizedField))

const displayReviewResult = computed(() => {
  if (!reviewResult.value) return null
  return localizeReviewResult(withDerivedDecision(reviewResult.value, reviewableFieldRows.value))
})

const displayFieldStats = computed(() => {
  return deriveFieldStats(reviewableFieldRows.value)
})

const filteredFields = computed(() => {
  const rows = localizedReviewableFieldRows.value
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
  if (!field) return t('unrecognised')
  return localizedTextValue(field.suggestedValue || field.normalizedValue || t('unrecognised'))
}

const blockingFindings = computed(() => {
  const result = displayReviewResult.value
  if (!result) return []
  const materialFindings = result.materials
    .filter((item) => item.blocking && (item.status === 'fail' || item.status === 'review'))
    .map((item) => ({
      id: `material:${item.id}`,
      status: item.status,
      title: item.status === 'fail'
        ? `${t('failMissingCore')}: ${item.shortName}`
        : `${t('materialNeedsReview')}: ${item.shortName}`,
      text: item.statusText,
      source: `${item.shortName} · ${item.templateId}`
    }))

  const fieldFindings = localizedReviewableFieldRows.value
    .filter((field) => field.blocking && (field.status === 'fail' || field.status === 'review'))
    .map((field) => ({
      id: `field:${field.key}`,
      status: field.status,
      title: field.label,
      text: field.issue || field.rule,
      source: field.sources.length
        ? field.sources.map((source) => `${source.documentName} · ${source.section} · ${source.fieldName}`).join('；')
        : t('noFieldEvidence')
    }))

  return localizedFindings([...materialFindings, ...fieldFindings])
})

const nonBlockingMaterialHints = computed(() => {
  const result = displayReviewResult.value
  if (!result) return []
  return result.materials.filter((item) => !item.blocking && item.status === 'warn')
})

const recognizedMaterials = computed(() => {
  const result = displayReviewResult.value
  if (!result) return []
  const uploadedByMaterial = new Map((result.uploadedFiles || []).map((file) => [file.materialId, file]))
  return (result.materials || [])
    .filter((material) => material.uploaded || uploadedByMaterial.has(material.id))
    .map((material) => {
      const upload = uploadedByMaterial.get(material.id)
      return {
        id: material.id,
        shortName: material.shortName,
        templateId: upload?.templateId || material.templateId || upload?.footerId || '',
        pages: upload?.pages || material.expectedPages || '',
        status: material.status,
        statusText: material.statusText,
        uploaded: Boolean(material.uploaded),
        scopeText: material.scopeText
      }
    })
})

const reviewSourcePages = computed(() => {
  const result = displayReviewResult.value
  if (!result) return []
  const backendPages = Array.isArray(result.reviewPages) ? result.reviewPages : []
  if (backendPages.length) {
    return backendPages.map((page) => ({
      key: reviewPageKey(page),
      materialId: page.materialId || '',
      documentName: page.documentName || page.materialId || t('documentFallback'),
      filename: page.filename || '',
      pageNo: Number(page.pageNo) || 1,
      title: page.title || pageLabel(Number(page.pageNo) || 1),
      imageDataUrl: page.imageDataUrl || '',
      imageWidth: Number(page.imageWidth) || 0,
      imageHeight: Number(page.imageHeight) || 0,
      fields: []
    }))
  }
  return fallbackReviewPages(result)
})

const fieldSourceRows = computed(() => {
  const rows = []
  for (const field of nonEmploymentFields.value) {
    ;(field.sources || []).forEach((source, index) => {
      const key = fieldSourceKey(field, source, index)
      rows.push({
        key,
        field,
        source,
        index,
        pageKey: sourcePageKey(source),
        bbox: sourceBbox(source),
        locatorConfidence: locatorConfidence(source),
        confidence: sourceConfidence(source),
        status: field.status
      })
    })
  }
  return rows
})

const documentFieldSourceRows = computed(() => {
  const rows = []
  const groups = displayReviewResult.value?.documentFieldGroups || []
  for (const group of groups) {
    for (const page of group.pages || []) {
      for (const item of page.fields || []) {
        const bbox = sourceBbox(item)
        const key = documentFieldKey(group, page, item)
        const source = {
          documentName: group.materialName || group.materialId || '',
          filename: item.filename || '',
          section: `第 ${Number(item.pageNo) || Number(page.pageNo) || 1} 页 · ${page.title || ''}`,
          fieldName: item.label || '',
          value: item.value || '',
          confidence: item.confidence || 0,
          materialId: group.materialId || '',
          pageNo: Number(item.pageNo) || Number(page.pageNo) || 1,
          imageWidth: item.imageWidth || 0,
          imageHeight: item.imageHeight || 0,
          bbox,
          locatorConfidence: item.locatorConfidence || 0
        }
        rows.push({
          key,
          field: {
            key,
            label: item.label || '',
            status: item.status || 'pass'
          },
          source,
          pageKey: sourcePageKey(source),
          bbox,
          locatorConfidence: locatorConfidence(source),
          confidence: sourceConfidence(source),
          status: item.status || 'pass',
          documentField: true
        })
      }
    }
  }
  return rows
})

const locatorSourceRows = computed(() => [
  ...fieldSourceRows.value,
  ...documentFieldSourceRows.value
])

const selectedFieldSource = computed(() => {
  return locatorSourceRows.value.find((row) => row.key === selectedFieldSourceKey.value) || null
})

const selectedLocatorText = computed(() => {
  const selected = selectedFieldSource.value
  if (!selected) return t('currentLocatorEmpty')
  const pageText = selected.source.pageNo ? pageLabel(selected.source.pageNo) : ''
  return `${t('currentLocator')}: ${selected.field.label} · ${selected.source.documentName || t('documentFallback')} ${pageText}`
})

function fallbackReviewPages(result) {
  const groups = result?.documentFieldGroups || []
  return groups.flatMap((group) => (group.pages || []).map((page) => ({
    key: `${group.materialId || group.materialName}:mock:${page.pageNo}`,
    materialId: group.materialId || '',
    documentName: group.materialName || group.materialId || t('documentFallback'),
    filename: '',
    pageNo: Number(page.pageNo) || 1,
    title: page.title || pageLabel(Number(page.pageNo) || 1),
    imageDataUrl: '',
    imageWidth: 0,
    imageHeight: 0,
    fields: page.fields || []
  })))
}

function reviewPageKey(page) {
  return `${page.filename || page.materialId || page.documentName}:page:${Number(page.pageNo) || 1}`
}

function sourcePageKey(source) {
  const pageNo = Number(source?.pageNo) || sourcePageNo(source)
  const matched = reviewSourcePages.value.find((page) => {
    const samePage = pageNo > 0 ? page.pageNo === pageNo : true
    return samePage && sourceMatchesPage(source, page)
  })
  if (matched) return matched.key
  return pageNo > 0 ? `${source?.filename || source?.materialId || source?.documentName}:page:${pageNo}` : ''
}

function sourceMatchesPage(source, page) {
  const sourceDocument = String(source?.documentName || '')
  const pageDocument = String(page.documentName || '')
  return Boolean(
    (source?.filename && page.filename === source.filename)
      || (source?.materialId && page.materialId === source.materialId)
      || (sourceDocument && pageDocument === sourceDocument)
      || (sourceDocument && pageDocument.includes(sourceDocument))
      || (pageDocument && sourceDocument.includes(pageDocument))
  )
}

function sourcePageNo(source) {
  const section = String(source?.section || '')
  const match = section.match(/第\s*(\d+)\s*页|page[_\s-]*(\d+)/i)
  return match ? Number(match[1] || match[2]) || 0 : 0
}

function pageLabel(pageNo) {
  return currentLanguage.value === 'zh' ? t('page', { page: pageNo }) : `${t('page')} ${pageNo}`
}

function fieldSourceKey(field, source, index) {
  return [
    field?.key || 'field',
    source?.filename || source?.documentName || 'source',
    source?.section || '',
    source?.fieldName || '',
    index
  ].join('|')
}

function pageSourceRows(page) {
  const selected = selectedFieldSource.value
  return selected?.pageKey === page.key && selected.bbox.length === 4 ? [selected] : []
}

function documentFieldKey(group, page, item) {
  return [
    group?.materialId || group?.materialName || 'material',
    Number(page?.pageNo) || 1,
    item?.label || 'field',
    item?.value || ''
  ].join('|')
}

function normalizedMatchText(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[\s:_/\\().,，。；;：·-]+/g, '')
}

function documentFieldSourceRow(group, page, item) {
  const key = documentFieldKey(group, page, item)
  return documentFieldSourceRows.value.find((row) => row.key === key) || null
}

function documentFieldIsActive(group, page, item) {
  const row = documentFieldSourceRow(group, page, item)
  return Boolean(row && selectedFieldSourceKey.value === row.key)
}

function selectDocumentField(group, page, item) {
  const row = documentFieldSourceRow(group, page, item)
  if (row) {
    selectFieldSourceRow(row)
    return
  }
  selectedFieldSourceKey.value = ''
  const pageKey = sourcePageKey({
    materialId: group?.materialId || '',
    documentName: group?.materialName || '',
    pageNo: Number(page?.pageNo) || 1
  })
  if (pageKey) scrollToReviewPage(pageKey)
}

function sourceBbox(source) {
  if (!Array.isArray(source?.bbox) || source.bbox.length !== 4) return []
  const values = source.bbox.map((value) => Number(value))
  return values.every((value) => Number.isFinite(value)) && values[2] > values[0] && values[3] > values[1]
    ? values
    : []
}

function sourceConfidence(source) {
  const confidence = Number(source?.confidence)
  return Number.isFinite(confidence) ? Math.max(0, Math.min(100, Math.round(confidence))) : 0
}

function locatorConfidence(source) {
  const confidence = Number(source?.locatorConfidence)
  return Number.isFinite(confidence) ? Math.max(0, Math.min(100, Math.round(confidence))) : 0
}

function averageFieldConfidence(field) {
  const sources = field?.sources || []
  if (!sources.length) return 0
  const total = sources.reduce((sum, source) => sum + sourceConfidence(source), 0)
  return Math.round(total / sources.length)
}

function bboxStyle(row, page) {
  const [left, top, right, bottom] = row.bbox
  const maxCoord = Math.max(Math.abs(left), Math.abs(top), Math.abs(right), Math.abs(bottom))
  if (maxCoord <= 1) {
    return {
      left: `${left * 100}%`,
      top: `${top * 100}%`,
      width: `${(right - left) * 100}%`,
      height: `${(bottom - top) * 100}%`
    }
  }
  const width = page.imageWidth || 1
  const height = page.imageHeight || 1
  return {
    left: `${(left / width) * 100}%`,
    top: `${(top / height) * 100}%`,
    width: `${((right - left) / width) * 100}%`,
    height: `${((bottom - top) / height) * 100}%`
  }
}

function sourceDomId(key) {
  return `field-source-${safeDomId(key)}`
}

function pageDomId(key) {
  return `review-page-${safeDomId(key)}`
}

function safeDomId(value) {
  return String(value || 'empty').replace(/[^a-zA-Z0-9_-]+/g, '-')
}

function selectFieldSource(field, source, index) {
  const key = fieldSourceKey(field, source, index)
  const row = fieldSourceRows.value.find((item) => item.key === key) || {
    key,
    field,
    source,
    pageKey: sourcePageKey(source),
    bbox: sourceBbox(source)
  }
  selectFieldSourceRow(row)
}

function selectFieldSourceRow(row) {
  selectedFieldSourceKey.value = row.key
  nextTick(() => {
    const sourceEl = document.getElementById(sourceDomId(row.key))
    if (sourceEl) {
      sourceEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
    const pageKey = row.pageKey || sourcePageKey(row.source)
    if (pageKey) {
      document.getElementById(pageDomId(pageKey))?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

function selectFieldDefaultSource(field) {
  const source = field?.sources?.[0]
  if (source) {
    selectFieldSource(field, source, 0)
  }
}

function scrollToReviewPage(pageKey) {
  nextTick(() => {
    document.getElementById(pageDomId(pageKey))?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

const reviewJsonPayload = computed(() => {
  if (!reviewResult.value) return {}
  const result = withDerivedDecision(reviewResult.value, reviewableFieldRows.value)
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
    reviewPages: (result.reviewPages || []).map((page) => ({
      materialId: page.materialId,
      documentName: page.documentName,
      filename: page.filename,
      pageNo: page.pageNo,
      title: page.title,
      imageWidth: page.imageWidth,
      imageHeight: page.imageHeight,
      hasImage: Boolean(page.imageDataUrl)
    })),
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
        materialId: source.materialId || '',
        pageNo: source.pageNo || 0,
        imageWidth: source.imageWidth || 0,
        imageHeight: source.imageHeight || 0,
        bbox: source.bbox || [],
        locatorConfidence: source.locatorConfidence || 0,
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
  return localizedTemplate(buildVerificationTemplate({
    ...result,
    fields: localizedReviewableFieldRows.value
  }, {
    applicationTypeLabel: selectedApplicationType.value.label,
    workflowLabel: activeWorkflowLabel.value
  }))
})

const templateStatusLegend = computed(() => localizedTemplateStatusLegend())
const progressMessage = computed(() => localizedJobStatusMessage(jobStatus.value?.message) || t('progressFallback'))

function scrollMainPageToTop() {
  nextTick(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
  })
}

function localizedJobStatusMessage(message) {
  const text = String(message || '').trim()
  if (!text) return ''
  if (currentLanguage.value === 'zh') return toTraditional(text)
  return translateJobStatusMessage(text)
}

function translateJobStatusMessage(message) {
  const dynamicRules = [
    {
      pattern: /^正在渲染并识别材料类型：(.+)$/,
      render: ([filename]) => `Rendering and recognising document type: ${filename}`
    },
    {
      pattern: /^正在准备字段提取范围：(.+)$/,
      render: ([filename]) => `Preparing field extraction scope: ${filename}`
    },
    {
      pattern: /^正在按已识别模板执行字段提取：(.+)$/,
      render: ([filename]) => `Extracting fields using recognised template: ${filename}`
    },
    {
      pattern: /^正在调用本地 LLM 做结构化提取：(.+)$/,
      render: ([filename]) => `Calling local LLM for structured extraction: ${filename}`
    },
    {
      pattern: /^正在识别第 (\d+) 页：(.+)$/,
      render: ([page, filename]) => `Recognising page ${page}: ${filename}`
    },
    {
      pattern: /^正在识别第 (\d+) 页第 (\d+) 次请求：(.+)$/,
      render: ([page, attempt, filename]) => `Recognising page ${page}, request ${attempt}: ${filename}`
    },
    {
      pattern: /^已完成 (\d+) \/ (\d+) 份材料识别。$/,
      render: ([processed, total]) => `Completed recognition for ${processed} / ${total} documents.`
    }
  ]
  for (const rule of dynamicRules) {
    const match = message.match(rule.pattern)
    if (match) return rule.render(match.slice(1))
  }
  return {
    '材料审批任务已创建，等待开始处理。': 'The document review task has been created and is waiting to start.',
    '正在汇总材料清单、标准化字段和跨文件规则结论。': 'Summarising the document checklist, standardised fields and cross-document rule findings.',
    '材料审批识别完成。': 'Document review recognition completed.',
    '材料审批任务已取消。': 'The document review task has been cancelled.',
    '材料审批任务失败。': 'The document review task failed.'
  }[message] || message
}

function setResultView(view) {
  resultView.value = view
  scrollMainPageToTop()
}

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
      documentName: t('pendingRecognition'),
      filename: entry.file.name,
      pages: t('pendingRecognition'),
      footerId: t('pendingRecognition'),
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

async function startRecognition() {
  if (processing.value) return
  if (uploadedFileObjects.value.length) {
    await startBackendRecognition()
    return
  }
  apiError.value = t('uploadFirstError')
  reviewResult.value = null
  jobStatus.value = null
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
    message: t('jobUploadMessage'),
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
      throw new Error(completed.error || completed.message || t('jobIncomplete'))
    }
    const result = completed.result
    reviewResult.value = result
    setResultView('recognition')
    verificationConclusion.value = null
    verificationError.value = ''
    uploadedFiles.value = result?.uploadedFiles || uploadedFiles.value
    startVerificationConclusion(result)
  } catch (error) {
    apiError.value = localizedTextValue(error?.message || t('backendFailed'))
    jobStatus.value = null
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
  if (processing.value && Number.isFinite(progress) && !['failed', 'canceled'].includes(jobStatus.value?.status)) {
    return `${Math.max(0, Math.min(100, Math.round(progress)))}%`
  }
  return t('pendingRecognition')
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
  throw new Error(t('jobStillProcessing'))
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
      if (currentLanguage.value === 'zh') {
        throw new Error('请求超时，请检查后端服务后重试。')
      }
      throw new Error(t('requestTimeout'))
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
    pass: t('statusPass'),
    fail: t('statusFail'),
    review: t('statusReview'),
    warn: t('statusWarn'),
    muted: t('statusMuted')
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
    PASS: t('decisionPass'),
    REVIEW: t('decisionReview'),
    FAIL: t('decisionFail')
  }[decision] || decision
}

function materialRequirementLabel(material) {
  if (material.requirementLabel) return material.requirementLabel
  if (!material.applicable) return t('notApplicable')
  if (material.core) return t('coreRequired')
  if (material.conditional) return t('conditionallyRequired')
  return t('officialRequired')
}

async function openVerificationPage() {
  if (!reviewResult.value) return
  setResultView('verification')
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
  const fields = applyFieldAdjudications(
    result.fields || [],
    localFieldAdjudications(result),
    { applicationTypeId: result.applicationTypeId }
  )
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
        <p class="eyebrow">{{ t('eyebrow') }}</p>
        <h1>{{ t('title') }}</h1>
        <p class="header-copy">
          {{ demoFlowDescription }}
        </p>
      </div>

      <div class="header-controls">
        <div class="language-switcher" role="group" :aria-label="t('language')">
          <button
            v-for="language in LANGUAGES"
            :key="language.id"
            type="button"
            :class="{ active: currentLanguage === language.id }"
            @click="setLanguage(language.id)"
          >
            {{ language.label }}
          </button>
        </div>

        <div class="workflow-tabs" role="tablist" :aria-label="t('caseSwitch')">
          <button
            v-for="mode in localizedDemoModes"
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
      </div>

    </header>

    <section v-if="!reviewResult" class="upload-stage">
      <div class="review-upload-panel">
        <section class="case-section" aria-labelledby="case-title">
          <div class="section-heading">
            <div>
              <h2 id="case-title">{{ isFdhMode ? t('caseTitleFdh') : t('caseTitleStudent') }}</h2>
              <p>{{ isFdhMode ? t('caseHintFdh') : t('caseHintStudent') }}</p>
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
              <h2 id="checklist-title">{{ isFdhMode ? t('checklistTitleFdh') : t('checklistTitleStudent') }}</h2>
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
              <h2 id="upload-title">{{ t('uploadTitle') }}</h2>
              <p>{{ isFdhMode ? t('uploadHintFdh') : t('uploadHintStudent') }}</p>
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
              <strong>{{ t('dropzoneTitle') }}</strong>
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
                  <strong>{{ t('selectedFiles') }}</strong>
                  <span>{{ uploadedFiles.length }} {{ t('filesUnit') }}{{ uploadedFiles.length > 3 ? ` · ${t('listScrollable')}` : '' }}</span>
                </div>
                <button
                  v-if="uploadedFiles.length"
                  class="clear-files-button"
                  type="button"
                  :disabled="processing"
                  @click="clearUploadedFiles"
                >
                  {{ t('clearAll') }}
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
                    :aria-label="`${t('clearAll')} ${file.filename}`"
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
                      {{ fileSizeLabel(file.size) || t('unknownSize') }} · {{ uploadedFileProgressLabel() }}
                    </span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-panel">{{ t('emptyUpload') }}</div>
            </div>
          </div>

          <div v-if="processing" class="progress-box" aria-live="polite">
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: uploadedFileObjects.length ? `${jobStatus?.progress || 8}%` : '72%' }"></div>
            </div>
            <div class="progress-meta">
              <span>{{ progressMessage }}</span>
              <strong>{{ `${jobStatus?.progress || 0}%` }}</strong>
            </div>
          </div>

          <div v-if="apiError" class="api-error" role="alert">
            {{ apiError }}
          </div>

          <button class="primary-action" type="button" :disabled="processing" @click="startRecognition">
            {{ processing ? t('recognizing') : t('startRecognition') }}
          </button>
        </section>
      </div>
    </section>

    <section v-else class="result-stage">
      <div class="result-toolbar">
        <div class="file-summary">
          <span class="file-label">{{ selectedDemoMode.shortLabel }}</span>
          <strong>{{ selectedApplicationType.label }}</strong>
          <span>{{ reviewResult.uploadedFiles.length }} {{ t('uploadedMaterials') }}</span>
        </div>
        <div class="result-view-controls">
          <div class="result-tabs" role="tablist" :aria-label="t('resultTabs')">
            <button
              type="button"
              :class="{ active: resultView === 'recognition' }"
              @click="setResultView('recognition')"
            >
              {{ t('recognitionResult') }}
            </button>
            <button
              type="button"
              :class="{ active: resultView === 'json' }"
              @click="setResultView('json')"
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
              {{ t('openVerification') }}
            </button>
            <button v-else class="secondary-action" type="button" @click="setResultView('recognition')">{{ t('backToRecognition') }}</button>
            <button class="secondary-action" type="button" @click="resetDemo">{{ t('reupload') }}</button>
          </div>
        </div>
      </div>

      <div v-if="resultView === 'recognition'" class="review-workspace">
        <section class="recognized-materials-panel">
          <div class="recognized-materials-heading">
            <h2>{{ t('recognizedMaterials') }}</h2>
            <p>{{ recognizedMaterials.length }} {{ t('recognizedMaterialsCount') }}</p>
          </div>
          <div class="recognized-material-list">
            <article
              v-for="material in recognizedMaterials"
              :key="material.id"
              class="recognized-material-card"
              :class="[`status-${material.status}`, { uploaded: material.uploaded }]"
            >
              <strong>{{ material.shortName }}</strong>
              <span>{{ material.templateId || material.scopeText }}</span>
              <small>{{ material.pages ? `${material.pages} ${currentLanguage === 'zh' ? '頁' : 'pages'}` : t('pagesPending') }} · {{ material.statusText }}</small>
            </article>
          </div>
        </section>

        <section class="source-review-panel">
          <div class="panel-heading source-review-heading">
            <div>
              <h2>{{ t('sourcePagesTitle') }}</h2>
              <p>{{ t('sourcePagesHint') }}</p>
            </div>
            <span class="locator-summary">{{ selectedLocatorText }}</span>
          </div>
          <div class="source-review-body">
            <nav class="source-page-thumbs" :aria-label="t('sourceThumbs')">
              <button
                v-for="page in reviewSourcePages"
                :key="page.key"
                type="button"
                class="source-page-thumb"
                :class="{ active: selectedFieldSource?.pageKey === page.key }"
                @click="scrollToReviewPage(page.key)"
              >
                <strong>{{ page.documentName }}</strong>
                <span>{{ pageLabel(page.pageNo) }}</span>
              </button>
            </nav>
            <div class="source-page-scroll">
              <article
                v-for="page in reviewSourcePages"
                :id="pageDomId(page.key)"
                :key="page.key"
                class="source-page-sheet"
                :class="{ active: selectedFieldSource?.pageKey === page.key }"
              >
                <header>
                  <strong>{{ page.documentName }}</strong>
                  <span>{{ page.filename || page.title }} · {{ pageLabel(page.pageNo) }}</span>
                </header>
                <div v-if="page.imageDataUrl" class="source-page-image">
                  <img :src="page.imageDataUrl" :alt="`${page.documentName} ${pageLabel(page.pageNo)}`">
                  <button
                    v-for="row in pageSourceRows(page)"
                    :id="sourceDomId(row.key)"
                    :key="row.key"
                    type="button"
                    class="source-highlight"
                    :class="[row.status, { active: selectedFieldSourceKey === row.key }]"
                    :style="bboxStyle(row, page)"
                    :title="`${row.field.label}：${row.source.value}`"
                    @click="selectedFieldSourceKey = row.key"
                  />
                </div>
                <div v-else class="source-page-fallback">
                  <div
                    v-for="item in page.fields"
                    :key="`${page.key}:${item.label}`"
                    class="source-page-field-row"
                    :class="item.status"
                  >
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value || t('unrecognised') }}</strong>
                  </div>
                </div>
              </article>
              <div v-if="!reviewSourcePages.length" class="source-page-empty">
                {{ t('noSourcePages') }}
              </div>
            </div>
          </div>
        </section>

        <section class="review-main">
          <section v-if="displayReviewResult.documentFieldGroups?.length" class="document-fields-panel locator-document-fields">
            <div class="panel-heading">
              <div>
                <h2>{{ t('documentFieldsTitle') }}</h2>
                <p>{{ t('documentFieldsHint') }}</p>
              </div>
            </div>
            <div class="document-group-list">
              <article
                v-for="group in displayReviewResult.documentFieldGroups"
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
                    <h3>{{ pageLabel(page.pageNo) }} · {{ page.title }}</h3>
                    <div class="document-field-table">
                      <div class="document-field-row document-field-head">
                        <span>{{ t('field') }}</span>
                        <span>{{ t('filledOrRecognisedValue') }}</span>
                        <span>{{ t('status') }}</span>
                      </div>
                      <button
                        v-for="item in page.fields"
                        :key="documentFieldKey(group, page, item)"
                        type="button"
                        class="document-field-row locator-document-field-row"
                        :class="[item.status, { active: documentFieldIsActive(group, page, item) }]"
                        @click="selectDocumentField(group, page, item)"
                      >
                        <span>{{ item.label }}</span>
                        <strong>{{ item.value || t('unrecognised') }}</strong>
                        <span class="document-field-status">
                          <small v-if="sourceConfidence(item)">{{ t('valueConfidence') }} {{ sourceConfidence(item) }}%</small>
                          <span class="status-badge" :class="item.status">{{ statusLabel(item.status) }}</span>
                        </span>
                      </button>
                    </div>
                  </section>
                </div>
              </article>
            </div>
          </section>

          <section class="fields-panel">
            <div class="panel-heading fields-heading">
              <div>
                <h2>{{ t('normalizedFieldsTitle') }}</h2>
                <p>{{ t('normalizedFieldsHint') }}</p>
              </div>
              <div class="field-metrics" :aria-label="t('fieldStats')">
                <span><strong>{{ displayFieldStats.total }}</strong>{{ t('allFields') }}</span>
                <span><strong>{{ displayFieldStats.pass }}</strong>{{ t('passed') }}</span>
                <span><strong>{{ displayFieldStats.fail }}</strong>{{ t('issues') }}</span>
                <span><strong>{{ displayFieldStats.review }}</strong>{{ t('pendingReview') }}</span>
              </div>
            </div>

            <div class="filter-row" role="tablist" :aria-label="t('fieldFilter')">
              <button type="button" :class="{ active: fieldFilter === 'all' }" @click="fieldFilter = 'all'">{{ t('allFields') }}</button>
              <button type="button" :class="{ active: fieldFilter === 'issues' }" @click="fieldFilter = 'issues'">{{ t('onlyIssues') }}</button>
              <button type="button" :class="{ active: fieldFilter === 'review' }" @click="fieldFilter = 'review'">{{ t('onlyReview') }}</button>
              <button type="button" :class="{ active: fieldFilter === 'required' }" @click="fieldFilter = 'required'">{{ t('onlyRequired') }}</button>
            </div>

            <div class="field-card-list">
              <template v-for="field in nonEmploymentFields" :key="field.key">
                <article
                  class="standard-field-card locator-field-card"
                  :class="[field.status, { active: selectedFieldSource?.field?.key === field.key }]"
                >
                  <header class="field-card-header locator-field-header" @click="selectFieldDefaultSource(field)">
                    <div>
                      <span>{{ field.category }}</span>
                      <h3>{{ field.label }}</h3>
                      <code>{{ field.key }}</code>
                      <small class="field-confidence">{{ t('overallConfidence') }} {{ averageFieldConfidence(field) || '-' }}% · {{ field.sources.length }} {{ t('sources') }}</small>
                    </div>
                    <div class="field-card-actions">
                      <span v-if="field.required" class="required-pill">{{ t('required') }}</span>
                      <span class="status-badge" :class="field.status">{{ statusLabel(field.status) }}</span>
                    </div>
                  </header>

                  <div class="source-evidence-list">
                    <button
                      v-for="(source, index) in field.sources"
                      :key="fieldSourceKey(field, source, index)"
                      type="button"
                      class="source-evidence-row"
                      :class="{ active: selectedFieldSourceKey === fieldSourceKey(field, source, index) }"
                      @click="selectFieldSource(field, source, index)"
                    >
                      <span class="source-evidence-name">
                        <strong>{{ source.documentName }} · {{ source.section }}</strong>
                        <small>{{ source.fieldName }}</small>
                      </span>
                      <strong class="source-evidence-value">
                        <template
                          v-for="(segment, segmentIndex) in sourceValueSegments(field, source)"
                          :key="`${segmentIndex}:${segment.text}:${segment.diff}`"
                        >
                          <mark v-if="segment.diff" class="value-diff-char">{{ segment.text }}</mark>
                          <span v-else>{{ segment.text }}</span>
                        </template>
                      </strong>
                      <span class="source-confidence-pill">{{ t('valueConfidence') }} {{ sourceConfidence(source) }}%</span>
                      <span class="source-confidence-pill" :class="{ muted: !locatorConfidence(source) }">
                        {{ locatorConfidence(source) ? `${t('locatorConfidence')} ${locatorConfidence(source)}%` : t('notLocated') }}
                      </span>
                    </button>
                    <div v-if="!field.sources.length" class="source-evidence-empty">
                      {{ t('noEvidence') }}
                    </div>
                  </div>

                  <div class="field-value-panel compact">
                    <div>
                      <span>{{ field.correctionApplied ? t('recommendedValue') : t('normalizedResult') }}</span>
                      <strong>{{ field.suggestedValue || field.normalizedValue }}</strong>
                      <small v-if="field.correctionApplied" class="field-original-value">
                        {{ t('originalNormalizedResult') }}: {{ field.rawNormalizedValue }}
                      </small>
                    </div>
                    <div v-if="field.correctionApplied" class="issue-box review">
                      {{ field.suggestionReason }}
                    </div>
                    <div v-else-if="field.issue" class="issue-box" :class="field.status">
                      {{ field.issue }}
                    </div>
                  </div>
                </article>

                <section v-if="isEmploymentPeriodAnchorField(field) && employmentPeriods.length" class="employment-period-group">
                  <h3 class="employment-period-title">{{ t('employmentExperience') }}</h3>
                  <article v-for="period in employmentPeriods" :key="period.n" class="employment-period-card">
                    <div class="employment-period-header">{{ t('employer') }} {{ period.n }}</div>
                    <dl class="employment-period-body">
                      <div v-if="period.nameField" class="employment-period-row">
                        <dt>{{ t('employer') }} {{ period.n }} {{ t('employerName') }}</dt>
                        <dd>{{ employmentValue(period.nameField) }}</dd>
                      </div>
                      <div v-if="period.addressField" class="employment-period-row">
                        <dt>{{ t('address') }}</dt>
                        <dd>{{ employmentValue(period.addressField) }}</dd>
                      </div>
                      <div v-if="period.periodFromField || period.periodToField" class="employment-period-row">
                        <dt>{{ t('employmentPeriod') }}</dt>
                        <dd>{{ t('from') }} {{ employmentValue(period.periodFromField) }} {{ t('to') }} {{ employmentValue(period.periodToField) }}</dd>
                      </div>
                    </dl>
                  </article>
                </section>
              </template>
            </div>
          </section>

          <section class="findings-panel compact-findings">
            <div class="panel-heading">
              <h2>{{ t('findingsTitle') }}</h2>
              <p>{{ t('findingsHint') }}</p>
            </div>
            <div v-if="blockingFindings.length" class="finding-list">
              <article v-for="finding in blockingFindings" :key="finding.id" class="finding-item" :class="finding.status">
                <span class="status-badge" :class="finding.status">{{ statusLabel(finding.status) }}</span>
                <div>
                  <strong>{{ finding.title }}</strong>
                  <p>{{ finding.text }}</p>
                  <small>{{ t('source') }}: {{ finding.source }}</small>
                </div>
              </article>
            </div>
            <div v-else class="finding-pass">
              {{ t('noBlockingFindings') }}
            </div>
            <div v-if="nonBlockingMaterialHints.length" class="nonblocking-box">
              <strong>{{ t('nonBlockingHint') }}</strong>
              <span>
                {{ nonBlockingMaterialHints.map((item) => `${item.shortName}: ${item.statusText}`).join('；') }}
              </span>
            </div>
          </section>
        </section>
      </div>
      <section v-else-if="resultView === 'json'" class="json-result-panel">
        <div class="panel-heading">
          <div>
            <h2>JSON</h2>
            <p>{{ t('jsonDescription') }}</p>
          </div>
        </div>
        <pre class="json-preview">{{ reviewJsonPreview }}</pre>
      </section>
      <section v-else class="verification-page">
        <div class="panel-heading">
          <div>
            <h2>{{ t('verificationPage') }}</h2>
            <p>{{ demoFlowDescription }}</p>
          </div>
          <span class="status-chip" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
            {{ displayReviewResult.decision }} · {{ decisionLabel(displayReviewResult.decision) }}
          </span>
        </div>

        <div v-if="verificationLoading" class="verification-loading" aria-live="polite">
          {{ t('generatingMinutes') }}
        </div>
        <div v-if="verificationError" class="verification-note" role="status">
          {{ verificationError }}
        </div>
        <div v-if="verificationTemplate" class="verification-output">
          <div class="verification-summary-card" :class="`decision-${displayReviewResult.decision.toLowerCase()}`">
            <span>{{ t('overallConclusion') }}</span>
            <strong>{{ displayReviewResult.decision }} - {{ displayReviewResult.decisionText }}</strong>
            <p>{{ verificationTemplate.summaryText }}</p>
            <ul class="overall-bullet-list">
              <li v-for="item in verificationTemplate.overallBullets" :key="item.label">
                <span>{{ item.label }}：</span>
                <strong>{{ item.value }}</strong>
              </li>
            </ul>
          </div>

          <div class="template-status-legend" :aria-label="t('fieldStatusLegend')">
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
              <h3>{{ t('materialVerification') }}</h3>
              <p>{{ t('materialVerificationHint') }}</p>
            </div>
            <table class="material-template-table">
              <thead>
                <tr>
                  <th>{{ t('material') }}</th>
                  <th>{{ t('templateOrFooter') }}</th>
                  <th>{{ t('verificationStatus') }}</th>
                  <th>{{ t('remarks') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="material in verificationTemplate.materialRows" :key="material.id">
                  <td>{{ material.no }}. {{ material.shortName }}</td>
                  <td>{{ material.templateId }}</td>
                  <td>
                    <span class="status-badge" :class="material.status">{{ material.statusLabel }}</span>
                  </td>
                  <td>{{ material.issue || t('includedInCompleteness') }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section v-for="section in verificationTemplate.sections" :key="section.id" class="verification-section">
            <div class="panel-heading">
              <h3>{{ section.title }}</h3>
              <p>{{ t('sectionHint') }}</p>
            </div>
            <table class="minutes-template-table">
              <thead>
                <tr>
                  <th>{{ t('field') }}</th>
                  <th>{{ t('fillValue') }}</th>
                  <th>{{ t('status') }}</th>
                  <th>{{ t('sourcesAndDraftRemarks') }}</th>
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
                      {{ t('normalized') }}: {{ fieldRow.normalizedValue }}
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
                        :class="{ 'without-crop': !isFdhMode || !source.snapshotDataUrl }"
                      >
                        <div
                          v-if="isFdhMode && source.snapshotDataUrl"
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
                          <small v-if="isFdhMode && !source.snapshotDataUrl" class="template-evidence-crop-missing">{{ t('cropMissing') }}</small>
                          <div class="template-evidence-value">
                            <span>{{ t('recognisedValue') }}</span>
                            <strong>{{ templateSourceValue(source) }}</strong>
                          </div>
                          <small>{{ t('confidence') }} {{ templateSourceConfidence(source) }}</small>
                        </div>
                      </article>
                    </div>
                    <small v-else class="template-empty-evidence">{{ t('noUsableEvidence') }}</small>
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
