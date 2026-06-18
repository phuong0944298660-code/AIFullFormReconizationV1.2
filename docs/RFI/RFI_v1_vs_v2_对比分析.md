# ImmD AI RFI v1 vs v2 對比分析

> **文件用途**：對比兩版 RFI 的差異，識別範圍、技術、合規與商務變化，為 RFQ 響應與方案調整提供依據  
> **對比文件**：
> - v1: `RFI for Artificial Intelligence Total Solutions_v1.pdf` (April 2026)
> - v2: `RFI for Artificial Intelligence Solution_v2.pdf` (June 2026)  
> **編制日期**：2026-06-16  
> **密級**：INTERNAL

---

## 一、核心結論（Executive Summary）

v2 並非單純修訂，而是一次<strong>範圍收窄但合規強化</strong>的重新發包：

1. <strong>從「全面 APPLIES-2」收窄為「Pilot 試點」</strong>，先試行學生/IANG（Phase 1），再擴展至外傭 FDH（Phase 2）。
2. <strong>ITAI / 信創（Xinchuang）從「參考」升級為「戰略要求」</strong>，硬件原產地、軟件列名、國標 GB 合規成為強制門檻。
3. <strong>業務量重新拆分</strong>：年度總量由 50 萬略升至 54.5 萬，但峰值由單一 500 案/小時，拆分為 Phase 1 265 + Phase 2 235 案/小時。
4. <strong>AI 軟件規格更明確</strong>：明確要求支持 Qwen3.5+/Qwen3-VL+、1,300 input / 50 output tokens。
5. <strong>數據保留與審計要求更嚴</strong>：新增 AI 應用結果與推理/處理日誌 1 年歸檔保留。

對 CMHK 而言，v2 帶來的主要挑戰是<strong>國產化適配與試點範圍的重新 Sizing</strong>；主要機會是競爭對手同樣面臨信創門檻，GW 的 Qwen 路線與我們的提前佈局較為匹配。

---

## 二、重大差異一覽表

| # | 差異維度 | v1 (April) | v2 (June) | 影響等級 |
|---|---------|-----------|-----------|---------|
| 1 | **項目名稱 / 性質** | Supply of AI Total Solutions for APPLIES-2 | Supply of **Pilot** AI Infrastructure and Implementation Solutions for Visa/Entry Permit Applications | 🔴 高 |
| 2 | **實施範圍** | 面向 APPLIES-2 系統整體 | 分 **Phase 1（Student/IANG）+ Phase 2（FDH）** 兩階段試點 | 🔴 高 |
| 3 | **年度申請量** | ~500,000 | Phase 1 ~135,000 + Phase 2 ~410,000 = **~545,000** | 🟡 中 |
| 4 | **峰值處理量** | ~500 案/小時（單一） | Phase 1 **265** 案/小時 + Phase 2 **235** 案/小時 | 🔴 高 |
| 5 | **提交截止日期** | 15 May 2026 | **6 July 2026** | 🟡 中 |
| 6 | **ITAI / 信創要求** | 僅在 Appendix B/C 作為參考要求 | 正文 **4.3.6.1** 明確為戰略要求：要求列入中國信息安全測評中心、原產地 PRC、或開源 | 🔴 高 |
| 7 | **AI Server 功耗上限** | ≤ 11 kW | ≤ **16 kW**（並新增 HBM 要求） | 🟡 中 |
| 8 | **模型版本要求** | Qwen / QwenVL | **Qwen3.5 or later / Qwen3-VL or later** | 🟡 中 |
| 9 | **推理 Token 規格** | 未明確 | 明確 ≥ **1,300 input / 50 output tokens per request** | 🟡 中 |
| 10 | **非生產環境** | DEV, SIT, UAT | DEV, SIT, **iSIT**, UAT, **iUAT**, **fire-fighting environment** | 🟡 中 |
| 11 | **數據保留期限** | App info 30天；Audit logs 60天 | 新增 AI Result / Reasoning log 60天線上 + **1年歸檔** | 🟡 中 |
| 12 | **實施變更人天** | 實施期未列，維護期 140人天/年 | **實施期 120人天**，維護期 140人天/年 | 🟡 中 |
| 13 | **備份磁帶數量** | 20 tapes | **10 tapes** | 🟢 低 |
| 14 | **新增業務聯繫人** | Mr. LAU Wing-fat | 新增 **Mr. YAU Ka-wai**（業務） | 🟢 低 |
| 15 | **新增報表功能要求** | 無 | 4.3.5.7 要求提供 AI **audit trail / statistics report / printing** | 🟡 中 |
| 16 | **維護團隊駐地** | 未特別強調 | 4.4.15 要求維護團隊在 **Hong Kong 境內**有足夠人手 | 🟡 中 |

