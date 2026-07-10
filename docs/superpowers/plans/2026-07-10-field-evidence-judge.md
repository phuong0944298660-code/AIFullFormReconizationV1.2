# 字段证据裁判实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为学生 IANG 与 FDH 公共识别链路加入 OCR 字段联合证据框、`qwen3.6-flash` 独立裁判和后端确定性评分，并用核验分数替代主 LLM 置信度决定低分复核。

**Architecture:** 主 LLM 继续输出结构化字段和值；本地 OCR 每页执行一次检测，后端通过字段标签主锚点和值辅助线索解析 `labelBbox`、`valueBbox` 和 `evidenceBbox`。裁判模型只返回截图观察事实，后端校验后生成 `verificationScore`；证据、裁判或响应异常一律安全降级为 `review`。

**Tech Stack:** Java 17、Spring Boot、Java HttpClient、Jackson、Vue 3、Node Test、Python 3、FastAPI、PaddleOCR PP-OCRv6 Tiny。

---

## 文件结构

新增后端文件：

- `backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceRegion.java`：字段标签、填写值与联合证据框的不可变结果。
- `backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceLocator.java`：字段名称匹配、值候选排序、歧义拒绝和联合框计算。
- `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeProperties.java`：裁判端点、模型、超时、阈值和运行模式。
- `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeGateway.java`：裁判调用边界，便于测试替换。
- `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeObservation.java`：裁判事实及稳定状态。
- `backend/src/main/java/com/aiform/id995a/ocr/QwenFieldJudgeClient.java`：OpenAI 兼容多模态 HTTP 调用和响应校验。
- `backend/src/main/java/com/aiform/id995a/ocr/FieldVerificationScorer.java`：确定性核验分数与安全失败规则。

修改后端文件：

- `backend/src/main/java/com/aiform/id995a/ocr/FieldLabelDetection.java`：区分检测与文字识别信息，同时保留旧构造方式。
- `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldDetail.java`：携带定位、裁判和核验字段。
- `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldEvidenceService.java`：生成联合证据框、裁剪证据图、调用裁判和评分。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewResult.java`：向字段来源和文档字段暴露核验元数据。
- `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewAssembler.java`：FDH 使用核验分数和裁判状态。
- `backend/src/main/java/com/aiform/id995a/fdh/StudentIangReviewAssembler.java`：IANG 使用同一核验规则。
- `backend/src/main/resources/application.yml`：增加 `field-judge` 配置，不存储密钥。
- `ocr-service/app/ocr_pipeline.py`：保留文字较弱或为空的检测框。
- `frontend/src/App.vue`：显示裁判评分、观察值、原因和联合框高亮。
- `frontend/src/ocrPresentation.js`：前端字段模型兼容新核验属性。

新增测试文件：

- `backend/src/test/java/com/aiform/id995a/ocr/FieldEvidenceLocatorTest.java`
- `backend/src/test/java/com/aiform/id995a/ocr/FieldVerificationScorerTest.java`
- `backend/src/test/java/com/aiform/id995a/ocr/QwenFieldJudgeClientTest.java`
- `frontend/src/fieldVerification.test.js`

修改现有测试：

- `backend/src/test/java/com/aiform/id995a/ocr/StructuredFieldEvidenceServiceTest.java`
- `backend/src/test/java/com/aiform/id995a/fdh/FdhReviewAssemblerTest.java`
- `backend/src/test/java/com/aiform/id995a/fdh/StudentIangReviewAssemblerTest.java`
- `ocr-service/tests/test_ocr_pipeline.py`
- `frontend/src/ocrPresentation.test.js`

### 任务 1：字段联合证据框

**Files:**
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceRegion.java`
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceLocator.java`
- Create: `backend/src/test/java/com/aiform/id995a/ocr/FieldEvidenceLocatorTest.java`

- [ ] **步骤 1：写失败测试**

覆盖同一行字段、下方字段、手写弱文字框、重复标签歧义和联合框并集：

```java
@Test
void locatesLabelAndValueOnSameRow() {
  List<FieldLabelDetection> detections = List.of(
      new FieldLabelDetection("HK Identity Card No.", 98, List.of(100, 200, 360, 245)),
      new FieldLabelDetection("A123456(7)", 96, List.of(380, 200, 620, 245))
  );
  FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
      "HK Identity Card No.", "A123456(7)", detections, 1200, 1600
  );
  assertEquals("located", result.status());
  assertEquals(List.of(100, 200, 360, 245), result.labelBbox());
  assertEquals(List.of(380, 200, 620, 245), result.valueBbox());
  assertTrue(result.evidenceBbox().get(0) <= 100);
  assertTrue(result.evidenceBbox().get(2) >= 620);
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```powershell
cd backend
mvn '-Dtest=FieldEvidenceLocatorTest' test
```

