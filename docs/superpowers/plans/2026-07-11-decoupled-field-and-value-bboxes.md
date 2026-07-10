# 字段与值 bbox 完全解耦 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 OCR 字段名 bbox 进行前端高亮，同时使用 LLM 原始值 bbox 裁剪裁判图片并恢复实际评分展示。

**Architecture:** `StructuredFieldEvidenceService` 将 OCR 标签定位与 LLM 值坐标解析为两条独立数据流。公共高亮 bbox 只取 OCR `labelBbox`，裁判截图只从 LLM `valueBbox` 生成；前端不再优先使用合并证据 bbox，并区分零分、未执行和失败状态。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Vue 3、Node.js test runner

---

### Task 1: 后端解耦字段 bbox 与值 bbox

**Files:**
- Modify: `backend/src/test/java/com/aiform/id995a/ocr/StructuredFieldEvidenceServiceTest.java`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldEvidenceService.java`

- [ ] **Step 1: 修改现有测试，定义双 bbox 行为**

将 `buildsCombinedEvidenceAndScoresIndependentJudgeObservation` 改为只提供 `HKID` OCR detection，并断言：

```java
assertThat(detail.recognitionBbox()).containsExactly(82, 12, 180, 28);
assertThat(detail.labelBbox()).containsExactly(8, 12, 70, 28);
assertThat(detail.valueBbox()).containsExactly(82, 12, 180, 28);
assertThat(detail.bbox()).isEqualTo(detail.labelBbox());
assertThat(detail.verificationScore()).isEqualTo(100);
```

- [ ] **Step 2: 增加裁判截图来源测试**

在 fake judge 中记录 `snapshotDataUrl`。页面为 `200x200`、LLM 值 bbox 为 `[82,12,180,28]` 时，解码截图并断言宽度小于字段名和值并集宽度，同时 OCR 未识别值仍得到 100 分：

```java
assertThat(judgeSnapshot.get()).startsWith("data:image/jpeg;base64,");
assertThat(decodedJudgeImage.getWidth()).isLessThan(150);
assertThat(detail.verificationStatus()).isEqualTo("pass");
```

- [ ] **Step 3: 运行测试并确认按预期失败**

```powershell
cd backend
mvn '-Dtest=StructuredFieldEvidenceServiceTest#buildsCombinedEvidenceAndScoresIndependentJudgeObservation' test
```

Expected: FAIL，因为当前值 bbox 来自 OCR 二次定位，公共 bbox 使用合并证据，且缺少 OCR 值 detection 时裁判不会运行。

- [ ] **Step 4: 实现最小后端改动**

在 `prepareField` 中采用以下职责：

```java
List<Integer> valueBbox = parseBbox(evidence, page.imageWidth(), page.imageHeight());
List<Integer> labelBbox = fieldLabelLocator.locate(label, labelDetections);
List<Integer> displayBbox = labelBbox;
CropResult crop = crop(page, valueBbox);
FieldJudgeObservation judgeObservation = judge(candidate, label, valueText, crop, valueBbox);
```

将 `judge` 的前置条件改为有效 LLM 值 bbox 与非空截图：

```java
if (valueBbox.isEmpty()) {
  return FieldJudgeObservation.unavailable("value_bbox_missing");
}
if (crop.dataUrl().isBlank()) {
  return FieldJudgeObservation.unavailable("value_crop_failed");
}
```

`recognitionBbox` 与 `valueBbox` 都保存 LLM 原始坐标；`evidenceBbox` 不再控制高亮或裁判调用。截图继续复用 `FieldCropper.CropKind.SNAPSHOT` 的安全边距，并且不写回扩展坐标。

- [ ] **Step 5: 运行后端聚焦测试**

```powershell
cd backend
mvn '-Dtest=StructuredFieldEvidenceServiceTest,FieldVerificationScorerTest,QwenFieldJudgeClientTest,FdhReviewAssemblerTest' test
```

Expected: PASS，且零失败、零错误。

### Task 2: 前端只高亮字段 bbox 并正确展示裁判状态

**Files:**
- Modify: `frontend/src/ocrPresentation.test.js`
- Modify: `frontend/src/ocrPresentation.js`
- Modify: `frontend/src/layoutStyles.test.js`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 增加高亮 bbox 选择失败测试**

```javascript
test('sourceEvidenceBbox uses label bbox instead of value or combined evidence', () => {
  assert.deepEqual(sourceEvidenceBbox({
    labelBbox: [10, 20, 110, 40],
    valueBbox: [200, 20, 300, 40],
    evidenceBbox: [0, 10, 320, 50],
    bbox: [10, 20, 110, 40]
  }), [10, 20, 110, 40])
})
```

- [ ] **Step 2: 增加零分不是未完成的渲染约束**

```javascript
assert.doesNotMatch(app, /sourceConfidence\(item\) \?/)
assert.doesNotMatch(app, /sourceConfidence\(source\) \?/)
assert.match(app, /sourceScoreText\(item\)/)
```

- [ ] **Step 3: 运行前端测试并确认按预期失败**

```powershell
cd frontend
node --test src\ocrPresentation.test.js src\layoutStyles.test.js
```

Expected: FAIL，因为当前优先读取 `evidenceBbox`，且 `0` 分被 truthy 判断显示为“裁判未完成”。

- [ ] **Step 4: 实现最小前端改动**

将 bbox 选择改为字段标签优先，兼容公共 `bbox`，不读取 `valueBbox/evidenceBbox`：

```javascript
export function sourceEvidenceBbox(source) {
  for (const candidate of [source?.labelBbox, source?.bbox]) {
    if (validBbox(candidate)) return candidate.map(Number)
  }
  return []
}
```

在 `App.vue` 增加：

```javascript
function sourceScoreText(source) {
  const score = fieldVerificationScore(source)
  return score === null ? t('judgePending') : `${score}%`
}
```

文档字段和字段来源都调用该 helper，使 `0` 显示为 `0%`；已有 `verificationReason` 继续展示具体失败原因。

- [ ] **Step 5: 运行前端测试与构建**

```powershell
cd frontend
node --test src\*.test.js
node .\node_modules\vite\bin\vite.js build
```

Expected: 所有测试 PASS，Vite build 成功。

### Task 3: 配置与端到端验证

**Files:**
- Modify only if missing locally: `llm.local.cmd`（Git 忽略，本地凭据文件）
- No repository source changes expected

- [ ] **Step 1: 校验本地裁判配置而不输出密钥**

确认 `FIELD_JUDGE_ENABLED=true`、`FIELD_JUDGE_MODE=enforce`、base URL、model、API key 均非空。不得把真实 key 输出到终端日志、测试或提交内容。

- [ ] **Step 2: 运行完整后端测试**

```powershell
cd backend
mvn test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 重启本地服务**

```powershell
.\stop-local.ps1
.\start-local.ps1 -SkipBuild -WithSidecar
```

Expected: 前端 `5197`、后端 `18083`、OCR sidecar `18092` 启动成功。

- [ ] **Step 4: 使用现有 IANG 测试材料验证**

提交现有 `S-IANG-01-ID990A.pdf`，确认至少一个有 LLM `value_bbox` 的字段：

- 页面高亮只覆盖字段名；
- 返回的 `valueBbox` 保持 LLM 原始坐标；
- 裁判返回实际评分或明确失败原因；
- 页面不再把 `0%` 显示为“裁判未完成”。

- [ ] **Step 5: 检查最终差异**

```powershell
git diff --check
git status --short
```

Expected: 无空白错误；只报告本任务改动和进入任务前已存在的用户改动。由于工作区已有重叠的未提交修改，不自动创建包含生产代码的提交，也不推送远端，除非用户明确要求。
