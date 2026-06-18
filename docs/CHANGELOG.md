# 变更记录 (CHANGELOG)

## 客户第一次演示版本 — 2026-06-18

> **里程碑：客户第一次演示。**
> 本次版本面向客户完成首次系统演示，**开始放开所有识别到的字段**（不再仅限部分字段），
> 并新增申请类型选择识别、官方页码识别、复核决策与核验通知等能力。
>
> ⚠️ **尚未完全完成**：识别与核验链路已基本打通、可演示，但部分字段的准确率、
> 裁剪精炼规则与复核决策逻辑仍在持续完善中，**不代表最终交付质量**。

### 重点说明

- **放开所有识别字段**：结构化抽取（`StructuredExtractionClient`）与选择字段裁剪精炼
  （`SelectionFieldCropRefinementService`）扩展到全量字段；前端（`App.vue` / `fieldAdjudication`）
  同步展示所有识别结果，不再只呈现部分字段。
- **新增识别能力**：申请类型选择识别、官方页码识别（LLM gateway + FDH 官方页码检测器）。
- **复核与通知**：新增复核决策（`reviewDecision`）与核验通知（`verificationNotice`）。

### 后端 (backend)

- **FDH 审核**
  - 新增 `FdhOfficialPageNumberDetector`：官方页码检测。
  - `FdhReviewAssembler` / `FdhReviewConclusionService` / `FdhReviewJobService` /
    `FdhReviewDocument`：完善核验组装、结论生成、作业调度与审核文档模型。
- **LLM**
  - 新增 `ApplicationTypeSelectionRecognitionGateway` / `Result`：申请类型选择识别。
  - 新增 `OfficialPageNumberRecognitionGateway` / `Result`：官方页码识别。
  - `StructuredExtractionClient`：结构化抽取扩展至全量字段（+280 行）。
- **OCR**
  - `SelectionFieldCropRefinementService`：选择字段裁剪精炼大幅扩展（+593 行）。
  - `OcrDemoService`：演示服务小调整。
- **测试**：同步新增/更新对应单元测试（官方页码检测器、核验组装、结论、作业、抽取客户端、裁剪精炼）。

### 前端 (frontend)

- `App.vue`：放开所有识别字段的展示（+88 行调整）。
- `fieldAdjudication`：字段裁定逻辑完善。
- 新增 `reviewDecision`：复核决策。
- 新增 `verificationNotice`：核验通知。

### 数据与文档

- `data/template-classification.json` / `docs/template-classification.md`：模板分类更新。
- 新增 `docs/RFI/`：RFI v2 文档、v1/v2 对比分析及中文译本。

### 工程

- `.gitignore`：忽略 Office 锁定临时文件（`~$*`）。

### 已知未完成 / 后续

- 部分字段识别准确率与裁剪精炼规则待优化。
- 复核决策与核验通知逻辑需根据演示反馈持续完善。
- 上述能力为演示用途，最终交付前需补充覆盖与回归测试。