预期：编译失败，提示 `FieldEvidenceRegion` 或 `FieldEvidenceLocator` 不存在。

- [ ] **步骤 3：实现最小定位器**

`FieldEvidenceRegion` 固定字段：

```java
public record FieldEvidenceRegion(
    List<Integer> labelBbox,
    List<Integer> valueBbox,
    List<Integer> evidenceBbox,
    String status,
    String method,
    double locationScore,
    String reason
) {}
```

`FieldEvidenceLocator.locate` 使用现有 `FieldLabelLocator` 找标签，随后按“归一化值匹配优先、同行右侧次之、标签下方再次之”的顺序选择值框；等分候选返回 `ambiguous`，找不到值返回 `partial`。联合框按标签和值并集后增加不超过页面尺寸 1.5% 的边距并执行边界裁剪。

- [ ] **步骤 4：运行定位测试**

运行：`mvn '-Dtest=FieldEvidenceLocatorTest' test`

预期：全部通过。

- [ ] **步骤 5：提交里程碑**

```powershell
git add backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceRegion.java backend/src/main/java/com/aiform/id995a/ocr/FieldEvidenceLocator.java backend/src/test/java/com/aiform/id995a/ocr/FieldEvidenceLocatorTest.java
git commit -m "feat(ocr): locate combined field evidence regions"
```

### 任务 2：OCR 保留弱文字检测框

**Files:**
- Modify: `ocr-service/app/ocr_pipeline.py`
- Modify: `ocr-service/tests/test_ocr_pipeline.py`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/FieldLabelDetection.java`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/LocalFieldRegionOcrClient.java`

- [ ] **步骤 1：增加失败测试**

Python 测试构造 `rec_texts=["Field", ""]` 和两个 `dt_polys`，断言第二个空文字检测仍返回 bbox，并标记 `text_status="unreadable"`。

```python
def test_page_detection_keeps_polygon_when_text_is_empty():
    payload = [{"rec_texts": ["Name", ""], "rec_scores": [0.99, 0.0], "dt_polys": [
        [[10, 10], [80, 10], [80, 30], [10, 30]],
        [[100, 10], [220, 10], [220, 30], [100, 30]],
    ]}]
    lines = extract_lines_from_ocr_response(payload)
    assert len(lines) == 2
    assert lines[1]["text"] == ""
    assert lines[1]["text_status"] == "unreadable"
```

- [ ] **步骤 2：运行并确认失败**

运行：`ocr-service\.venv\Scripts\python.exe -m pytest ocr-service\tests\test_ocr_pipeline.py -q`

预期：测试失败，当前实现会丢弃空文字检测。

- [ ] **步骤 3：实现响应兼容**

`extract_lines_from_ocr_response` 以 polygon 数量为主循环，不再因文字为空丢弃检测；输出 `text_status`。Java 客户端解析该属性。`FieldLabelDetection` 增加兼容构造器，旧测试仍可使用三个参数。

- [ ] **步骤 4：运行 Python 与 Java OCR 测试**

运行：

```powershell
ocr-service\.venv\Scripts\python.exe -m pytest ocr-service\tests\test_ocr_pipeline.py -q
cd backend
mvn '-Dtest=LocalFieldRegionOcrClientTest,FieldEvidenceLocatorTest' test
```

预期：全部通过。

### 任务 3：确定性评分器

**Files:**
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeObservation.java`
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldVerificationScorer.java`
- Create: `backend/src/test/java/com/aiform/id995a/ocr/FieldVerificationScorerTest.java`

- [ ] **步骤 1：写评分表失败测试**

```java
@ParameterizedTest
@CsvSource({
    "exact,clear,complete,100,pass",
    "normalized_equal,clear,complete,90,pass",
    "semantic_equal,clear,complete,80,review",
    "crop_incomplete,clear,incomplete,40,review",
    "unreadable,unreadable,complete,30,review",
    "mismatch,clear,complete,0,review"
})
void scoresValidatedJudgeFacts(String match, String legibility, String coverage, int score, String status) {
  FieldVerification result = new FieldVerificationScorer(85).score(
      "A123456(7)", new FieldJudgeObservation("available", "A123456(7)", match, legibility, coverage, "reason")
  );
  assertEquals(score, result.score());
  assertEquals(status, result.status());
}
```

