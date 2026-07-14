export const applicationTypes = [
  {
    id: 'iang_recent_in_hk',
    label: 'IANG 应届毕业生在港首次申请',
    shortLabel: 'IANG 应届首申',
    description: '内地非本地学生在香港完成本科或以上课程后，毕业 6 个月内在港申请 IANG 留港工作/逗留。',
    checklistKey: 'IANG 应届毕业生在港首次申请'
  }
]

export const scenarios = [
  {
    id: 'iang_recent_in_hk_review',
    label: 'IANG 应届毕业生在港首次申请',
    shortLabel: 'IANG 应届首申'
  }
]

export const materials = [
  {
    id: 'id990a',
    no: 1,
    name: 'IANG / 专业人士来港就业申请表',
    shortName: 'ID 990A',
    templateId: 'ID 990A',
    expectedPages: '前 5 页',
    applicable: true,
    uploaded: true,
    core: true,
    blocking: true,
    status: 'pass',
    statusText: '已识别前 5 页',
    requirementLabel: 'Demo审批',
    scopeText: 'Demo审批；按页尾页码识别 ID 990A 前 5 页'
  },
  {
    id: 'educationProof',
    no: 2,
    name: '学历 / 毕业资格证明',
    shortName: '毕业证明',
    templateId: 'Certifying letter / Transcript / Graduation certificate',
    expectedPages: '按证明',
    applicable: true,
    uploaded: true,
    core: true,
    blocking: true,
    status: 'pass',
    statusText: '毕业资格可核验',
    requirementLabel: 'Demo审批',
    scopeText: 'Demo审批；判断学历层级及 6 个月应届窗口'
  },
  {
    id: 'identityDocs',
    no: 3,
    name: '港澳通行证 / 护照 / 香港身份证',
    shortName: '身份及旅行证件',
    templateId: 'EEP / Passport / HKID',
    expectedPages: '资料页及 HKID',
    applicable: true,
    uploaded: true,
    core: true,
    blocking: true,
    status: 'pass',
    statusText: '身份字段可核验',
    requirementLabel: 'Demo审批',
    scopeText: 'Demo审批；与申请表、毕业证明、付款记录交叉核验'
  },
  {
    id: 'paymentStatus',
    no: 4,
    name: '付款状态 / 申请费付款截图',
    shortName: '付款状态',
    templateId: 'Online payment page',
    expectedPages: '1 页',
    applicable: true,
    uploaded: true,
    core: true,
    blocking: true,
    status: 'review',
    statusText: '显示尚未完成付款',
    issue: '付款页显示 “NOT YET COMPLETE”，需要补缴或确认付款完成状态。',
    requirementLabel: 'Demo审批',
    scopeText: 'Demo审批；付款未完成时保留 REVIEW'
  },
  {
    id: 'photo',
    no: 5,
    name: '申请人近照',
    shortName: '申请人近照',
    templateId: 'Photo',
    expectedPages: '1 张',
    applicable: true,
    uploaded: false,
    core: false,
    blocking: false,
    status: 'warn',
    statusText: '未上传',
    requirementLabel: '官方应交',
    scopeText: '当前 demo 不纳入最终阻断'
  },
  {
    id: 'currentStayEvidence',
    no: 6,
    name: '最近入境记录 / 小白条 / e-Visa',
    shortName: '当前逗留记录',
    templateId: 'Landing slip / e-Visa',
    expectedPages: '按记录',
    applicable: true,
    uploaded: false,
    core: false,
    blocking: false,
    status: 'warn',
    statusText: '未上传',
    requirementLabel: '官方应交',
    scopeText: '确认当前在港及逗留期限；当前 demo 不纳入最终阻断'
  },
  {
    id: 'mainlandConsent',
    no: 7,
    name: '《内地的中国居民赴港工作同意书》',
    shortName: '赴港工作同意书',
    templateId: 'ID(C) 991 附件二',
    expectedPages: '1 页',
    applicable: true,
    uploaded: false,
    core: false,
    blocking: false,
    conditional: true,
    status: 'warn',
    statusText: '内地居民条件应交',
    requirementLabel: '条件应交',
    scopeText: '内地居民适用；当前 demo 展示为条件应交，不纳入最终阻断'
  },
  {
    id: 'visaIssueFee',
    no: 8,
    name: '获批后的签证签发费付款证明 / e-Visa 下载件',
    shortName: '获批后结果材料',
    templateId: 'Visa issue fee / e-Visa',
    expectedPages: '获批后产生',
    applicable: true,
    uploaded: false,
    core: false,
    blocking: false,
    status: 'warn',
    statusText: '获批后阶段材料',
    requirementLabel: '后续阶段',
    scopeText: '获批后用于结果归档；不属于当前首轮材料审核阻断范围'
  }
]

