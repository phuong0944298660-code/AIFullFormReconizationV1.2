export const applicationTypes = [
  {
    id: 'entry_visa',
    label: '入境签证',
    shortLabel: '入境签证',
    description: '从外国受聘来港家庭傭工签证。默认演示类别。',
    checklistKey: '入境签证'
  },
  {
    id: 'renewal',
    label: '于两年合约期届满后续约',
    shortLabel: '续约',
    description: '同一雇主两年合约届满后续约。',
    checklistKey: '于两年合约期届满后续约'
  },
  {
    id: 'remaining_period',
    label: '完成现有合约的余下期间',
    shortLabel: '余下期间',
    description: '完成现有合约余下期间的申请。',
    checklistKey: '完成现有合约的余下期间'
  },
  {
    id: 'change_employer',
    label: '转换雇主',
    shortLabel: '转换雇主',
    description: '与同一傭主续约或转换雇主相关申请。',
    checklistKey: '转换雇主'
  }
]

export const scenarios = [
  {
    id: 'pass',
    label: 'PASS：核心材料齐全',
    shortLabel: '核心材料齐全'
  },
  {
    id: 'missing_core',
    label: 'FAIL：缺核心材料',
    shortLabel: '缺核心材料'
  },
  {
    id: 'missing_required_field',
    label: 'FAIL：必填字段漏填',
    shortLabel: '必填漏填'
  },
  {
    id: 'field_mismatch',
    label: 'FAIL：跨文件明显不一致',
    shortLabel: '字段不一致'
  },
  {
    id: 'review_low_confidence',
    label: 'REVIEW：识别低置信 / 轻微差异',
    shortLabel: '待人工复核'
  }
]

export const materials = [
  {
    id: 'id988a',
    no: 1,
    name: '从外国受聘来港家庭傭工签证 / 延长逗留期限申请表',
    shortName: 'ID 988A',
    templateId: 'ID 988A (06/2024)',
    expectedPages: 5,
    applicability: ['entry_visa', 'renewal', 'remaining_period', 'change_employer']
  },
  {
    id: 'id988b',
    no: 2,
    name: '从外国聘用家庭傭工申请表',
    shortName: 'ID 988B',
    templateId: 'ID 988B (06/2024)',
    expectedPages: 4,
    applicability: ['entry_visa', 'renewal', 'change_employer']
  },
  {
    id: 'id407',
    no: 3,
    name: '新标准雇佣合约正本一份',
    shortName: 'ID 407',
    templateId: 'ID 407 (11/2016)',
    expectedPages: 4,
    applicability: ['entry_visa', 'renewal', 'change_employer'],
    note: '续约网上申请领取延长逗留标签时仍需提交合约正本以供查阅。'
  },
  {
    id: 'helperTravelOriginal',
    no: 4,
    name: '傭工的旅行证件正本',
    shortName: '旅行证件正本',
    templateId: '非固定模板',
    expectedPages: '按证件',
    applicability: ['renewal', 'remaining_period', 'change_employer']
  },
  {
    id: 'helperTravelCopy',
    no: 5,
    name: '傭工的旅行证件副本',
    shortName: '旅行证件副本',
    templateId: '非固定模板',
    expectedPages: '资料页',
    applicability: ['entry_visa', 'renewal', 'remaining_period', 'change_employer']
  },
  {
    id: 'helperHkid',
    no: 6,
    name: '傭工的香港身份证副本（如适用）',
    shortName: '傭工 HKID',
    templateId: 'HKID',
    expectedPages: '1-2 页',
    applicability: ['entry_visa', 'change_employer'],
    conditional: true
  },
  {
    id: 'employerId',
    no: 7,
    name: '雇主的香港永久性居民身份证 / 香港身份证 / 护照副本',
    shortName: '雇主身份证明',
    templateId: 'HKID / Passport',
    expectedPages: '1-2 页',
    applicability: ['entry_visa', 'change_employer']
  },
  {
    id: 'financialProof',
    no: 8,
    name: '雇主的经济状况证明（副本）',
    shortName: '经济状况证明',
    templateId: '税单 / 银行 / 薪金',
    expectedPages: '按证明',
    applicability: ['entry_visa', 'renewal', 'remaining_period', 'change_employer']
  },
  {
    id: 'addressProof',
    no: 9,
    name: '雇主的住址证明（副本）',
    shortName: '住址证明',
    templateId: '差饷 / 水电等',
    expectedPages: '按证明',
    applicability: ['entry_visa', 'renewal', 'remaining_period', 'change_employer']
  },
  {
    id: 'testimonial',
    no: 10,
    name: '傭工的荐书',
    shortName: '荐书',
    templateId: '信件',
    expectedPages: '按证明',
    applicability: ['entry_visa']
  },
  {
    id: 'releaseLetter',
    no: 11,
    name: '现时雇主发出的离职信（列明合约的届满 / 终止日期）',
    shortName: '离职信',
    templateId: '信件',
    expectedPages: '按证明',
    applicability: ['change_employer']
  },
  {
    id: 'continuousLetter',
    no: 12,
    name: '雇主继续聘用的确认信',
    shortName: '继续聘用确认信',
    templateId: '信件',
    expectedPages: '按证明',
    applicability: ['remaining_period']
  }
]

