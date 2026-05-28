# FDH Entry Visa 官方附件清单与跨文件字段核验台账

更新时间：2026-05-27  
适用场景：新聘外籍家庭傭工入境簽證（Entry Visa for New Foreign Domestic Helper / FDH Entry Visa）  
项目用途：支撑“文件分類 / 數據核驗 / Minutes 草擬 / 整合工作流 / AI 準確率與優化”的后续迭代。

## 1. 官方来源与本地抓取结果

本次仅以香港入境事务处（Immigration Department, ImmD）和香港劳工处 FDH Portal / 政府新闻稿为依据；非官方中介网站不作为规则来源。

| 来源 | 官方链接 | 本地保存 |
|---|---|---|
| FDH 申请说明页 | https://www.immd.gov.hk/eng/services/visas/foreign_domestic_helpers.html | `docs/fdh-entry-visa-official/foreign_domestic_helpers_official.html` |
| 12 项 checklist（英文） | https://www.immd.gov.hk/eng/forms/forms/fdhchecklist.html | `docs/fdh-entry-visa-official/fdhchecklist_official.html` |
| 12 项 checklist（中文，截图同页） | https://www.immd.gov.hk/hks/forms/forms/fdhchecklist.html | `docs/fdh-entry-visa-official/fdhchecklist_official_hks.html` |
| ID 988A 表格页 | https://www.immd.gov.hk/eng/forms/forms/id988a.html | `docs/fdh-entry-visa-official/ID988A_official_page.html` |
| ID 988A PDF | https://www.immd.gov.hk/pdforms/id988a.pdf | `docs/fdh-entry-visa-official/ID988A_06-2024.pdf` |
| ID 988B 表格页 | https://www.immd.gov.hk/eng/forms/forms/id988b.html | `docs/fdh-entry-visa-official/ID988B_official_page.html` |
| ID 988B PDF | https://www.immd.gov.hk/pdforms/ID988B.pdf | `docs/fdh-entry-visa-official/ID988B_06-2024.pdf` |
| ID 407 合约说明页 | https://www.immd.gov.hk/eng/forms/forms/id407.html | `docs/fdh-entry-visa-official/ID407_11-2016_page*_specimen.jpg` |
| ID(E) 969 指南 | https://www.immd.gov.hk/eng/forms/forms/id-e-969.html | `docs/fdh-entry-visa-official/IDE969_official_page.html`, `IDE969_06-2024_guidebook.pdf` |
| 劳工处 FDH Portal | https://www.fdh.labour.gov.hk/en/home.html | `docs/fdh-entry-visa-official/labour_fdh_home_official.html` |
| MAW / 膳食津贴政府新闻稿 | https://www.info.gov.hk/gia/general/202509/29/P2025092900318.htm | `docs/fdh-entry-visa-official/maw_food_allowance_2025-09-29_official.html` |

版本/页数基线：

| 模板 | 当前项目 templateId 建议 | 页脚/版本 | 标准页数 | 说明 |
|---|---|---:|---:|---|
| Visa/Extension of Stay Application Form for Domestic Helper from Abroad | `id988a_2024_06` | `ID 988A (06/2024)` | 5 | 由傭工填写，须贴相片并签署。 |
| Application for Employment of Domestic Helper from Abroad | `id988b_2024_06` | `ID 988B (06/2024)` | 4 | 由雇主填写并签署。 |
| Standard Employment Contract | `id407_2016_11` | `ID 407 (11/2016)` | 4 | 官网公开页为 specimen，仅供参考；正式提交应为 ImmD 发放/邮寄的合约正本，且按需要经相关领事馆公证。 |

## 2. 12 项附件清单：Entry Visa 的必交/条件/不适用

ImmD checklist 覆盖四类 FDH 申请。对“新聘外籍家庭傭工入境簽證 / Entry visa”，不能把 12 项全部视为必交；应按以下状态判断。