const uploadedFiles = [
  {
    materialId: 'id990a',
    documentName: 'ID 990A',
    filename: 'ID990A_iang_recent_graduate_pages_1-5.pdf',
    pages: 5,
    footerId: 'ID 990A'
  },
  {
    materialId: 'educationProof',
    documentName: '毕业证明',
    filename: '毕业证明.pdf',
    pages: 1,
    footerId: 'Certifying letter'
  },
  {
    materialId: 'identityDocs',
    documentName: '身份及旅行证件',
    filename: '港澳通行证_HKID_组合样本.pdf',
    pages: 3,
    footerId: 'EEP / HKID'
  },
  {
    materialId: 'paymentStatus',
    documentName: '付款状态',
    filename: '付款证明.png',
    pages: 1,
    footerId: 'Payment of Application Fee'
  }
]

export function buildUploadedFiles() {
  return uploadedFiles.map((file) => ({ ...file }))
}

export function buildReviewResult(applicationTypeId = 'iang_recent_in_hk', scenarioId = 'iang_recent_in_hk_review') {
  const fields = buildFields()
  const resultMaterials = materials.map((material) => ({ ...material }))
  return {
    applicationTypeId,
    scenarioId,
    uploadedFiles: buildUploadedFiles(),
    materials: resultMaterials,
    fields,
    documentFieldGroups: buildDocumentFieldGroups(),
    decision: 'REVIEW',
    decisionText: '付款状态显示申请流程尚未完成，需确认付款完成后再进入最终通过。',
    stats: fieldStats(fields),
    generatedAt: '2026-06-22 16:30'
  }
}

