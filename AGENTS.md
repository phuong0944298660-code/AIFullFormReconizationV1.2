# AGENTS.md

## 根目录文件夹用途

- `backend/`：Java 17 + Spring Boot 后端，承载材料 review API、任务编排、字段组装、结论生成与 OCR/LLM 调用入口。
- `frontend/`：Vue 3 + Vite 前端，承载上传页、识别结果页、JSON 视图、核验结果页和主要 UI 样式。
- `ocr-service/`：可选 field OCR sidecar 服务，默认不启动；只有明确使用 `-WithSidecar` 时才参与本地流程。
- `data/`：模板分类、材料识别规则和 demo 配置类数据，不放本地凭据。
- `docs/`：项目说明、测试材料、业务文档和人工整理资料；忽略 WPS 临时锁文件如 `docs/~$*.xlsx`。
- `models/`：本地模型或模型相关资源目录；不要提交大型模型产物或本地私有权重，除非明确确认。
- `memory/`：本地运行或工具使用产生的记忆/上下文资料，通常不属于产品代码。
- `logs/`：本地前后端启动日志和调试日志，不提交。
- `tmp/`：本地服务状态、临时运行文件和脚本状态文件，例如 `tmp/local-services.json`。
- `tmp-debug/`：历史调试截图、裁剪图和旧端口日志，用于排查问题；不属于运行必需目录，清理前需确认没有正在引用的调试材料。
- `outputs/`：识别、导出或调试输出结果，通常是生成物，不提交。
- `.paddlex_cache/`：PaddleX/OCR 相关本地缓存，属于工具缓存；可按需清理，但不要混入业务改动提交。
- `.playwright-cli/`：Playwright 或前端验证工具缓存/运行目录，属于本地测试辅助产物。
- `.superpowers/`、`.annex_work/`：Codex/agent 工具运行目录，属于本地协作或自动化辅助产物。
- `.vite/`：Vite 缓存目录。根目录下的 `.vite/` 通常是误在仓库根目录启动 Vite 产生的缓存；保留目录无害，确认无效的 `deps` 缓存可清理。
- `%MAVEN_HOME%/`：异常 Maven 本地仓库路径产物，通常来自环境变量未展开导致 Maven 把 `%MAVEN_HOME%/conf/settings.xml` 当作相对路径使用；不应作为正常项目目录继续写入，修复 Maven 环境后再确认是否清理。
- `.git/`：Git 仓库元数据，不要手动改动。

## 项目背景

本仓库是从其他目录复制出来的项目变体，用于演示香港入境处材料核验流程。不要默认沿用原项目的端口、脚本说明或 README 中的旧信息。

当前 demo 范围：
- 前端：Vue 3 + Vite，目录为 `frontend/`。
- 后端：Java 17 + Spring Boot，目录为 `backend/`。
- 识别与结构化提取：默认使用本地 OpenAI-compatible 多模态 LLM。
- 可选 OCR sidecar：目录为 `ocr-service/`，默认不启动；只有明确使用 `-WithSidecar` 时才启动。
- 当前默认演示流程：学生出入境 IANG 应届毕业生在港首次申请。
- 可切换演示流程：FDH Entry Visa 外籍家庭佣工入境审核。
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

启动脚本会从 `llm.local.cmd` 读取本地模型凭据，也会读取可选的 `baidu-ocr.local.cmd`。不要提交真实 API key 或本地凭据文件。

## 学生 / IANG Demo 规则

学生 IANG demo 是当前默认首屏流程，面向“IANG 应届毕业生在港首次申请”材料核验。

当前 IANG demo 的最终通过 / 不通过范围：
- 材料 1-4 是 Demo 审批范围：`ID 990A`、学历 / 毕业资格证明、港澳通行证 / 护照 / 香港身份证、付款状态 / 申请费付款截图。
- `ID 990A` 当前按页尾页码识别前 5 页。
- 付款状态如果显示 `NOT YET COMPLETE` 或付款未完成，应保留为 `review`，不要自动通过。
- 材料 5-8 可以展示官方应交、条件应交或后续阶段状态，但当前 demo 不纳入最终阻断判断。

IANG 字段核验重点：
- 申请人姓名、HKID、旅行证件号、出生日期应在申请表、学历证明、身份及旅行证件、付款记录之间交叉核验。
- 学历 / 毕业资格证明用于核验院校、课程、毕业日期，以及是否符合应届窗口。
- 付款页可脱敏展示；脱敏或未完成付款应进入 `review`，不要自动判为 `pass`。