- [ ] **步骤 2：实现规范化和矛盾保护**

评分器只接受白名单枚举；`exact` 与 `normalized_equal` 必须由后端再次比较值。空响应、未知枚举、矛盾值和 `unavailable` 返回无分数的 `review`，原因码分别稳定为 `judge_invalid`、`judge_contradiction` 和 `judge_unavailable`。

- [ ] **步骤 3：运行测试**

运行：`mvn '-Dtest=FieldVerificationScorerTest' test`

预期：阈值 `84/85`、所有枚举及异常分支通过。

### 任务 4：Qwen 裁判客户端

**Files:**
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeProperties.java`
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldJudgeGateway.java`
- Create: `backend/src/main/java/com/aiform/id995a/ocr/QwenFieldJudgeClient.java`
- Create: `backend/src/test/java/com/aiform/id995a/ocr/QwenFieldJudgeClientTest.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **步骤 1：写 HTTP 请求失败测试**

用测试 `HttpClient` 捕获请求体，断言：

```java
assertEquals("qwen3.6-flash", body.path("model").asText());
assertFalse(body.path("enable_thinking").asBoolean(true));
assertEquals(0, body.path("temperature").asInt());
assertTrue(body.toString().contains("data:image/jpeg;base64,"));
assertFalse(body.toString().contains("recognitionConfidence"));
```

- [ ] **步骤 2：实现客户端**

请求 `/chat/completions`，图片使用 data URL，系统提示只允许返回设计规格中的五个事实属性。客户端校验 HTTP 状态、`choices[0].message.content` 和 JSON 枚举；日志仅记录状态、耗时和 usage，不记录请求体与字段值。

- [ ] **步骤 3：加入配置**

```yaml
field-judge:
  enabled: ${FIELD_JUDGE_ENABLED:false}
  mode: ${FIELD_JUDGE_MODE:shadow}
  base-url: ${FIELD_JUDGE_BASE_URL:}
  api-key: ${FIELD_JUDGE_API_KEY:}
  model: ${FIELD_JUDGE_MODEL:qwen3.6-flash}
  timeout-seconds: ${FIELD_JUDGE_TIMEOUT_SECONDS:20}
  pass-threshold: ${FIELD_JUDGE_PASS_THRESHOLD:85}
  concurrency: ${FIELD_JUDGE_CONCURRENCY:4}
```

- [ ] **步骤 4：运行测试**

运行：`mvn '-Dtest=QwenFieldJudgeClientTest,FieldVerificationScorerTest' test`

预期：请求结构、有效响应、超时、`429`、`5xx` 与非法响应测试通过。

### 任务 5：证据服务接入裁判

**Files:**
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldDetail.java`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldEvidenceService.java`
- Modify: `backend/src/test/java/com/aiform/id995a/ocr/StructuredFieldEvidenceServiceTest.java`

- [ ] **步骤 1：写失败测试**

构造标签与值 OCR 检测以及固定裁判响应，断言：

```java
assertEquals(List.of(90, 190, 635, 255), detail.evidenceBbox());
assertEquals(detail.evidenceBbox(), detail.bbox());
assertEquals("A123456(7)", detail.judgeObservedValue());
assertEquals(100, detail.verificationScore());
assertEquals("pass", detail.verificationStatus());
```

另写 OCR 不可用、partial、裁判超时和 invalid JSON 测试，全部断言 `verificationStatus="review"`。

- [ ] **步骤 2：扩展字段明细**

为 `StructuredFieldDetail` 增加完整定位与裁判属性，并提供旧签名兼容构造器，避免一次性改写所有现有测试 fixture。

- [ ] **步骤 3：修改处理顺序**

`StructuredFieldEvidenceService` 对每页 OCR 一次；每个字段调用 `FieldEvidenceLocator`；只对 `located` 且截图非空的字段调用裁判。最终 crop 从 `evidenceBbox` 生成，原 LLM bbox 只保留为 `recognitionBbox`。

- [ ] **步骤 4：运行证据服务测试**

运行：`mvn '-Dtest=StructuredFieldEvidenceServiceTest' test`

预期：旧字段展示兼容测试与新裁判测试全部通过。

### 任务 6：学生与佣工装配及决策

**Files:**
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewResult.java`
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewAssembler.java`
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/StudentIangReviewAssembler.java`
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewConclusionService.java`
- Modify: `backend/src/test/java/com/aiform/id995a/fdh/FdhReviewAssemblerTest.java`
- Modify: `backend/src/test/java/com/aiform/id995a/fdh/StudentIangReviewAssemblerTest.java`

- [ ] **步骤 1：写两套流程失败测试**

分别证明：主 LLM 置信度 99 但裁判 mismatch 时为 review；主 LLM 置信度 40 但裁判 exact 100 且无其他阻断时可 pass；裁判 unavailable 时为 review。

- [ ] **步骤 2：传递核验元数据**

`FieldSource` 与 `DocumentField` 增加 `verificationScore`、`verificationStatus`、`judgeObservedValue`、`verificationReason`、三个 bbox 和定位信息，并提供兼容构造器。

- [ ] **步骤 3：替换字段低置信判断**

仅替换字段来源的 LLM 置信度判断；材料模板置信度和页码识别置信度继续保留。建议值选择顺序改为材料优先级、来源一致性、核验分数。

- [ ] **步骤 4：压缩结论模型输入**

结论服务发送 `verificationScore` 和核验原因，不再把字段 `confidence` 当作可信度依据；继续移除所有截图 Base64。

- [ ] **步骤 5：运行重点后端测试**

运行：

```powershell
mvn '-Dtest=StructuredFieldEvidenceServiceTest,FdhReviewAssemblerTest,StudentIangReviewAssemblerTest,FdhReviewConclusionServiceTest,FdhReviewJobServiceTest' test
```

预期：全部通过。

### 任务 7：前端裁判展示

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/ocrPresentation.js`
- Modify: `frontend/src/ocrPresentation.test.js`
- Create: `frontend/src/fieldVerification.test.js`