const uploadedByScenario = {
  pass: ['id988a', 'id988b', 'id407', 'helperTravelCopy', 'employerId'],
  missing_core: ['id988a', 'id988b', 'helperTravelCopy', 'employerId'],
  missing_required_field: ['id988a', 'id988b', 'id407', 'helperTravelCopy'],
  field_mismatch: ['id988a', 'id988b', 'id407', 'helperTravelCopy'],
  review_low_confidence: ['id988a', 'id988b', 'id407', 'helperTravelCopy']
}

const uploadedFileByMaterial = {
  id988a: { filename: 'ID988A_helper_application_06-2024.pdf', pages: 5, footerId: 'ID 988A (06/2024)' },
  id988b: { filename: 'ID988B_employer_application_06-2024.pdf', pages: 4, footerId: 'ID 988B (06/2024)' },
  id407: { filename: 'ID407_standard_contract_11-2016.pdf', pages: 4, footerId: 'ID 407 (11/2016)' },
  helperTravelCopy: { filename: 'passport_copy_helper_biodata.jpg', pages: 1, footerId: 'Passport biodata page' },
  employerId: { filename: 'employer_hkid_copy.jpg', pages: 1, footerId: 'HKID copy' }
}

export function buildUploadedFiles(scenarioId) {
  return (uploadedByScenario[scenarioId] || uploadedByScenario.pass)
    .map((materialId) => {
      const material = materials.find((item) => item.id === materialId)
      return {
        materialId,
        documentName: material?.shortName || materialId,
        ...uploadedFileByMaterial[materialId]
      }
    })
}

export function buildReviewResult(applicationTypeId, scenarioId) {
  const uploadedIds = new Set(uploadedByScenario[scenarioId] || uploadedByScenario.pass)
  const materialRows = materials.map((material) => buildMaterialRow(material, applicationTypeId, uploadedIds, scenarioId))
  const fields = buildFields(scenarioId, uploadedIds, applicationTypeId)
  const blockingMaterialFindings = materialRows.filter((row) => row.blocking && row.status === 'fail')
  const blockingFieldFindings = fields.filter((field) => field.blocking && field.status === 'fail')
  const reviewFindings = fields.filter((field) => field.blocking && field.status === 'review')
  const decision = blockingMaterialFindings.length || blockingFieldFindings.length
    ? 'FAIL'
    : reviewFindings.length
      ? 'REVIEW'
      : 'PASS'

  return {
    applicationTypeId,
    scenarioId,
    uploadedFiles: buildUploadedFiles(scenarioId),
    materials: materialRows,
    fields,
    decision,
    decisionText: decisionText(decision, blockingMaterialFindings, blockingFieldFindings, reviewFindings),
    stats: fieldStats(fields),
    generatedAt: '2026-05-27 17:30'
  }
}