---

## 三、分章節詳細對比

### 3.1 第一章：項目名稱與範圍（Section 1-3）

| 項目 | v1 | v2 | 備註 |
|------|----|----|------|
| 標題 | AI Total Solutions for APPLIES-2 | **Pilot** AI Infrastructure and Implementation Solutions for Visa/Entry Permit Applications | 從「全面」改為「試點」 |
| 日期 | April 2026 | June 2026 | 重新發包 |
| 截止日 | 15 May 2026 | **6 July 2026** | 延長約 7 週 |
| 主要聯繫人 | LAU Wing-fat（業務）、William Mok（技術） | **新增 YAU Ka-wai**（業務），William Mok（技術） | 業務窗口增加 |

### 3.2 業務需求（Section 4.1）

| 項目 | v1 | v2 | 影響 |
|------|----|----|------|
| 年度申請量 | ~500,000 | Phase 1 ~135,000（Student/IANG）+ Phase 2 ~410,000（FDH） | 總量略升，但分階段實施 |
| 峰值處理量 | ~500 / hour | Phase 1 **265 / hour** + Phase 2 **235 / hour** | 單階段峰值下降，但需支持兩種業務模式 |
| 每案附件數 | ~12 | ~12 | 不變 |
| 年增長率 | 7% / 5年 | 7% / 5年 | 不變 |
| 新增描述 | 較簡單 | 4.1.3 明確強調「automating document handling, improving data verification, assisting case officers」 | 與現有方案表述一致 |

### 3.3 主要功能（Section 4.2）概要

兩版功能模組相同（Document Classification / Data Verification / Minutes Drafting），但 v2 描述更細緻、條款數量明顯增加，並將多項原本模糊的要求具體化。以下為概要，<strong>逐條變更詳見下一節「4.2 Major Functions 變更記錄」</strong>。

| 功能 | v2 新增 / 強化內容 |
|------|------------------|
| **Document Classification** | 新增「image enhancement, orientation correction, cropping」等預處理要求；要求輸出 rectified documents；強調 audit trails |
| **Data Verification** | 明確需識別「missing signatures, incomplete fields, inconsistent information, low-quality images」；要求 validation status 與低置信度標記 |
| **Minutes Drafting** | 明確輸出 PDF/DOCX；強調與前兩功能數據整合 |
| **Combined Workflow** | 新增支持 batch file transfers 等整合機制；強調 workflow status updates |
| **AI Requirements** | 新增 **Qwen3.5 or later / Qwen3-VL or later** 模型支持要求；推理規格 ≥1,300 input / 50 output tokens |

---

### 3.4 系統需求（Section 4.3）

| 項目 | v1 | v2 | 影響 |
|------|----|----|------|
| **ITAI / 信創** | Appendix B/C 參考要求 | **4.3.6.1 正文強制**：戰略性採用 ITAI/Xinchuang；硬件/軟件須列入中國信息安全測評中心；原產地 PRC 或開源 | 🔴 重大合規門檻 |
| **非生產環境** | DEV / SIT / UAT | DEV / SIT / **iSIT** / UAT / **iUAT** / **fire-fighting** | 環境數量增加，成本上升 |
| **AI Server 功耗** | ≤ 11 kW | ≤ **16 kW** + **HBM** 要求 | 對散熱與電力規劃影響大 |
| **Key Management** | 覆蓋 SAN/backup | 新增覆蓋 **local storages** | 範圍擴大 |
| **AI 報表/打印** | 無 | 4.3.5.7 要求 audit trail / statistics report / printing | 需新增報表模組 |

### 3.5 數據保留（Section 4.3.15）