| 序号 | 官方附件/材料 | Entry Visa 状态 | 模板/版本 | 分类识别要点 | 后续校验重点 |
|---:|---|---|---|---|---|
| 1 | Visa/Extension of Stay Application Form for Domestic Helper from Abroad (`ID 988A`) | 必交 | `ID 988A (06/2024)`, 5 页 | 页脚 ID、标题、申请类别区、傭工个人资料、相片框、签名栏 | 申请类别必须选“Entry to Hong Kong to take up employment as a domestic helper from abroad / Entry visa”；傭工姓名、证件号、出生日期、国籍、签名、相片必核。 |
| 2 | Application for Employment of Domestic Helper from Abroad (`ID 988B`) | 必交 | `ID 988B (06/2024)`, 4 页 | 页脚 ID、标题、雇主资料、傭工资料、住户资料、声明/承诺 | 雇主身份、住址、收入声明、傭工姓名、雇佣类型、签名必须完整；与 ID 407、雇主证件、地址/财力证明一致。 |
| 3 | Original copy of new Standard Employment Contract (`ID 407`) | 必交 | `ID 407 (11/2016)`, 4 页 | D.H. Contract No.、合约条款、工资/膳食、住宿及家务安排附录、双方签名 | 必须为新合约正本；如相关领事馆要求，应有公证/认证；工资不得低于当前 MAW；合约期通常 2 年；雇主/傭工签名与 988A/988B 一致。 |
| 4 | Original of Helper's travel document | Entry Visa 不适用 | 非固定模板 | checklist 对 Entry visa 无 tick | 不应对新聘入境签证报“缺件”；但如实际流程要求面交原件，应作为人工复核项。 |
| 5 | Copy of Helper's travel document | 必交 | 护照/旅行证件 | 证件资料页、相片、证件号、有效期；如适用含原居地 re-entry visa | 与 ID 988A 的姓名、出生日期、国籍、旅行证件号、签发/到期日一致；护照有效期必须覆盖拟逗留期，ImmD 说明逗留期限不会超过护照到期前 1 个月。 |
| 6 | Copy of Helper's Hong Kong Identity Card (if any) | 条件必交 | HKID | HKID 卡面、姓名、号码 | ID 988A 声明“有 HKID”时必须出现；如 ID 988A 勾选没有，不应强制缺件。 |
| 7 | Copy of employer's HK Permanent Identity Card / HK Identity Card / passport | 必交 | HKID/护照/旅行证件 | 雇主姓名、证件号、出生日期、相片 | 与 ID 988B 雇主资料一致；如非 HKPR / right to land / unconditional stay，应按说明保留旅行证件证据。 |
| 8 | Proof of employer's financial position (Copy) | 必交 | 税单/银行/工资/资产等 | IRD notice、3 个月薪金 auto-payment、3 个月工资单、自雇/董事证明、6 个月定存/存款等 | ID 988B 住户每月平均收入声明须不少于 HK$15,000（每聘用 1 名傭工）；证明期间、金额、姓名/公司关系必须可追溯。 |
| 9 | Proof of employer's residential address (Copy) | 必交 | 差饷/水电电话等账单 | 地址证明日期、地址、持有人 | 必须为 ID 407 合约地址，通常为最近 3 个月内；房署/房协屋邨需同意信及租约页；证明不在雇主名下时需关系证明。 |
| 10 | Testimonial of Helper | 必交 | 推荐/工作证明 | 证明人姓名和地址、傭工姓名、工作性质、期间 | 必须显示至少 2 年家庭傭工工作经验；证明人姓名/地址用于核验；与 ID 988A 工作经历一致。 |
| 11 | Release letter from current employer showing date of expiry/termination | Entry Visa 不适用 | 信件 | 当前雇主、终止/到期日、签署 | 仅 Change employer 需要；新聘入境签证不应强制缺件。 |
| 12 | Employer's supporting letter to confirm continuous employment | Entry Visa 不适用 | 信件 | 雇主确认继续聘用、期间、签署 | 仅 Completion of remaining period 需要；新聘入境签证不应强制缺件。 |

### 2.1 官网 checklist 脚注规则

截图页底部的 `* / ** / *** / ****` 是跨申请类别的提交方式规则，应作为附件完整性规则的一部分保存。对本台账的 Entry Visa 场景影响如下：