function buildFields() {
  return [
    field({
      key: 'applicant.name.full_en',
      category: '申请人身份',
      label: '申请人英文姓名',
      required: true,
      normalizedValue: 'ZHAO HANGYU',
      sources: [
        source('ID 990A', '第 2 页 Personal particulars', 'Name in English', 'ZHAO HANGYU', 95),
        source('毕业证明', '证明正文', 'Student name', 'ZHAO, Hangyu', 94),
        source('付款状态', 'Payment of Application Fee', 'Applicant’s Name', 'ZHAO, HAN***', 80)
      ],
      rule: '申请表、学历证明、付款记录中的申请人姓名应可归一到同一申请人；脱敏付款页进入人工复核但不直接判失败。'
    }),
    field({
      key: 'applicant.hkid',
      category: '申请人身份',
      label: '香港身份证号码',
      required: true,
      normalizedValue: 'F539325(2)',
      sources: [
        source('ID 990A', '第 2 页 Personal particulars', 'HKID no.', 'F539325(2)', 93),
        source('毕业证明', '证明正文', 'Hong Kong Identity Card No.', 'F539325(2)', 95),
        source('HKID', '身份证正面', 'HKID No.', 'F539325(2)', 90)
      ],
      rule: 'HKID 在申请表、毕业证明和身份证样本中应一致；如无 HKID 则按官方 if any 处理。'
    }),
    field({
      key: 'applicant.travel_doc.number',
      category: '申请人身份',
      label: '港澳通行证 / 护照号码',
      required: true,
      normalizedValue: 'CA3273201',
      suggestedValue: 'CA3273201',
      status: 'review',
      correctionApplied: false,
      suggestionReason: '跨材料值不一致，建议采用“CA3273201”，该字段需人工复核确认。',
      issue: '申请表与旅行证件的号码不一致，需人工复核。',
      sources: [
        source('ID 990A', '第 2 页 Travel document', 'Travel document no.', 'CA3273201', 91),
        source('港澳通行证', '资料页', 'Permit no.', 'CA3273207', 86)
      ],
      rule: '申请表上的旅行证件号码应与港澳通行证或护照资料页一致。'
    }),
    field({
      key: 'applicant.date_of_birth',
      category: '申请人身份',
      label: '出生日期',
      required: true,
      normalizedValue: '1981-08-03',
      sources: [
        source('ID 990A', '第 2 页 Personal particulars', 'Date of birth', '03/08/1981', 92),
        source('港澳通行证', '资料页', 'Date of birth', '1981.08.03', 90)
      ],
      rule: '日期标准化后应一致。'
    }),
    field({
      key: 'education.institution',
      category: '学历与毕业资格',
      label: '毕业院校',
      required: true,
      normalizedValue: 'The Chinese University of Hong Kong',
      sources: [
        source('毕业证明', '页脚 / 签发机构', 'Institution', 'The Chinese University of Hong Kong', 95),
        source('ID 990A', '第 4 页 Education background', 'Institution', 'The Chinese University of Hong Kong', 90)
      ],
      rule: '毕业院校应为香港认可院校或符合 IANG 资格的院校范围。'
    }),
    field({
      key: 'education.programme',
      category: '学历与毕业资格',
      label: '课程 / 专业',
      required: true,
      normalizedValue: 'Master of Science in Computer Science',
      sources: [
        source('毕业证明', '证明正文', 'Programme', 'Master of Science in Computer Science (Full-time)', 96),
        source('ID 990A', '第 4 页 Education background', 'Programme', 'MSc Computer Science', 88)
      ],
      rule: '课程名称可做简称归一；应为本科或以上资历。'
    }),
    field({
      key: 'education.graduation_date',
      category: '学历与毕业资格',
      label: '毕业 / 完成课程日期',
      required: true,
      normalizedValue: '2026-06-16',
      sources: [
        source('毕业证明', '签发日期及证明内容', 'Completion date', '16 June 2026', 91),
        source('ID 990A', '第 4 页 Education background', 'Graduation date', '2026-06-16', 88)
      ],
      rule: '应届毕业生通常以毕业证书或院校证明所载日期判断 6 个月申请窗口。'
    }),
    field({
      key: 'education.recent_graduate_window',
      category: '学历与毕业资格',
      label: '应届毕业生 6 个月窗口',
      required: true,
      normalizedValue: 'Within 6 months',
      sources: [
        source('毕业证明', '签发日期及证明内容', 'Completion date', '16 June 2026', 91),
        source('ID 990A', '第 1 页 Application date', 'Application date', '22 June 2026', 86)
      ],
      rule: '毕业日期距申请日期不超过 6 个月时，按 IANG 应届毕业生路径处理。'
    }),
    field({
      key: 'payment.application_fee_status',
      category: '付款状态',
      label: '申请费付款状态',
      required: true,
      normalizedValue: 'NOT YET COMPLETE',
      status: 'review',
      issue: '付款页显示在线申请流程尚未完成，需要补缴或上传付款成功记录。',
      sources: [
        source('付款状态', 'Payment of Application Fee', 'Payment status', 'The online application process is NOT YET COMPLETE', 97)
      ],
      rule: '付款状态应与申请阶段一致；若仍显示未完成，不应自动通过。'
    }),
    field({
      key: 'id990a.signature.present',
      category: 'IANG/专业人士申请表',
      label: '申请人声明及签署',
      required: true,
      normalizedValue: '已检测到',
      sources: [
        source('ID 990A', '第 5 页 Declaration', 'Signature of applicant', 'signature detected', 82),
        source('ID 990A', '第 5 页 Declaration', 'Declaration date', '22 June 2026', 84)
      ],
      rule: 'ID 990A 前 5 页识别范围内，申请人声明及签署应存在。'
    })
  ].map((item) => ({
    ...item,
    blocking: item.required
  }))
}