| 數據類型 | v1 | v2 |
|---------|----|----|
| Application info | 30 days（不含個人資料） | 30 days online（**含個人資料**） |
| AI 應用結果 | 未列明 | **60 days online + 1 year archived** |
| Reasoning/Processing log | 未列明 | **60 days online + 1 year archived** |
| Audit logs | 60 days | **60 days online + 1 year archived** |

### 3.6 實施與維護（Section 4.3.14 / 4.4）

| 項目 | v1 | v2 |
|------|----|----|
| Phase 1 UAT | Month 5-6 | Month 5-6 |
| **Production Trial Run** | 無 | **Month 6（新增）** |
| Phase 1 User Training | Month 7 | Month 6-7 |
| 實施期變更人天 | 未明確 | **120 人天** |
| 維護期變更人天 | 140 人天/年 | 140 人天/年 |
| 維護團隊駐地 | 未特別強調 | 要求在香港境內有足夠人手 |
| 合規評估 | PIA / ITSRAA | PIA / ITSRAA / **AI Application Impact Assessment** |
| 備份磁帶 | 20 tapes | 10 tapes |

### 3.7 附錄硬件 / 軟件要求

| 項目 | v1 | v2 |
|------|----|----|
| AI Server 功耗 | ≤ 11 kW | ≤ **16 kW** |
| AI Server 記憶體 | ≥ 1,024 GB | ≥ 1,024 GB + **HBM** |
| 軟件模型支持 | Qwen, QwenVL | **Qwen3.5 or later, Qwen3-VL or later** |
| Token 規格 | 未明確 | ≥ 1,300 input / ≥ 50 output tokens |
| 殺毒軟件兼容 | Trellix 必須 | Trellix 或與 EDR 整合 |

---

## 四、4.2 Major Functions 變更記錄（逐條對比）

> 本節單獨對比兩版 RFI 中 **Section 4.2 Major Functions of the New AI Solution(s)** 的逐條差異。v2 在功能劃分上由 4 個一級條款（4.2.1–4.2.4）擴展為 6 個（4.2.1–4.2.6），子條款總數由 17 條增至 28 條，且大量增加 **audit trail / processing records / validation status / confidence indicators** 等可追溯性要求。

### 4.2 標題與總述

| 項目 | v1 | v2 | 變化 |
|------|----|----|------|
| 標題 | Major Functions of the New AI **Solutions** | Major Functions of the New AI **Solution** | 由複數改為單數，呼應 Pilot 性質 |
| 總述 | 無 | 新增總述，列出三大功能（Document Classification / Data Verification / Minutes Drafting）並說明「The following major functions are identified that the proposed AI system is to facilitate」 | 新增 |

### 4.2.1 Automated Document Classification

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.1.1 | Shall be able to **support verification** on the completeness of application's documents and images according to business/workflow logic | Shall **automatically validate** the completeness of application data (including multi-format documents and images) against **predefined business rules and workflow requirements** | ① 從「支持驗證」升級為「自動驗證」；② 對象從 documents/images 擴展為 application data；③ 強調 predefined business rules | 🔴 高 |
| 4.2.1.2 | Shall be able to provide and support subsequent classification tasks | Shall support document **pre-processing and rectification**, including **image enhancement, orientation correction, cropping**, and other preparation activities required for classification and verification | 完全替換為預處理與矯正要求 | 🔴 高 |
| 4.2.1.3 | Shall be able to check, handle and alert users for problematic items | Shall classify submitted documents and images into **predefined document categories** and provide classification results with **relevant confidence indicators** where applicable | 原「問題項告警」內容移至 4.2.1.4；此處改為「分類到預定義類別 + 置信度指標」 | 🟡 中 |
| 4.2.1.4 | Shall generate and export related statistics data and reports | Shall identify and detect **missing, incomplete, duplicated, unclear, problematic, or irregular** documents and alert users for review or follow-up action | ① 統計報告內容移至 4.2.1.6；② 此處承接原 4.2.1.3 的問題檢測，並大幅擴展問題類型（新增 duplicated, unclear, irregular） | 🟡 中 |
| 4.2.1.5 | — | Shall generate classification results and export relevant files, including **rectified documents**, to the Intelligent Data Verification function and other interfacing ImmD Systems as required | **新增**：明確要求輸出矯正後文件至 Data Verification 模組 | 🟡 中 |
| 4.2.1.6 | — | Shall generate and export related statistics, **audit trails, processing records**, and reports for document classification activities | **新增**：擴展統計範圍至審計追蹤與處理記錄 | 🟡 中 |