| 官网脚注 | 官方含义 | 对 Entry Visa 的规则影响 |
|---|---|---|
| `*` | 网上申请续约时，领取延期逗留标签时须同时提交雇佣合约正本或已公证合约供查阅。 | 该脚注只挂在“于两年合约期届满后续约”的 ID 407 栏，不适用于新聘 Entry Visa；Entry Visa 仍按 ID 407 必交处理。 |
| `**` | 适用于亲身递交申请。 | 该脚注挂在“旅行证件正本”的续约/余下期间栏；Entry Visa 栏无 tick，因此新聘 Entry Visa 不应因缺旅行证件正本而失败。 |
| `***` | 邮递、投递或网上递交时，收到领证通知后须带备通知书、授权书（如适用）及申请人的旅行证件领取延期逗留标签。 | Entry Visa 的“旅行证件副本”无脚注但有 tick，因此仍为必交副本；领取标签阶段的原件核对不应混入首轮缺件规则。 |
| `****` | 续约/完成余下期间时，如声明每聘用 1 名家庭佣工的住户月均入息不少于 HK$15,000 且住址无改变，递交时无须提交财力和住址证明；ImmD 仍可要求补充。 | 该豁免只挂在续约/余下期间栏；Entry Visa 的财力证明和住址证明均无豁免，仍为必交。 |

## 3. 当前关键业务阈值

这些阈值会随政策变更，规则配置应做版本化，不应硬编码在代码里。

| 规则项 | 当前基线 | 来源/说明 |
|---|---|---|
| 雇主家庭收入 | 一般每聘用 1 名傭工，月家庭收入不少于 HK$15,000，或有可比资产支持整个合约期 | ImmD FDH 页面和 ID 988B 住户资料。 |
| FDH 最低工资（MAW） | 自 2025-09-30 起签订的合约，MAW 为 HK$5,100/月；2024-09-28 至 2025-09-29 签订的旧基线为 HK$4,990/月 | 劳工处 FDH Portal / 政府新闻稿。需求材料里“HK$4,990”只能作为旧示例。 |
| 膳食 | 雇主须提供免费膳食；如以膳食津贴代替，当前不少于 HK$1,236/月 | 2025-09-29 政府新闻稿。 |
| 合约期 | 标准雇佣合约通常为 2 年 | ID 407 说明。 |
| 证件有效期 | 傭工必须持有效 national passport；准许逗留日期不会超过护照到期前 1 个月 | ImmD Entry Visa 申请说明。 |
| 地址证明时效 | 差饷/水电/电话/电费等地址证明通常为最近 3 个月内 | ImmD Entry Visa Step 3。 |
| 非中英文材料 | 需要经认可译者/法院译员/授权公众翻译等认证的中文或英文译本 | ImmD Important Notes。 |

## 4. 标准化字段字典（跨文件核验用）

字段命名建议采用 `entity.field`，每个字段都保留 `sourceDocumentId`, `page`, `bbox`, `rawText`, `normalizedValue`, `confidence` 和 `evidenceImage`，以满足 crop/highlight 追溯。

### 4.1 案件与文档元数据

| 字段 | 类型 | 主要来源 | 标准化/说明 |
|---|---|---|---|
| `case.application_type` | enum | ID 988A, checklist | 固定为 `FDH_ENTRY_VISA_NEW_HELPER`；ID 988A 必须勾选 1(a) Entry visa。 |
| `document.type` | enum | 分类器 | `ID988A`, `ID988B`, `ID407`, `HELPER_TRAVEL_DOC_COPY`, `HELPER_HKID_COPY`, `EMPLOYER_ID_COPY`, `EMPLOYER_FINANCIAL_PROOF`, `EMPLOYER_ADDRESS_PROOF`, `HELPER_TESTIMONIAL`, `RELEASE_LETTER`, `CONTINUOUS_EMPLOYMENT_LETTER`, `OTHER`。 |
| `document.template_id` | string | 页脚/版面 | 例如 `id988a_2024_06`。 |
| `document.footer_id` | string | 页脚 OCR | 例如 `ID 988A (06/2024)`。 |
| `document.page_count` | integer | PDF/image parser | 与模板标准页数比较。 |
| `document.file_hash` | string | 文件接入 | 用于重复上传/重复页检测。 |
| `document.quality_flags` | array | OCR/视觉质量 | 模糊、裁边、旋转、低分辨率、遮挡、涂抹。 |