function buildDocumentFieldGroups() {
  return [
    {
      materialId: 'id990a',
      materialName: 'ID 990A IANG / 专业人士来港就业申请表',
      templateId: 'ID 990A',
      note: '当前 demo 按页尾页码仅识别前 5 页。',
      pages: [
        page(1, '申请类别', [
          ['申请计划', 'IANG - 非本地应届毕业生', 'pass'],
          ['申请地点', 'In Hong Kong', 'pass'],
          ['申请日期', '22 June 2026', 'pass']
        ]),
        page(2, '个人资料及旅行证件', [
          ['英文姓名', 'ZHAO HANGYU', 'pass'],
          ['香港身份证号码', 'F539325(2)', 'pass'],
          ['旅行证件号码', 'CA3273201', 'pass', { confidence: 91 }],
          ['出生日期', '03/08/1981', 'pass'],
          ['性别', 'Female', 'pass']
        ]),
        page(3, '联络资料', [
          ['香港住址', 'Flat G, Tower 3, Nob Hill, Kwai Chung', 'pass'],
          ['电话', '+852 9123 4567', 'pass'],
          ['电邮', 'applicant@example.com', 'pass']
        ]),
        page(4, '学历资料', [
          ['院校', 'The Chinese University of Hong Kong', 'pass'],
          ['课程', 'Master of Science in Computer Science', 'pass'],
          ['修读模式', 'Full-time', 'pass'],
          ['毕业日期', '16 June 2026', 'pass']
        ]),
        page(5, '在港逗留及申请资料', [
          ['现有逗留身份', 'Student', 'pass'],
          ['获准逗留至', '31 July 2026', 'pass'],
          ['是否已觅得工作', 'No - recent graduate route', 'pass'],
          ['申请人声明', '已勾选', 'pass'],
          ['申请人签署', '已检测到', 'pass'],
          ['签署日期', '22 June 2026', 'pass']
        ])
      ]
    },
    {
      materialId: 'educationProof',
      materialName: '学历 / 毕业资格证明',
      templateId: 'Certifying letter',
      pages: [
        page(1, '毕业资格证明', [
          ['Ref', 'GS/19/1', 'pass'],
          ['收件人', 'IMMIGRATION DEPARTMENT', 'pass'],
          ['姓名', 'ZHAO, Hangyu', 'pass'],
          ['身份证号', 'F539325(2)', 'pass'],
          ['大学', 'The Chinese University of Hong Kong', 'pass'],
          ['学科及学位', 'Master of Science in Computer Science (Full-time)', 'pass'],
          ['日期', '16 June 2026', 'pass']
        ])
      ]
    },
    {
      materialId: 'identityDocs',
      materialName: '港澳通行证 / 护照 / 香港身份证',
      templateId: 'EEP / HKID',
      pages: [
        page(1, '港澳通行证资料页', [
          ['姓名', 'ZHENGJIAN, YANGBEN', 'pass'],
          ['证件号码', 'CA3273201', 'pass'],
          ['出生日期', '1981.08.03', 'pass'],
          ['有效期限', '2019.01.18 - 2029.01.17', 'pass']
        ]),
        page(2, '香港身份证', [
          ['英文姓名', 'ZHAO HANGYU', 'pass'],
          ['HKID', 'F539325(2)', 'pass'],
          ['出生日期', '03-08-81', 'pass']
        ])
      ]
    },
    {
      materialId: 'paymentStatus',
      materialName: '付款状态 / 申请费付款截图',
      templateId: 'Payment of Application Fee',
      pages: [
        page(1, '付款页面', [
          ['申请人', 'ZHAO, HAN***', 'review'],
          ['申请编号', '1340351-25', 'pass'],
          ['申请人数', '1', 'pass'],
          ['每份申请需缴纳的申请费', 'HK$ 600.00', 'pass'],
          ['申请费总金额', 'HK$ 600.00', 'pass']
        ])
      ]
    }
  ]
}

function page(pageNo, title, rows) {
  return {
    pageNo,
    title,
    fields: rows.map(([label, value, status, extra = {}]) => ({
      label,
      value,
      status,
      confidence: status === 'review' ? 78 : 92,
      ...extra
    }))
  }
}

function field(config) {
  return {
    status: 'pass',
    issue: '',
    ...config
  }
}

function source(documentName, section, fieldName, value, confidence) {
  const materialId = materialIdForSourceDocument(documentName)
  const pageNo = pageNoForSource(documentName, section, fieldName)
  return {
    documentName,
    materialId,
    pageNo,
    section,
    fieldName,
    value,
    confidence,
    locatorConfidence: 0,
    snapshotText: value || 'blank'
  }
}

function materialIdForSourceDocument(documentName) {
  if (documentName === 'ID 990A') return 'id990a'
  if (documentName === '毕业证明') return 'educationProof'
  if (documentName === '港澳通行证' || documentName === 'HKID') return 'identityDocs'
  if (documentName === '付款状态') return 'paymentStatus'
  return ''
}

function pageNoForSource(documentName, section, fieldName) {
  const pageMatch = String(section || '').match(/第\s*(\d+)\s*页/)
  if (pageMatch) return Number(pageMatch[1]) || 0
  if (documentName === '毕业证明' || documentName === '付款状态') return 1
  if (documentName === '港澳通行证') return 1
  if (documentName === 'HKID') return 2
  if (documentName === 'ID 990A' && fieldName.includes('Signature')) return 5
  return 0
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