### 4.2.2 Intelligent Data Verification

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.2.1 | Shall be able to extract information from unstructured data such as documents and images | Shall extract relevant information from unstructured data sources, e.g. documents with **hand-writing** and images for further processing | 明確提及**手寫內容**（hand-writing）識別 | 🟡 中 |
| 4.2.2.2 | Shall be able to cross-reference the extracted information and other structured data provided by applicants, and automatically identify inconsistencies or incomplete data | Shall compare extracted information submitted by applicants or obtained from **interfacing systems** to identify inconsistencies, **missing data, or conflicting information** | ① 數據來源擴展至 interfacing systems；② 增加 conflicting information | 🟡 中 |
| 4.2.2.3 | Shall be able to check and identify any incomplete problematic or irregular contents according to business/workflow logic, such as identifying incomplete information, unclear stamps, etc | Shall **validate** extracted and structured data against **predefined business rules, workflow logic, and compliance requirements** | 重大：從「識別問題內容」轉變為「根據業務規則與合規要求驗證數據」 | 🔴 高 |
| 4.2.2.4 | Shall be able to crop / highlight the unstructured data in original copy for user to check the problematic or irregular contents | Shall identify incomplete, problematic, or irregular content, including unclear stamps, **missing signatures, incomplete fields, inconsistent information, or low-quality images** | ① cropping/highlighting 功能移至 4.2.2.5；② 問題類型明確擴展（新增 missing signatures, low-quality images 等） | 🟡 中 |
| 4.2.2.5 | Shall generate and export related statistics data and reports | Shall **crop, highlight, or indicate** relevant areas in the original document or image to assist users in reviewing extracted data, discrepancies, and irregular content | ① 統計報告內容移至 4.2.2.7；② 此處承接 cropping/highlighting 功能 | 🟡 中 |
| 4.2.2.6 | — | Shall generate verification results, **indicate validation status**, and **flag low-confidence, inconsistent, or exceptional items** for user review | **新增**：明確驗證結果輸出格式與低置信度標記 | 🟡 中 |
| 4.2.2.7 | — | Shall generate and export related statistics, **audit trails, processing records**, and reports for the activities | **新增**：擴展統計與審計要求 | 🟡 中 |

### 4.2.3 Intelligent Minutes Drafting

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.3.1 | Shall be able to generate draft minutes based on minutes template (in DOCX format) | Shall generate draft minutes using predefined templates, e.g. in DOCX format | 措辭調整，含義基本一致 | 🟢 低 |
| 4.2.3.2 | Shall be able to generate minutes according to structured data, result of the above functions 4.2.1 and 4.2.2, and business/workflow logic | Shall generate draft minutes by **predefined rules and logics** | ① 原「根據前序功能結果生成」移至 4.2.3.3；② 此處強調預定義規則 | 🟡 中 |
| 4.2.3.3 | Shall generate and export related statistics data and reports | Shall **synthesize and auto-populate** the draft minutes (such as **PDF and DOCX format**) by consolidating data inputs from the Document Classification (4.2.1) and Data Verification (4.2.2) function, governed by business and workflow logic | 重大：① 承接原 4.2.3.2 的「前序功能數據整合」；② 明確輸出 **PDF 與 DOCX** | 🔴 高 |
| 4.2.3.4 | — | Shall export generated draft minutes and **related metadata** to other interfacing systems as required | **新增**：強調 metadata 輸出 | 🟡 中 |
| 4.2.3.5 | — | Shall generate and export related statistics, **audit trails, processing records**, and reports for the activities | **新增**：擴展統計與審計要求 | 🟡 中 |