function buildMaterialRow(material, applicationTypeId, uploadedIds, scenarioId) {
  const applicable = material.applicability.includes(applicationTypeId)
  const uploaded = uploadedIds.has(material.id)
  const core = material.no <= 3 && applicable
  const status = materialStatus(material, applicable, uploaded, core, scenarioId)
  return {
    ...material,
    applicable,
    uploaded,
    core,
    blocking: core,
    status,
    statusText: materialStatusText(status, material, core),
    scopeText: core ? '影响最终结论' : applicable ? '官方清单项，本 demo 不阻断' : '当前类别不适用'
  }
}

function materialStatus(material, applicable, uploaded, core, scenarioId) {
  if (!applicable) return 'muted'
  if (!uploaded && core) return 'fail'
  if (!uploaded) return 'warn'
  if (scenarioId === 'review_low_confidence' && material.id === 'id988a') return 'review'
  return 'pass'
}

function materialStatusText(status, material, core) {
  if (status === 'pass') return core ? '核心材料齐全' : '材料已提交'
  if (status === 'fail') return '缺核心材料'
  if (status === 'review') return '模板识别低置信'
  if (status === 'warn') return material.conditional ? '条件应交，未上传' : '未上传，不阻断'
  return '不适用'
}

function fieldStats(fields) {
  return {
    total: fields.length,
    pass: fields.filter((field) => field.status === 'pass').length,
    fail: fields.filter((field) => field.status === 'fail').length,
    review: fields.filter((field) => field.status === 'review').length,
    required: fields.filter((field) => field.required).length
  }
}

function decisionText(decision, materialFindings, fieldFindings, reviewFindings) {
  if (decision === 'FAIL') {
    const first = materialFindings[0]?.shortName || fieldFindings[0]?.label || '核心规则'
    return `${first} 存在阻断问题，当前申请不允许通过。`
  }
  if (decision === 'REVIEW') {
    const first = reviewFindings[0]?.label || '关键字段'
    return `${first} 需要人工复核；复核前不建议自动通过。`
  }
  return '核心材料 1-3 齐全，必填字段完整，关键字段跨文件一致，允许通过。'
}

