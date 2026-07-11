# Document Field Judge Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display real per-page judge scores in a compact, independently scrollable review panel and keep detailed judge context scoped to the field the user selected.

**Architecture:** The existing back end already emits each document field's score, verification status, observed value, reason, and label/value bounding boxes. Extend that DTO with `judgeMatchType` so the client can render a concise result consistently. The Vue panel keeps its own scrolling body and sticky inspector footer; normalized field cards render the same reason inline instead of sharing the document panel footer.

**Tech Stack:** Java 17/Spring Boot records and assemblers; Vue 3 SFC; CSS Grid; Maven and Node test runners.

---

### Task 1: Preserve the judge match type in the review API

**Files:**
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewResult.java`
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/FdhReviewAssembler.java`
- Modify: `backend/src/main/java/com/aiform/id995a/fdh/StudentIangReviewAssembler.java`
- Test: `backend/src/test/java/com/aiform/id995a/fdh/FdhReviewAssemblerTest.java`

- [ ] **Step 1: Write a failing API-assembly test**

```java
assertThat(documentField.judgeMatchType()).isEqualTo("exact");
```

- [ ] **Step 2: Run the focused test and verify it fails because `judgeMatchType` is absent.**

Run: `mvn '-Dtest=FdhReviewAssemblerTest' test`

- [ ] **Step 3: Add `judgeMatchType` to `DocumentField` and pass `ExtractedValue.judgeMatchType()` in both assemblers.**

```java
String judgeMatchType,
```

- [ ] **Step 4: Re-run the focused test.**

Run: `mvn '-Dtest=FdhReviewAssemblerTest' test`

### Task 2: Render a compact score/status column and panel-scoped inspector

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/layoutStyles.test.js`

- [ ] **Step 1: Write a failing front-end layout test for the compact score column and inspector.**

```js
assert.match(app, /class="document-field-inspector"/)
assert.match(css, /minmax\(104px, 0\.42fr\)/)
```

- [ ] **Step 2: Run the test and verify it fails.**

Run: `node --test src/layoutStyles.test.js`

- [ ] **Step 3: Replace the document panel's generic scroll with a grid of sticky header, scroll body, and inspector footer.**

```css
.document-fields-panel.locator-document-fields {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}
```

- [ ] **Step 4: Render score and short status in the 104px judge column; use the panel footer for `verificationReason`.**

```vue
<span class="document-field-judge compact">
  <strong>{{ documentJudgeScoreText(item) }}</strong>
  <small>{{ documentJudgeSummaryText(item) }}</small>
</span>
```

- [ ] **Step 5: Keep normalized-field reasons inline with their source card rather than using the document-panel inspector.**

- [ ] **Step 6: Re-run the front-end tests and production build.**

Run: `node --test src/layoutStyles.test.js; node .\\node_modules\\vite\\bin\\vite.js build`

### Task 3: Verify the real service flow

**Files:**
- No additional production files.

- [ ] **Step 1: Run the focused backend and front-end suites.**

Run: `mvn '-Dtest=FdhReviewAssemblerTest,FdhReviewJobServiceTest' test` and `node --test src/*.test.js`

- [ ] **Step 2: Package, restart the local services, and submit the ID 990A PDF.**

Run: `./stop-local.ps1; cd backend; mvn -DskipTests package; cd ..; ./start-local.ps1 -SkipBuild`

- [ ] **Step 3: Confirm the job completes and document-field sources include both non-null scores and non-empty judge reasons.**

```powershell
curl.exe --noproxy '*' -sS http://127.0.0.1:18083/api/fdh/review/jobs/<job-id>
```

## Review

- API retains the detailed reason and match type needed by both UI contexts.
- The document panel does not create a browser-fixed footer or hide the normalized field area.
- The compact score column reserves more width for field labels and recognition values.