### 4.2.4 Combined Workflow

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.4.1 | Shall be able to automatic integrate above functions 4.2.1, 4.2.2 and 4.2.3 into a streamline workflow | Shall be able to automatic integrate above functions 4.2.1, 4.2.2 and 4.2.3 into a streamline workflow | 無實質變化 | 🟢 低 |
| 4.2.4.2 | The new AI Solutions shall ingest case application data, e.g. structured and unstructured data from other ImmD systems and deliver the results—including data, images, and reports—to ImmD systems via API or dedicated interfaces | Shall ingest case application data—including structured data, documents, images, and supporting materials—from interfacing ImmD Systems via APIs, dedicated interfaces, **batch file transfers, or other agreed integration mechanisms** | 重大擴展：新增 **batch file transfers** 等整合機制；明確 supporting materials | 🔴 高 |
| 4.2.4.3 | — | Shall deliver the corresponding processing results—including classification results, extracted data, verification results, highlighted images, draft minutes, reports, and **workflow status updates**—back to the interfacing systems | **新增**：明確輸出內容清單與 workflow status updates | 🟡 中 |
| 4.2.4.4 | — | Shall generate and export related statistics, **audit trails, processing records**, and reports | **新增**：擴展統計與審計要求 | 🟡 中 |

### 4.2.5 AI Requirements

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.5.1 | Shall utilize cutting-edge OCR and AI technology including Generative AI, e.g., Large Language Models (LLMs) | Shall utilize cutting-edge OCR and AI technology including Generative AI, e.g., Large Language Models (LLMs) | 無實質變化 | 🟢 低 |
| 4.2.5.2 | Shall implement a quantitative scoring mechanism for all AI processes to validate the accuracy | Shall implement a quantitative scoring mechanism for all **AI-driven processes** to validate the accuracy | 措辭微調 | 🟢 低 |
| 4.2.5.3 | Shall provide continuous model tuning and training services to ensure a sustained accuracy rate (e.g. 90%) | Shall provide continuous model tuning, training and **performance optimization services** to ensure a sustained accuracy rate (e.g. 90%) | 新增 **performance optimization** | 🟡 中 |
| 4.2.5.4 | Shall support various tuning and training methodologies including but not limited to supervised and unsupervised and RLHF | Shall support various tuning and training methodologies including but not limited to supervised and unsupervised and RLHF | 無實質變化 | 🟢 低 |
| 4.2.5.5 | Shall be able to export AI-generated results, data, images, and reports via standardized APIs or interfaces with internal ImmD System(s) | Shall export AI-generated outputs (data, images, documents, and reports) via standardized APIs or system interfaces to integrated internal ImmD Systems | 措辭調整，含義基本一致 | 🟢 低 |
| 4.2.5.6 | Shall provide standardized interface or API capture user feedback on AI results, driving higher accuracy and satisfaction | Shall provide standardized APIs or interfaces to capture user feedback on AI-generated results, in order to support continuous improvement of accuracy, **usability, and user satisfaction** | 新增 **usability, and user satisfaction** | 🟡 中 |
| 4.2.5.7 | Shall allow technical staff to perform advanced model tuning, retraining, versioning, and deployment across the system ecosystem | Shall allow technical staff to perform advanced model tuning, retraining, versioning, and deployment across the system ecosystem | 無實質變化 | 🟢 低 |

### 4.2.6 Interface with ImmD Systems

| 條款 | v1 原文 | v2 原文 | 變化說明 | 影響 |
|------|--------|--------|---------|------|
| 4.2.6.1 | Shall be integrate with existing workflow ImmD System(s) through interface or API for above functions | Shall be **integrated** with existing workflow ImmD Systems through interface or API for above functions | 語法修正 | 🟢 低 |
| 4.2.6.2 | Shall be able to receive application information including structured and unstructured data e.g. documents and images through interface or API of another internal ImmD System(s) | Shall be capable of receiving application information from internal ImmD Systems, including structured and unstructured data such as forms, documents, images, and other relevant files, through APIs or system interfaces | 措辭擴展，明確 forms 與 other relevant files | 🟡 中 |
| 4.2.6.3 | Shall be able to provide the result of above major functions such as Clause 4.2.1, 4.2.2 and 4.2.3 through interface or API to another internal ImmD System(s) | Shall be capable of providing generated results, including reports, documents, meeting minutes, extracted data, and outputs from the major functions, to internal ImmD Systems through APIs or system interfaces | 擴展：明確輸出類型清單 | 🟡 中 |
| 4.2.6.4 | Shall allow exchange and enquiry of data through interface or API of another internal ImmD System(s) provided by ImmD which is/are located in another network zone | Shall allow exchange and enquiry of data through system interfaces or APIs of another internal ImmD System(s) provided by ImmD which is/are located in another network zone | 措辭微調 | 🟢 低 |
| 4.2.6.5 | Shall develop interface or API in case the existing developed interface or API is not applicable to the new AI Solutions | — | **刪除**：v2 不再要求開發新接口 | 🟢 低 |