### 4.2 傭工字段

| 字段 | 来源 | 用途 |
|---|---|---|
| `helper.name.surname_en`, `helper.name.given_en`, `helper.name.full_en` | ID 988A, travel document, ID 407, testimonial | 姓名一致性主键；英文名需大小写/空格标准化。 |
| `helper.name.chinese`, `helper.name.maiden`, `helper.name.alias` | ID 988A | 作为辅助匹配和 Minutes 生成字段。 |
| `helper.sex`, `helper.date_of_birth`, `helper.place_of_birth`, `helper.marital_status` | ID 988A, travel document | 与护照资料页核验。 |
| `helper.nationality`, `helper.occupation` | ID 988A, travel document | 国籍一致性和普通移民要求核验。 |
| `helper.hkid.has_hkid`, `helper.hkid.number` | ID 988A, helper HKID copy | 若声明有 HKID，则附件 6 条件必交。 |
| `helper.travel_doc.type`, `helper.travel_doc.number`, `helper.travel_doc.issue_place`, `helper.travel_doc.issue_date`, `helper.travel_doc.expiry_date` | ID 988A, travel document copy | 证件一致性、有效期、过期风险。 |
| `helper.address.present`, `helper.address.domicile` | ID 988A | 地址字段抽取；如与证件/证明冲突则人工复核。 |
| `helper.contact.phone`, `helper.contact.fax`, `helper.contact.email` | ID 988A | 联系信息完整性。 |
| `helper.current_employer.name`, `helper.current_employer.address` | ID 988A | Entry visa 通常可为空；如出现，需识别是否误选申请类型。 |
| `helper.work_experience.records`, `helper.work_experience.total_years`, `helper.work_experience.total_months` | ID 988A, testimonial | Testimonial 至少 2 年经验规则。 |
| `helper.declarations.name_changed`, `helper.declarations.visa_refused`, `helper.declarations.convicted` | ID 988A | 风险项和 Minutes 草拟。 |
| `helper.photo.present` | ID 988A | Entry visa 类型 1(a) 必须贴相片。 |
| `helper.signature.present`, `helper.signature.date`, `helper.signature.image_hash` | ID 988A, ID 407 | 签名缺失、跨文件签名相似性。 |

### 4.3 雇主字段

| 字段 | 来源 | 用途 |
|---|---|---|
| `employer.name.chinese`, `employer.name.surname_en`, `employer.name.given_en`, `employer.name.full_en` | ID 988B, employer ID, ID 407, financial/address proof | 雇主身份一致性主键。 |
| `employer.sex`, `employer.date_of_birth`, `employer.nationality`, `employer.occupation` | ID 988B, employer ID | 身份一致性。 |
| `employer.hkid.number`, `employer.travel_doc.type`, `employer.travel_doc.number` | ID 988B, employer ID/passport | 证件一致性。 |
| `employer.address.residential`, `employer.address.correspondence` | ID 988B, ID 407, address proof | 合约地址、住址证明和 ID 988B 地址一致性。 |
| `employer.contact.phone`, `employer.contact.home_phone`, `employer.contact.fax`, `employer.contact.email` | ID 988B | 联系信息完整性。 |
| `employer.signature.present`, `employer.signature.date`, `employer.signature.image_hash` | ID 988B, ID 407 | 签名缺失和跨文件一致性。 |

### 4.4 合约与住户字段