function buildFields(scenarioId, uploadedIds, applicationTypeId) {
  const id407Applicable = materials.find((item) => item.id === 'id407')?.applicability.includes(applicationTypeId)
  const id407MissingStatus = id407Applicable ? 'fail' : 'warn'
  const id407MissingIssue = id407Applicable
    ? 'ID 407 未上传，无法核验该合约字段。'
    : '当前申请类别未要求 ID 407，本字段不影响最终结论。'
  const baseFields = [
    field({
      key: 'case.application_type',
      category: '案件与文档',
      label: '申请类别',
      required: true,
      normalizedValue: 'Entry visa - Domestic helper from abroad',
      sources: [
        source('ID 988A', '第 1 部分 Application Type', 'Entry visa', '入境签证', 96)
      ],
      rule: '必须与用户在首页选择的四类申请情形一致。'
    }),
    field({
      key: 'helper.name.full_en',
      category: '傭工字段',
      label: '傭工英文姓名',
      required: true,
      normalizedValue: scenarioId === 'field_mismatch' ? 'SITI NURHALIZA / SITI NURHALIZA BINTI' : 'SITI NURHALIZA',
      status: scenarioId === 'field_mismatch' ? 'fail' : 'pass',
      issue: scenarioId === 'field_mismatch' ? 'ID 988A 与 ID 407 的傭工英文姓名明显不一致。' : '',
      sources: [
        source('ID 988A', '第 2 部分 Personal Particulars', 'Surname / Given names', 'SITI NURHALIZA', 94),
        source('ID 407', '合约首页', 'Name of Helper', scenarioId === 'field_mismatch' ? 'SITI NURHALIZA BINTI' : 'SITI NURHALIZA', 92),
        source('旅行证件副本', 'Bio-data page', 'Name', 'SITI NURHALIZA', 88)
      ],
      rule: 'ID 988A、ID 407、旅行证件上的英文姓名应一致；明显不一致判为 FAIL。'
    }),
    field({
      key: 'helper.travel_doc.number',
      category: '傭工字段',
      label: '傭工旅行证件号码',
      required: true,
      normalizedValue: scenarioId === 'review_low_confidence' ? 'C8923745 / C892374S' : 'C8923745',
      status: scenarioId === 'review_low_confidence' ? 'review' : 'pass',
      issue: scenarioId === 'review_low_confidence' ? '末位字符差异很小，疑似 OCR 把 5 识别为 S，需要人工确认。' : '',
      sources: [
        source('ID 988A', '第 2 部分 Personal Particulars', 'Travel document no.', 'C8923745', 91),
        source('旅行证件副本', 'Bio-data page', 'Passport No.', scenarioId === 'review_low_confidence' ? 'C892374S' : 'C8923745', scenarioId === 'review_low_confidence' ? 62 : 89)
      ],
      rule: 'ID 988A 与旅行证件副本号码应一致；轻微 OCR 疑点进入 REVIEW。'
    }),
    field({
      key: 'helper.date_of_birth',
      category: '傭工字段',
      label: '傭工出生日期',
      required: true,
      normalizedValue: '1992-11-27',
      sources: [
        source('ID 988A', '第 2 部分 Personal Particulars', 'Date of birth', '27/11/1992', 93),
        source('旅行证件副本', 'Bio-data page', 'Date of birth', '27 NOV 1992', 88)
      ],
      rule: '日期标准化后应一致。'
    }),
    field({
      key: 'helper.nationality',
      category: '傭工字段',
      label: '傭工国籍',
      required: true,
      normalizedValue: 'Indonesian',
      sources: [
        source('ID 988A', '第 2 部分 Personal Particulars', 'Nationality', 'Indonesian', 92),
        source('旅行证件副本', 'Bio-data page', 'Nationality', 'Indonesia', 86)
      ],
      rule: '可做国家名/国籍词标准化。'
    }),
    field({
      key: 'helper.signature.present',
      category: '傭工字段',
      label: '傭工签名',
      required: true,
      normalizedValue: scenarioId === 'missing_required_field' ? '未检测到' : '已检测到',
      status: scenarioId === 'missing_required_field' ? 'fail' : 'pass',
      issue: scenarioId === 'missing_required_field' ? 'ID 988A 申请人签名栏为空，属于必填字段缺失。' : '',
      sources: [
        source('ID 988A', '声明栏', 'Signature of applicant', scenarioId === 'missing_required_field' ? '' : 'signature detected', scenarioId === 'missing_required_field' ? 35 : 78)
      ],
      rule: 'ID 988A 申请人签名必须存在；缺失判为 FAIL。'
    }),
    field({
      key: 'employer.name.full_en',
      category: '雇主字段',
      label: '雇主英文姓名',
      required: true,
      normalizedValue: 'CHAN TAI MAN',
      sources: [
        source('ID 988B', 'Part A Employer particulars', 'Name of employer', 'CHAN TAI MAN', 93),
        source('ID 407', 'Employer section', 'Name of employer', 'CHAN TAI MAN', 90)
      ],
      rule: 'ID 988B 与 ID 407 雇主姓名应一致。'
    }),
    field({
      key: 'employer.signature.present',
      category: '雇主字段',
      label: '雇主签名',
      required: true,
      normalizedValue: '已检测到',
      sources: [
        source('ID 988B', 'Declaration', 'Signature of employer', 'signature detected', 81),
        source('ID 407', '签署栏', 'Employer signature', 'signature detected', 79)
      ],
      rule: 'ID 988B 与 ID 407 的雇主签名栏应存在签署痕迹。'
    }),
    field({
      key: 'contract.dh_contract_no',
      category: '合约字段',
      label: '标准雇佣合约编号',
      required: id407Applicable,
      normalizedValue: uploadedIds.has('id407') ? 'FH-CON-IDN2026-0612' : '未识别',
      status: uploadedIds.has('id407') ? 'pass' : id407MissingStatus,
      issue: uploadedIds.has('id407') ? '' : id407MissingIssue,
      sources: uploadedIds.has('id407')
        ? [
          source('ID 988A', '第 4 部分 Undertaking', 'Employment contract no.', 'FH-CON-IDN2026-0612', 89),
          source('ID 988B', '第 3 部分 Undertaking', 'Employment contract no.', 'FH-CON-IDN2026-0612', 88),
          source('ID 407', '合约首页', 'Contract No.', 'FH-CON-IDN2026-0612', 87)
        ]
        : [],
      rule: 'ID 988A、ID 988B 与 ID 407 的标准雇佣合约编号必须完整填写并保持一致。'
    }),
    field({
      key: 'contract.monthly_wage_hkd',
      category: '合约字段',
      label: '每月工资',
      required: id407Applicable,
      normalizedValue: uploadedIds.has('id407') ? 'HK$5,100' : '未识别',
      status: uploadedIds.has('id407') ? 'pass' : id407MissingStatus,
      issue: uploadedIds.has('id407') ? '' : id407MissingIssue,
      sources: uploadedIds.has('id407')
        ? [source('ID 407', '工资及膳食条款', 'Monthly wages', 'HK$5,100', 86)]
        : [],
      rule: '正式版本应接入当前法定最低工资阈值；本 demo 使用样例值展示。'
    }),
    field({
      key: 'contract.food.allowance_hkd',
      category: '合约字段',
      label: '膳食津贴',
      required: id407Applicable,
      normalizedValue: uploadedIds.has('id407') ? 'HK$1,236' : '未识别',
      status: uploadedIds.has('id407') ? 'pass' : id407MissingStatus,
      issue: uploadedIds.has('id407') ? '' : id407MissingIssue,
      sources: uploadedIds.has('id407')
        ? [source('ID 407', '工资及膳食条款', 'Food allowance', 'HK$1,236', 84)]
        : [],
      rule: '正式版本应接入当前膳食津贴阈值；本 demo 使用样例值展示。'
    }),
    field({
      key: 'document.footer_id',
      category: '案件与文档',
      label: '页尾模板标识',
      required: true,
      normalizedValue: scenarioId === 'review_low_confidence' ? 'ID 988A low confidence' : 'ID 988A / ID 988B / ID 407',
      status: scenarioId === 'review_low_confidence' ? 'review' : 'pass',
      issue: scenarioId === 'review_low_confidence' ? 'ID 988A 页尾标识区域模糊，需要人工确认模板版本。' : '',
      sources: [
        source('ID 988A', '页尾', 'Template footer', 'ID 988A (06/2024)', scenarioId === 'review_low_confidence' ? 58 : 96),
        source('ID 988B', '页尾', 'Template footer', 'ID 988B (06/2024)', 95),
        ...(uploadedIds.has('id407') ? [source('ID 407', '页尾', 'Template footer', 'ID 407 (11/2016)', 93)] : [])
      ],
      rule: '通过页尾 ID 与页面结构识别材料类型和模板版本。'
    })
  ]

  return baseFields.map((item) => ({
    ...item,
    blocking: item.required && item.category !== '证明材料字段'
  }))
}

function field(config) {
  return {
    status: 'pass',
    issue: '',
    ...config
  }
}

function source(documentName, section, fieldName, value, confidence) {
  return {
    documentName,
    section,
    fieldName,
    value,
    confidence,
    snapshotText: value || 'blank'
  }
}