### 4.2 變更趨勢總結

v2 對 4.2 節的修改呈現以下 **5 大趨勢**：

1. **從「輔助能力」到「自動化能力」**：4.2.1.1 由「support verification」改為「automatically validate」，對 AI 主動性要求提升。
2. **預處理與矯正成為獨立要求**：4.2.1.2 明確要求 image enhancement / orientation correction / cropping，對 CV 前處理能力提出具體要求。
3. **可追溯性全面強化**：幾乎每個子模組都新增 **audit trails / processing records / statistics / reports** 輸出要求。
4. **結果輸出更細化**：明確 rectified documents、validation status、confidence indicators、workflow status updates、metadata 等輸出項。
5. **整合方式更多樣**：4.2.4.2 新增 batch file transfers，意味著除了 API 還需支持文件批次接口。

### 4.2 變更對 CMHK 方案的即時影響

| 影響點 | 說明 |
|--------|------|
| **GW 方案基本覆蓋，但需補充審計日誌設計** | GW 已有 document classification、data verification、minutes drafting 功能，但 v2 對 audit trails / processing records 的粒度要求需在 TP 中明確響應 |
| **预处理模块需独立說明** | v2 將 image enhancement / orientation correction / cropping 單獨列出，需在方案中獨立成章節 |
| **接口設計需兼容 batch file transfer** | 現有 GW Pull/REST 設計可能不足，需評估 SFTP / 共享文件夾等批次接口 |
| **Confidence indicator 與 validation status 成為標配** | 需在 UI、API、報表中統一體現，且與人工審核流程掛鉤 |

---

## 五、對 CMHK 的影響分析

### 5.1 需要重新評估的方面

| 方面 | v2 帶來的變化 | CMHK 應對 |
|------|--------------|-----------|
| **硬件選型** | 信創成為強制要求，AI Server 功耗上限提高 | 需重新評估昇騰 NPU / 海光 DCU / 寒武紀對 Qwen3.5-VL 的支持；放棄非國產 GPU 主方案 |
| **Sizing 計算** | 峰值拆分為 265 + 235 案/小時，且分兩階段 | 現有 Sizing Calculator 需按 Phase 1 / Phase 2 重新拆分，並為 Pilot 預留較小規模 |
| **報價策略** | Pilot 性質，範圍收窄但環境增加 | 報價需區分 Phase 1 Pilot 與 Phase 2 擴展，並預留 iSIT/iUAT/fire-fighting 環境成本 |
| **供應商方案** | 信創門檻提高 | 需確認 GW/HH/ZS 各自信創適配能力；GW 的 Qwen 路線較有利，但硬件層需補信創方案 |
| **數據合規** | AI 結果與推理日誌保留 1 年 | 存儲 Sizing 與備份策略需調整；需設計 reasoning log 結構化輸出 |
| **維護團隊** | 要求香港境內足夠人手 | 確保維護團隊本地化承諾可寫入合同 |

### 5.2 對現有工作的影響

| 已完成工作 | v2 影響 | 是否需要重做 |
|-----------|--------|------------|
| Sizing Calculator v17 | 業務量拆分、信創硬件替換、新增環境 | 🟡 需要更新 |
| 供應商 TP v0.7 評審 | 需補充信創適配與 Pilot 階段規劃 | 🟡 需要補充 |
| CMHK 整合草案 | 需按 v2 調整範圍與里程碑 | 🟡 需要更新 |
| V1/V2 Ballpark | 報價基礎變化較大 | 🔴 需要重新報價 |
| 供應商對比表 | 需新增信創與模型版本維度 | 🟡 需要更新 |