| 字段 | 来源 | 用途 |
|---|---|---|
| `contract.dh_contract_no` | ID 407, ID 988A undertaking, ID 988B undertaking | 合约编号一致性。 |
| `contract.date_signed` | ID 407 | 用于 MAW 生效日期判断。 |
| `contract.helper_place_of_origin` | ID 407, travel document | 原居地/国籍/回程安排核验。 |
| `contract.commencement.option`, `contract.commencement.date` | ID 407 | A/B/C 选项；新聘入境通常应为 Clause 2(A)。 |
| `contract.duration_months` | ID 407 | 通常 24 个月。 |
| `contract.employer_residence_address` | ID 407, ID 988B, address proof | live-in 地址一致性。 |
| `contract.monthly_wage_hkd` | ID 407, ID 988A/988B undertaking | 不低于签约日适用 MAW。 |
| `contract.food.free_food_provided`, `contract.food.allowance_hkd` | ID 407 | 未提供免费膳食时，津贴不低于当前基线。 |
| `contract.travel_origin`, `contract.return_passage_terms` | ID 407 | 回程/原居地责任核验。 |
| `contract.signatures.employer`, `contract.signatures.helper`, `contract.signatures.witnesses` | ID 407 | 签名/见证签名完整性。 |
| `contract.notarisation.required`, `contract.notarisation.present` | ID 407, consulate requirement | 领事馆要求时必须有公证/认证。 |
| `household.income.declared_ge_15000`, `household.income.amount_hkd` | ID 988B, financial proof | 雇主资格核验。 |
| `household.bedrooms_count`, `household.separate_servant_room` | ID 988B, ID 407 schedule | 住宿安排一致性。 |
| `household.members[]` | ID 988B, ID 407 schedule, tenancy docs | 成员数必须与 ID 407 附录第 2 段一致。 |
| `household.current_helpers[]` | ID 988B | 增聘/替换傭工时判断是否需要额外说明信。 |
| `duties.domestic_duties[]`, `duties.window_cleaning_conditions` | ID 407 schedule | 是否仅家务职责、外窗清洁安全条款。 |

### 4.5 支持性材料字段

| 材料 | 标准字段 | 核验用途 |
|---|---|---|
| Helper travel document copy | `passport.name`, `passport.number`, `passport.nationality`, `passport.date_of_birth`, `passport.issue_date`, `passport.expiry_date`, `passport.photo_present`, `passport.reentry_visa_present` | 与 ID 988A、ID 407 一致；有效期和相片完整性。 |
| Helper HKID copy | `helper_hkid.name`, `helper_hkid.number` | 仅在 ID 988A 声明有 HKID 时强制。 |
| Employer ID/passport copy | `employer_id.name`, `employer_id.number`, `employer_id.date_of_birth`, `employer_id.photo_present` | 与 ID 988B、ID 407 一致。 |
| Financial proof | `financial_proof.type`, `holder_name`, `period_start`, `period_end`, `monthly_income_hkd`, `asset_balance_hkd`, `company_relation_proof_present` | 收入/资产资格、期间覆盖、材料是否过期。 |
| Address proof | `address_proof.type`, `holder_name`, `issue_date`, `address`, `housing_authority_consent_present`, `tenancy_pages_present`, `relationship_proof_present` | 3 个月内、地址一致、非雇主名下时关系证明。 |
| Helper testimonial | `testimonial.helper_name`, `writer_name`, `writer_address`, `employment_role`, `period_start`, `period_end`, `duration_months`, `signature_or_stamp_present` | 至少 2 年家庭傭工经验；证明人信息完整。 |
| Release letter | `release.current_employer_name`, `helper_name`, `termination_or_expiry_date`, `signature_present` | Entry visa 不强制；如误上传可分类为不适用材料。 |
| Continuous employment letter | `continuous_letter.employer_name`, `helper_name`, `remaining_period`, `signature_present` | Entry visa 不强制；仅 remaining period 场景。 |

## 5. 规则引擎核验清单

建议输出 `PASS / WARN / FAIL / REVIEW` 四类结论，并对每条规则保留证据位置：`documentId`, `page`, `bbox`, `fieldKey`, `expected`, `actual`, `confidence`。