- [ ] **步骤 1：写展示失败测试**

测试字段展示优先读取 `verificationScore`，无分数显示“未完成裁判”，且联合 bbox 作为高亮框。

```javascript
assert.equal(fieldVerificationScore({ verificationScore: 90, confidence: 99 }), 90)
assert.equal(fieldVerificationScore({ verificationScore: null, confidence: 99 }), null)
assert.deepEqual(sourceEvidenceBbox({ evidenceBbox: [1, 2, 3, 4], bbox: [9, 9, 9, 9] }), [1, 2, 3, 4])
```

- [ ] **步骤 2：修改界面文本与详情**

把“综合置信度/值置信度”改为“裁判评分/核验分数”；选中来源时展示主 LLM 值、裁判观察值和核验原因；定位质量继续独立显示。

- [ ] **步骤 3：修改 review 筛选**

裁判未完成、分数低于 85 或核验状态为 review 的字段必须进入现有 review 过滤器，不新增第五种业务状态。

- [ ] **步骤 4：运行前端测试与构建**

运行：

```powershell
cd frontend
node --test src\*.test.js
node .\node_modules\vite\bin\vite.js build
```

预期：测试和构建通过。

### 任务 8：端到端失败策略与安全验证

**Files:**
- Modify: `backend/src/test/java/com/aiform/id995a/fdh/FdhReviewJobServiceTest.java`
- Modify: `backend/src/test/java/com/aiform/id995a/controller/FdhReviewControllerTest.java`
- Modify: `AGENTS.md` only if startup/configuration behavior changes materially

- [ ] **步骤 1：增加任务级集成测试**

覆盖 sidecar 关闭、裁判关闭、裁判超时与一份材料部分字段成功/部分字段失败，确认任务完成而不是整体失败，失败字段为 review。

- [ ] **步骤 2：验证日志和 JSON 安全**

测试日志记录器与前端 JSON payload 不包含 `Authorization`、API Key 或完整 `data:image/...;base64` 内容。

- [ ] **步骤 3：运行全量验证**

```powershell
cd backend
mvn test
cd ..\frontend
node --test src\*.test.js
node .\node_modules\vite\bin\vite.js build
```

预期：全部通过。

- [ ] **步骤 4：人工真实截图验证**

用至少一张印刷值和一张手写值截图验证：联合框同时包含字段名和值、裁判请求关闭思考、返回值可解析、前端高亮与截图一致。不得把测试图片或响应日志提交到 Git。

- [ ] **步骤 5：最终提交前检查**

```powershell
git diff --check
git status --short
```

只暂存本需求修改，不加入日志、密钥、模型文件、输出图片或用户已有无关改动。

