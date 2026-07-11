# Local OCR Field Label Bbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use the existing local PP-OCRv5 detection and recognition models to locate printed field labels on each uploaded page and use only those label boxes for source highlighting.

**Architecture:** The OCR sidecar adds a full-page label-detection endpoint returning text polygons and confidence. The backend matches each structured field label against detected printed text, replaces display bbox only when the label match is reliable, and otherwise returns no highlight. LLM-recognized values and review decisions remain unchanged.

**Tech Stack:** Python 3.12, FastAPI, PaddleOCR 3.5, PaddlePaddle 3.0, Java 17, Spring Boot, JUnit 5.

---

### Task 1: Sidecar full-page detection

**Files:**
- Modify: `ocr-service/app/ocr_pipeline.py`
- Modify: `ocr-service/app/main.py`
- Modify: `ocr-service/run-dev.cmd`
- Test: `ocr-service/tests/test_ocr_pipeline.py`

- [ ] Add a failing test asserting that injected detection output becomes `{text, confidence, bbox}` lines.
- [ ] Run `python -m unittest ocr-service/tests/test_ocr_pipeline.py` and verify the new test fails because page detection is absent.
- [ ] Add `LocalTextDetectionClient` using `models/PP-OCRv5_server_det_infer`, expose `/ocr/page-detect`, and keep crop recognition unchanged.
- [ ] Run the sidecar tests and verify they pass.

### Task 2: Backend label locator

**Files:**
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldLabelDetection.java`
- Create: `backend/src/main/java/com/aiform/id995a/ocr/FieldLabelLocator.java`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/LocalFieldRegionOcrClient.java`
- Modify: `backend/src/main/java/com/aiform/id995a/ocr/StructuredFieldEvidenceService.java`
- Test: `backend/src/test/java/com/aiform/id995a/ocr/FieldLabelLocatorTest.java`

- [ ] Add failing tests proving exact and normalized label matches return the detected label bbox while handwriting/value text is ignored.
- [ ] Run `mvn -Dtest=FieldLabelLocatorTest test` and verify failure before implementation.
- [ ] Add the page-detection client and label matching with conservative confidence and token-overlap thresholds.
- [ ] Replace display bbox with the matched printed-label bbox; return an empty bbox when no reliable label match exists.
- [ ] Run focused backend tests and verify they pass.

### Task 3: Integration verification

**Files:**
- Modify only if required by test results: `backend/src/main/java/com/aiform/id995a/ocr/OcrDemoService.java`

- [ ] Start services with `.\start-local.ps1 -WithSidecar`.
- [ ] Verify `http://127.0.0.1:18092/health` returns 200.
- [ ] Submit `S-IANG-01-ID990A.pdf` in the student flow.
- [ ] Confirm `Length of residence...` highlights its printed field label and not the handwritten `22` or the qualification row.
- [ ] Run `mvn -Dtest=FieldLabelLocatorTest,StructuredFieldEvidenceServiceTest,LocalFieldRegionOcrClientTest test` and the sidecar tests.