## FDH Demo 规则

FDH review demo 不是通用文档总结器，必须保持“规则优先”的处理链路：
- 先通过页尾标识（例如 `ID 988A (06/2024)`）、页数、页面结构识别材料类型。
- 识别出材料类型后，再进入对应模板规则。
- 有确定性模板信号时，不要让 LLM 判断整页材料类型。
- `ID 988A (06/2024)` 第 5 页不需要字段识别。
- `ID 988B (06/2024)` 第 4 页不需要字段识别。
- 当前并发默认值：2 份文件并行、单文件 2 页并行。

FDH demo 的最终通过 / 不通过范围：
- 材料 1-3 是最终阻断范围：`ID 988A`、`ID 988B`、`ID 407`。
- 材料 4-12 可以展示官方应交或条件应交状态，但当前 demo 不纳入最终阻断判断。
- 如果材料 4-12 有上传，仍需展示是否已上传。

## 字段核验与结论规则

标准化字段按案件与文档、申请人 / 雇工、雇主、合约、学历、付款等业务分组。

跨文件字段展示规则：
- 展示该字段在每份材料中的来源，包括文件名称、局部快照、字段名称、识别值。
- 如果字段经历纠偏或裁定步骤，需要展示“建议采用值”，但字段状态仍保持为 `review`，除非对应流程明确允许语义一致自动通过。
- 不要把经历纠偏的字段自动改成 `pass`。
- 明显跨文件不一致应作为阻断或待复核问题，具体取决于置信度和差异严重程度。
- 低置信度、不可读、或轻微 OCR 差异，应进入 `review`，不要自动通过。

核验结果页是本 demo 的 Minutes 草拟能力：
- 整体结论与案件摘要合并展示。
- 按官方字段顺序，渲染类似模板表单的标准化字段表。
- 字段状态仅使用：通过、未识别、必填未填写、待复核。
- 核验结果页不展示不适用字段。

## 重要前端文件

- `frontend/src/App.vue`：demo 主界面，包括流程切换、上传页、识别结果页、JSON tab、核验结果页。
- `frontend/src/studentIangMockData.js`：学生 / IANG mock 场景、材料与字段数据。
- `frontend/src/fdhMockData.js`：FDH mock 场景、材料与字段数据。
- `frontend/src/fieldAdjudication.js`：本地字段裁定兜底，以及后端 LLM 建议采用值合并逻辑。
- `frontend/src/verificationTemplate.js`：核验结果 / Minutes 风格模板生成逻辑。
- `frontend/src/styles.css`：主要 UI 样式。

## 重要后端文件

- `backend/src/main/java/com/aiform/id995a/controller/FdhReviewController.java`：材料 review API，当前同时承载 FDH 与学生 IANG review 请求。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewJobService.java`：多文件 review 任务编排。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewAssembler.java`：FDH 核验结果组装逻辑。
- `backend/src/main/java/com/aiform/id995a/fdh/StudentIangReviewAssembler.java`：学生 / IANG 核验结果组装逻辑。
- `backend/src/main/java/com/aiform/id995a/fdh/StudentIangMaterialCatalog.java`：学生 / IANG 材料清单与材料分类规则。
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

重点后端验证：

```powershell
cd backend
mvn '-Dtest=FdhReviewConclusionServiceTest,FdhReviewControllerTest,FdhReviewAssemblerTest,FdhReviewJobServiceTest' test
```

## Git 与文件处理约定

- 工作区可能包含用户或生成工具造成的无关改动，不要擅自回退，除非用户明确要求。
- 忽略 WPS 临时锁文件，例如 `docs/~$*.xlsx`。
- 不要提交日志、本地凭据、临时生成文件或真实 API key。
- 提交时尽量只包含与当前需求相关的文件。
- 创建 git commit 时，提交说明必须备注本次更新了什么内容、变更背景，并用分点形式阐述关键改动。
- 上传至云端 Git 前，必须先向用户总结本次改动需求以及实际修改内容，并用分点形式阐述。
- 任何提交到云端 Git 的操作（包括 `git push`、创建 PR 或向远端分支发布提交）必须先获得用户明确同意。
- 推送时遵循用户指定分支。当前用户常用分支为 `dev` 和 `UAT`。
- 粤在信 Git 远端为 `http://192.168.5.221:8081/yuezaixin/immd2.git`。
- 后续提交或推送到粤在信 `master` 分支前，必须先获得用户明确同意。