| 规则 ID | 类别 | 严重性 | 判断逻辑 | 结论示例 |
|---|---|---|---|---|
| `FDH_DOC_REQUIRED_MISSING` | 缺件 | FAIL | Entry visa 必交项 1/2/3/5/7/8/9/10 缺失；附件 6 在 ID 988A 声明有 HKID 时缺失 | `缺少 Helper travel document copy` |
| `FDH_DOC_NOT_REQUIRED_UPLOADED` | 错件/多余件 | WARN | Entry visa 上传 release letter 或 continuous employment letter，但申请类型不是 change employer / remaining period | `上传了当前场景不适用材料` |
| `FDH_DOC_WRONG_TYPE` | 错件 | FAIL/REVIEW | 文件分类结果与用户选择/文件名/页脚不一致，且置信度低于阈值 | `疑似把 ID 988B 上传为 ID 988A` |
| `FDH_DOC_DUPLICATE_UPLOAD` | 重复上传 | WARN/FAIL | `file_hash` 相同，或页面感知 hash 高度相似，且归入同一附件类别 | `同一护照页重复上传 2 次` |
| `FDH_PAGE_COUNT_ABNORMAL` | 页数异常 | FAIL/REVIEW | ID 988A 非 5 页、ID 988B 非 4 页、ID 407 非 4 页；支持材料页数明显异常 | `ID 988B 仅 3 页，疑似缺页` |
| `FDH_TEMPLATE_VERSION_MISMATCH` | 模板错误 | REVIEW/FAIL | 页脚 ID 不在模板库允许版本内，或页脚缺失但版面无法匹配 | `ID 988A 版本不是 06/2024` |
| `FDH_SIGNATURE_MISSING` | 签名缺失 | FAIL | ID 988A 申请人签名、ID 988B 雇主签名、ID 407 雇主/傭工签名缺失 | `ID 407 第 2 页缺 Helper signature` |
| `FDH_PHOTO_MISSING` | 相片缺失 | FAIL | ID 988A 申请类别为 1(a) Entry visa，但第 2 页相片区域为空 | `ID 988A 未贴近照` |
| `FDH_NOTARISATION_MISSING` | 印章/公证缺失 | REVIEW/FAIL | ID 407 如根据相关领事馆要求应公证但未检测到公证章/签注 | `合约未发现领事馆公证痕迹，需人工确认` |
| `FDH_TRANSLATION_MISSING` | 翻译缺失 | REVIEW/FAIL | 非中文/英文证明材料未随附认证翻译件 | `地址证明非中英文且未见认证翻译` |
| `FDH_MATERIAL_EXPIRED_ADDRESS` | 材料过期 | FAIL | 地址证明 `issue_date` 距申请提交日超过 3 个月 | `地址证明超过 3 个月` |
| `FDH_MATERIAL_EXPIRED_PASSPORT` | 材料过期 | FAIL | 护照到期日早于预计逗留结束前 1 个月 | `护照有效期不足以覆盖拟准许逗留期` |
| `FDH_APPLICATION_TYPE_INVALID` | 字段缺失/错选 | FAIL | ID 988A 未勾选 1(a) Entry visa，或多选互斥申请类型 | `ID 988A 申请类别双勾选` |
| `FDH_HELPER_NAME_MISMATCH` | 字段一致性 | FAIL/REVIEW | ID 988A、护照、ID 407、testimonial 中傭工英文姓名标准化后不一致 | `Helper name 与护照不一致` |
| `FDH_HELPER_TRAVEL_DOC_MISMATCH` | 字段一致性 | FAIL | ID 988A 旅行证件号/国籍/出生日期与护照不一致 | `Travel document no. 不一致` |
| `FDH_EMPLOYER_ID_MISMATCH` | 字段一致性 | FAIL | ID 988B 雇主姓名/证件号与雇主 ID/passport 不一致 | `Employer HKID no. 不一致` |
| `FDH_EMPLOYER_ADDRESS_MISMATCH` | 字段一致性 | FAIL/REVIEW | ID 988B residential address、ID 407 Clause 3 地址、地址证明地址不一致 | `合约地址与地址证明不一致` |
| `FDH_FINANCIAL_THRESHOLD_NOT_MET` | 业务规则 | FAIL | 财力证明无法支持每名傭工 HK$15,000/月或可比资产；ID 988B 声明为 No | `雇主收入声明低于资格门槛` |
| `FDH_FINANCIAL_PERIOD_INSUFFICIENT` | 材料异常 | REVIEW/FAIL | 3 个月工资/银行流水不足，或 6 个月资产证明不足 | `银行流水仅覆盖 1 个月` |
| `FDH_CONTRACT_WAGE_BELOW_MAW` | 业务规则 | FAIL | ID 407 工资低于签约日适用 MAW；2025-09-30 后签约低于 HK$5,100 | `合约工资低于当前 MAW` |
| `FDH_FOOD_ALLOWANCE_BELOW_BASELINE` | 业务规则 | FAIL | ID 407 未提供免费膳食且津贴低于当前不少于 HK$1,236/月 | `膳食津贴低于最低基线` |
| `FDH_CONTRACT_DURATION_INVALID` | 业务规则 | REVIEW/FAIL | ID 407 合约期不是 2 年，且没有可解释例外 | `合约期非 24 个月` |
| `FDH_LIVE_IN_ADDRESS_INVALID` | 业务规则 | FAIL | ID 407/ID 988A/ID 988B 承诺与住址信息显示傭工不住在雇主合约地址，且无历史例外 | `疑似不符合 live-in 要求` |
| `FDH_HOUSEHOLD_COUNT_MISMATCH` | 字段一致性 | REVIEW/FAIL | ID 988B 住户成员数量与 ID 407 附录第 2 段不一致 | `住户成员数不一致` |
| `FDH_TESTIMONIAL_EXPERIENCE_INSUFFICIENT` | 业务规则 | FAIL/REVIEW | testimonial 或 ID 988A 工作经历显示家庭傭工经验少于 2 年 | `未证明至少 2 年家庭傭工经验` |
| `FDH_ADDITIONAL_HELPER_LETTER_MISSING` | 条件必交 | REVIEW/FAIL | ID 988B 勾选 Additional，但未附增聘原因、住宿安排和工作分配说明 | `增聘傭工说明信缺失` |
| `FDH_SIGNATURE_INCONSISTENT` | 签名一致性 | REVIEW | Helper 在 ID 988A/ID 407 的签名图像差异过大，或 employer 在 ID 988B/ID 407 的签名差异过大 | `签名样式差异较大，建议人工复核` |

