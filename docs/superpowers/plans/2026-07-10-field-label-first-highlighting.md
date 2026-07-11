# 字段定位优先高亮 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OCR 定位到 LLM 给出的字段名时即返回可用于前端高亮的 bbox，不再要求同时定位到字段值。

**Architecture:** `FieldEvidenceLocator` 保持字段名和字段值的独立定位结果。字段名存在而值不存在时，返回 `label_only` 区域，前端可高亮标签；裁判仍只接收包含值的 `located` 或 `value_only` 截图。

**Tech Stack:** Java 17、Spring Boot、JUnit 5。

---

### Task 1: 定义标签单独定位的回归测试

**Files:**
- Modify: `backend/src/test/java/com/aiform/id995a/ocr/FieldEvidenceLocatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
assertEquals("label_only", result.status());
assertEquals("label_only", result.method());
assertEquals(List.of(100, 100, 260, 140), result.labelBbox());
assertTrue(result.evidenceBbox().isEmpty());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn '-Dmaven.repo.local=C:\Users\49711\.m2\repository' '-Dtest=FieldEvidenceLocatorTest#returnsLabelOnlyEvidenceWhenValueIsNotDetected' test`

- [ ] **Step 3: Implement the minimal locator behavior**

Return `FieldEvidenceRegion` with `status` and `method` set to `label_only` when a unique label bbox is found but no candidate value is found.

- [ ] **Step 4: Run the focused test suite**

Run: `mvn '-Dmaven.repo.local=C:\Users\49711\.m2\repository' '-Dtest=FieldEvidenceLocatorTest,StructuredFieldEvidenceServiceTest' test`

### Task 2: Keep highlighters independent from judge crop eligibility

**Files:**
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldEvidenceService.java`

- [ ] **Step 1: Preserve label bbox for UI fields**

Set the public field `bbox` to the combined evidence bbox when present, otherwise to the located label bbox.

- [ ] **Step 2: Preserve judge safety**

Keep judge crop eligibility limited to `located` and `value_only`, so a label-only rectangle never produces an ungrounded value score.

- [ ] **Step 3: Package and restart locally**

Run the targeted tests, package the backend, then run `./stop-local.ps1` and `./start-local.ps1 -SkipBuild -WithSidecar`.