---

## 六、建議行動（Action Items）

### 5.1 即時行動（本週）

| # | 行動項 | 負責 | 優先級 |
|---|--------|------|--------|
| 1 | 向三家供應商發送 v2 RFI 澄清函，重點詢問：① 信創適配能力；② Qwen3.5-VL 在國產 NPU/GPU 上的推理支持；③ Pilot 分階段實施方案 | Solution Manager | 🔴 高 |
| 2 | 與硬件團隊緊急評估國產 NPU（昇騰 / 海光 / 寒武紀）對 Qwen3.5-VL 的適配性與性能 | Solution Manager + 硬件架構 | 🔴 高 |
| 3 | 更新 Sizing Calculator：按 Phase 1/Phase 2 拆分業務量，增加信創硬件選項，新增 iSIT/iUAT/fire-fighting 環境 | Solution Manager + 售前 | 🔴 高 |

### 5.2 短期行動（6 月底前）

| # | 行動項 | 負責 | 優先級 |
|---|--------|------|--------|
| 4 | 重新編制 CMHK 整合草案 v0.8，對齊 v2 Pilot 範圍與信創要求 | Solution Manager | 🟡 中 |
| 5 | 更新供應商對比矩陣，新增「信創適配 / 模型版本 / Token 規格 / 香港本地維護」維度 | Solution Manager | 🟡 中 |
| 6 | 重新核算 V3 Ballpark，區分 Phase 1 Pilot 與 Phase 2 擴展成本 | Solution Manager + 商務 | 🟡 中 |
| 7 | 與 ImmD 業務窗口（YAU Ka-wai）確認 Pilot 範圍優先級與 FDH 是否仍為最終目標 | Solution Manager | 🟡 中 |

### 5.3 中期行動（7 月 6 日提交前）

| # | 行動項 | 負責 | 優先級 |
|---|--------|------|--------|
| 8 | 完成 Annex I 表格填寫，重點體現信創合規、Pilot 分階段、香港本地支持 | Solution Manager + 供應商 | 🔴 高 |
| 9 | 準備 v2 RFI 響應文件，強調 CMHK 在信創領域的提前佈局與 GW Qwen 路線的匹配度 | Solution Manager | 🔴 高 |
| 10 | 法務審閱維護條款（香港境內人手、AI Impact Assessment、數據保留 1 年） | Solution Manager + 法務 | 🟡 中 |

---

## 七、對競爭格局的判斷

v2 的信創要求將構成顯著的<strong>進入門檻</strong>：

- **對港灣科技 GW**：軟件層 Qwen3.5-VL 路線符合 v2 模型要求，但硬件層需補充信創適配證明；若能在 7 月 6 日前提供昇騰/海光上的 PoC 數據，將大幅領先。
- **對合合信息 HH**：商業產品 DocFlow 若未做信創適配，門檻較高；且端到端 60% 準確率相較 v2 對準確率的隱含要求偏低。
- **對智算 ZS**：作為 DICT/硬件背景供應商，在信創硬件上可能有優勢，但軟件層能力待評估；需重點評估其是否能與 GW 軟件整合。

CMHK 的機會在於：已提前佈局 Qwen 系列 VLM 與國產化 GPU 調研，v2 反而強化了我們的差異化優勢。

---

## 八、風險提示

| 風險 | 等級 | 說明 |
|------|------|------|
| 信創適配時間不足 | 🔴 高 | 距 7 月 6 日僅約 3 週，若供應商無法及時提供國產 NPU 上的 Qwen3.5-VL 性能數據，將影響響應質量 |
| 硬件成本大幅上升 | 🟡 中 | 國產 AI 芯片單卡性能與生態成熟度可能低於國際 GPU，可能導致卡數與整機功耗上升 |
| Phase 1/Phase 2 範圍模糊 | 🟡 中 | 客戶未明確說明 Phase 2 是否自動啟動，報價與資源規劃需預留彈性 |
| 維護團隊本地化成本 | 🟡 中 | 4.4.15 要求香港境內維護人手，可能推高年度維護報價 |

---

*本對比分析基於 v1 與 v2 的 PDF 原文逐段比對完成。如需進一步細化某章節，可隨時補充。*