## 6. 结论输出建议

每宗 case 建议输出两层结果：

1. `documentInventoryConclusion`：面向文件分類，说明缺件、错件、重复上传、页数异常、模板版本问题。
2. `crossCheckConclusion`：面向數據核驗，说明字段缺失、一致性、过期、签名/印章、业务规则失败。

示例结构：

```json
{
  "caseType": "FDH_ENTRY_VISA_NEW_HELPER",
  "overallStatus": "REVIEW",
  "documentInventory": [
    {
      "documentType": "ID988A",
      "templateId": "id988a_2024_06",
      "pageCount": 5,
      "status": "PASS"
    }
  ],
  "findings": [
    {
      "ruleId": "FDH_CONTRACT_WAGE_BELOW_MAW",
      "severity": "FAIL",
      "fieldKey": "contract.monthly_wage_hkd",
      "expected": ">= 5100 for contracts signed on or after 2025-09-30",
      "actual": "4990",
      "evidence": {
        "documentType": "ID407",
        "page": 1,
        "bbox": null
      },
      "conclusion": "合约工资低于当前 FDH 最低工资，建议退回补正或人工复核。"
    }
  ]
}
```

## 7. 与当前 demo 的衔接点

当前项目已验证 `id407_2016_11`, `id988a_2024_06`, `id988b_2024_06` 的页脚/模板识别方向，并已有页面级展示、字段 crop、高亮和局部二次识别基础。下一步建议按本台账拆成三类配置：

| 配置 | 建议文件 | 用途 |
|---|---|---|
| 附件 taxonomy | `fdh-document-types.json` | 定义每个 case type 的必交/条件必交/不适用附件。 |
| 字段 schema | `fdh-field-schema.json` | 定义字段 key、来源模板、页码/锚点、标准化规则、必填条件。 |
| 规则集 | `fdh-cross-check-rules.json` | 定义规则 ID、严重性、适用条件、输出文案和 evidence 要求。 |

验收样本建议至少覆盖：完整合格件、缺 ID 407、ID 988B 缺页、ID 988A 双勾选、护照过期、工资低于 MAW、地址证明过期、雇主财力不足、testimonial 少于 2 年、ID 407 缺签名/公证、重复上传护照、错上传 release letter。
