# AGENTS.md

## 项目背景

本仓库是从其他目录复制出来的项目变体，用于演示香港入境处 FDH Entry Visa 材料核验流程。不要默认沿用原项目的端口、脚本说明或 README 中的旧信息。

当前 demo 范围：
- 前端：Vue 3 + Vite，目录为 `frontend/`。
- 后端：Java 17 + Spring Boot，目录为 `backend/`。
- 识别与结构化提取：默认使用本地 OpenAI-compatible 多模态 LLM。
- 可选 OCR sidecar：目录为 `ocr-service/`，默认不启动；只有明确使用 `-WithSidecar` 时才启动。
- 主要用户流程：申请材料上传 -> 文档解析识别 -> 字段结构化提取与归一 -> 跨档智能校验 -> 自动生成审核结论。

## 本地端口与启动方式

本项目是复制项目，必须使用新的本地端口，不要回退到原项目端口。

默认本地服务：
- 前端：`http://127.0.0.1:5197/`
- 后端：`http://127.0.0.1:18083`
- 可选 field OCR sidecar：`http://127.0.0.1:18092`

启动服务：

```powershell
.\start-local.ps1 -SkipBuild
```

停止服务：

```powershell
.\stop-local.ps1
```

启动脚本会从 `llm.local.cmd` 读取本地模型凭据。不要提交真实 API key 或本地凭据文件。

## FDH Demo 规则

FDH review demo 不是通用文档总结器，必须保持“规则优先”的处理链路：
- 先通过页尾标识（例如 `ID 988A (06/2024)`）、页数、页面结构识别材料类型。
- 识别出材料类型后，再进入对应模板规则。
- 有确定性模板信号时，不要让 LLM 判断整页材料类型。
- `ID 988A (06/2024)` 第 5 页不需要字段识别。
- `ID 988B (06/2024)` 第 4 页不需要字段识别。
- 当前并发默认值：2 份文件并行、单文件 2 页并行。

本 demo 的最终通过 / 不通过范围：
- 材料 1-3 是最终阻断范围：`ID 988A`、`ID 988B`、`ID 407`。
- 材料 4-12 可以展示官方应交或条件应交状态，但当前 demo 不纳入最终阻断判断。
- 如果材料 4-12 有上传，仍需展示是否已上传。

## 字段核验与结论规则

标准化字段按案件与文档、雇工、雇主、合约字段分组。

跨文件字段展示规则：
- 展示该字段在每份材料中的来源，包括文件名称、局部快照、字段名称、识别值。
- 如果字段经历纠偏或裁定步骤，需要展示“建议采用值”，但字段状态仍保持为 `review`。
- 不要把经历纠偏的字段自动改成 `pass`。
- 明显跨文件不一致应作为阻断或待复核问题，具体取决于置信度和差异严重程度。
- 低置信度、不可读、或轻微 OCR 差异，应进入 `review`，不要自动通过。

核验结果页是本 demo 的 Minutes 草拟能力：
- 整体结论与案件摘要合并展示。
- 按官方字段顺序，渲染类似模板表单的标准化字段表。
- 字段状态仅使用：通过、未识别、必填未填写、待复核。
- 核验结果页不展示不适用字段。

## 重要前端文件

- `frontend/src/App.vue`：FDH demo 主界面，包括上传页、识别结果页、JSON tab、核验结果页。
- `frontend/src/fdhMockData.js`：前端 mock 场景、FDH 材料与字段数据。
- `frontend/src/fieldAdjudication.js`：本地字段裁定兜底，以及后端 LLM 建议采用值合并逻辑。
- `frontend/src/verificationTemplate.js`：核验结果 / Minutes 风格模板生成逻辑。
- `frontend/src/styles.css`：主要 UI 样式。

## 重要后端文件

- `backend/src/main/java/com/aiform/id995a/controller/FdhReviewController.java`：FDH review API。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewJobService.java`：多文件 FDH review 任务编排。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewConclusionService.java`：LLM 辅助生成结论与字段裁定结果。
- `backend/src/main/java/com/aiform/id995a/ocr/`：模板识别、页面渲染、裁剪、字段提取辅助逻辑。

## 验证命令

前端：

```powershell
cd frontend
node --test src\*.test.js
node .\node_modules\vite\bin\vite.js build
```

后端：

```powershell
cd backend
mvn test
```

FDH 相关后端重点验证：

```powershell
cd backend
mvn '-Dtest=FdhReviewConclusionServiceTest,FdhReviewControllerTest' test
```

## Git 与文件处理约定

- 工作区可能包含用户或生成工具造成的无关改动，不要擅自回退，除非用户明确要求。
- 忽略 WPS 临时锁文件，例如 `docs/~$*.xlsx`。
- 不要提交日志、本地凭据、临时生成文件或真实 API key。
- 提交时尽量只包含与当前需求相关的文件。
- 推送时遵循用户指定分支。当前用户常用分支为 `dev` 和 `UAT`。

