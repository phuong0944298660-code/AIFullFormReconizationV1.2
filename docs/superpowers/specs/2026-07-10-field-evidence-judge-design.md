# Field Evidence Judge Design

## Goal

Replace the extraction LLM's self-reported field confidence with an evidence-based verification score for both Student IANG and FDH review flows.

The main LLM continues to extract structured field names and values from each document. Local PP-OCRv6 Tiny uses those field names and values as search hints to locate the field label and its filled value on the rendered page. A combined evidence region is used for both frontend highlighting and the image sent to `qwen3.6-flash`. The judge independently reads the image and reports comparison facts. The backend converts those facts into a deterministic score and sends low-scoring or unverifiable fields to manual review.

## Success Criteria

- Student IANG and FDH use the same evidence-location, judge, scoring, and failure-policy services.
- Frontend field highlighting and judge input use the same combined field-and-value evidence box.
- The extraction LLM's confidence is not used to auto-pass, fail, rank, or display a field when judge scoring is enabled.
- A field with a verification score below `85` is `review`.
- Missing or partial evidence boxes, incomplete crops, unreadable values, judge timeouts, invalid judge responses, and disabled/unavailable dependencies are `review` and never fall back to extraction confidence.
- The result remains explainable: users can see the evidence crop, extraction value, judge-observed value, score, and reason.
- No image base64, API key, or unredacted judge request is written to normal application logs.

## Non-Goals

- The judge does not replace document classification, page recognition, material completeness rules, cross-document rules, or the existing conclusion-generation model.
- The judge does not correct source documents or silently overwrite an extracted value.
- The first version does not train or fine-tune OCR or the judge model.
- The first version does not create a second independent whole-document extraction pipeline.
- The first version does not use the judge model's arbitrary self-reported `0-100` confidence as the displayed score.

## Core Responsibilities

### Main extraction LLM

The existing extraction LLM remains responsible for document-level understanding and structured JSON. For every populated field it supplies at least:

```json
{
  "fieldKey": "applicant.hkid",
  "fieldLabel": "HK Identity Card No.",
  "value": "A123456(7)",
  "page": 1
}
```

Its confidence may be retained as `recognitionConfidence` in internal/debug data during migration, but it does not drive the verification UI or review decision after enforcement is enabled.

### Local OCR and evidence locator

PP-OCRv6 Tiny detects page text and polygons once per rendered page. The backend evidence locator uses the extraction field label as the primary anchor and the extracted value only as a secondary search hint. It must not treat the extracted value as truth.

The locator produces:

```json
{
  "labelBbox": [100, 200, 360, 245],
  "valueBbox": [380, 200, 620, 245],
  "evidenceBbox": [90, 190, 635, 255],
  "locationStatus": "located",
  "locationMethod": "label_and_value",
  "locationScore": 92
}
```

- `labelBbox` is the OCR-located field label.
- `valueBbox` is the OCR-detected filled-value region. Its recognized text may be wrong or empty, especially for handwriting; the box can still be useful evidence.
- `evidenceBbox` is the union of label and value boxes plus bounded padding.
- `locationScore` describes localization quality only. It is never the field verification score.

The existing `bbox` response property remains as a compatibility alias for `evidenceBbox` during migration. Both frontend highlighting and the judge crop use `evidenceBbox`, eliminating the current split where highlighting uses a label box while snapshots can still come from an LLM-provided value box.

### Visual judge

`qwen3.6-flash` receives one field evidence crop per request, together with the field key, field label, extraction value, value type, and narrowly scoped comparison instructions. It does not receive the extraction LLM's confidence.

The request uses:

- `enable_thinking: false`
- `temperature: 0`
- structured JSON output
- a bounded output-token limit

The judge returns facts rather than a final system score:

```json
{
  "observedValue": "A123456(7)",
  "matchType": "exact",
  "legibility": "clear",
  "cropCoverage": "complete",
  "reason": "The identity-card number in the crop matches the extracted value."
}
```

Allowed `matchType` values are:

- `exact`
- `normalized_equal`
- `semantic_equal`
- `mismatch`
- `unreadable`
- `crop_incomplete`

The backend validates the response schema and independently checks deterministic normalization of `observedValue` against the extraction value. A model claim of `exact` or `normalized_equal` is not trusted when the returned values contradict it.

### Deterministic scorer

The backend computes `verificationScore` from validated judge facts:

| Validated result | Score | Status |
| --- | ---: | --- |
| Exact match, clear, complete crop | 100 | pass |
| Deterministic normalized match, clear, complete crop | 90 | pass |
| Semantic equivalence requiring interpretation | 80 | review |
| Crop incomplete | 40 | review |
| Value unreadable | 30 | review |
| Clear mismatch | 0 | review or existing rule-driven fail |
| Dependency/error/invalid response | no score | review |

The initial pass threshold is `85`. This deliberately keeps semantic adjudication in review unless a separate, explicit workflow rule authorizes automatic equivalence. Existing material and cross-document rules remain able to make a result stricter; a high verification score cannot override a missing material, required blank, payment-not-complete state, or cross-document conflict.

## Evidence Location Strategy

One geometry rule is not sufficient for all immigration forms. The locator chooses a strategy from field metadata and page structure:

- `inline`: value appears on the same row, usually to the right of the label.
- `below`: value appears directly below the label.
- `multiline`: addresses and other multi-line values.
- `selection`: checkbox and radio-button groups.
- `table_cell`: value appears in a neighboring or containing table cell.
- `signature`: handwritten signature or signature area.

The search order is:

1. Fuzzy-match the field label against OCR lines using normalized text.
2. Search nearby OCR detections using expected layout and field type.
3. Use normalized extraction-value text only to rank plausible nearby detections.
4. Reject ambiguous matches rather than selecting an arbitrary equal-scoring region.
5. Form `evidenceBbox` only when both the label and a plausible value region are available.

The OCR sidecar must retain detection polygons even when text recognition is weak, so handwritten or poorly recognized values can still become `valueBbox` candidates. Detection confidence and text-recognition confidence must be separate properties.

If only a label is located, the result is `partial`. The system may retain a diagnostic wide crop, but the field remains `review` and cannot be auto-passed.

## Shared Processing Flow

The judge is inserted in the common recognition/evidence path before Student IANG or FDH review assembly:

1. Render uploaded files to pages.
2. Classify document and page using existing deterministic rules.
3. Run the main LLM structured extraction.
4. Run full-page local OCR once per page.
5. Build label, value, and combined evidence boxes for each populated field.
6. Crop `evidenceBbox` from the original rendered page.
7. Call the visual judge for each successfully located field.
8. Validate judge facts and calculate `verificationScore`.
9. Pass enriched field details to the existing Student IANG or FDH assembler.
10. Apply material, required-field, cross-document, payment, and conclusion rules.

The judge must not be added to `FdhReviewConclusionService`. That service drafts the final narrative and field adjudications after review assembly; mixing evidence verification into it would make failures difficult to isolate and would duplicate Student/FDH behavior.

## Backend Component Changes

### OCR evidence data

Replace the label-only result with an evidence-region value object containing:

- detected label text and `labelBbox`
- detected value text and `valueBbox`
- `evidenceBbox`
- detection and text confidence
- location status, method, and ambiguity reason

Extend `FieldRegionOcrGateway` and `/ocr/page-detect` without creating a second sidecar. The page is OCRed once and detections are reused for every field on that page.

### Region resolution

Evolve `FieldLabelLocator` into a focused evidence-region resolver. Keep label matching isolated from layout-specific value-region selection so each part can be tested independently.

`StructuredFieldEvidenceService` should:

- stop using the LLM value bbox for the final crop when OCR evidence location is enabled;
- retain the original LLM bbox only as debug metadata;
- create the crop from `evidenceBbox`;
- attach localization metadata to `StructuredFieldDetail`.

### Judge client and orchestration

Add a dedicated judge configuration and HTTP client rather than reusing the conclusion model configuration:

- endpoint/base URL
- API key from a local secret or environment variable
- model, defaulting to `qwen3.6-flash`
- connect/request timeout
- maximum judge concurrency
- enforcement mode

The API key provided during exploration must not be committed. It should be rotated because it was shared in plaintext, then stored in a local ignored credential file or environment variable.

The judge runs per field with a small bounded concurrency pool. Initial defaults are four concurrent judge calls per review job, a 20-second request timeout, and one retry only for transport errors, `429`, and retryable `5xx` responses. Invalid JSON and semantic schema failures are not retried automatically.

### Response model

Enrich field-source/detail responses with:

- `recognitionConfidence` for debug compatibility
- `labelBbox`
- `valueBbox`
- `bbox` / `evidenceBbox`
- `locationStatus`, `locationMethod`, `locationScore`
- `judgeStatus`, `judgeObservedValue`, `judgeMatchType`
- `verificationScore`, `verificationReason`, `scoreSource`

Assemblers use `verificationScore` and judge status for low-score review logic. Existing confidence comparisons used to choose a suggested value must be replaced by deterministic document priority, source agreement, and verification score in that order.

## Failure Policy

The system is fail-closed for field verification:

- OCR sidecar disabled or unavailable: `review`.
- Label not found, value region not found, or ambiguous combined box: `review`.
- Empty or incomplete crop: `review`.
- Judge disabled, timeout, transport failure, rate limit after retry, or server failure: `review`.
- Judge returns invalid JSON, unknown enum, contradictory values, or missing required properties: `review`.
- Judge says unreadable or crop incomplete: `review`.

Failures are represented with stable machine-readable reason codes and localized user text. Raw exceptions, endpoints, credentials, and image data are not shown to users.

## Frontend Experience

The recognition result page keeps its current split layout and review filters.

Changes are surgical:

- Rename the primary field metric from “confidence” to “verification score” / “裁判评分”.
- Highlight `evidenceBbox`, which contains the field label and filled value.
- Show extraction value and judge-observed value together when the field is selected.
- Show a concise explanation such as “format-normalized match”, “value mismatch”, “crop incomplete”, or “judge unavailable”.
- Keep localization quality secondary and label it “定位质量”; it must not look like another correctness score.
- Fields without a completed judge result show “未完成裁判” and remain in the review filter.
- JSON view includes judge and localization metadata but compacts/removes image base64 as it does today.

The verification/Minutes page keeps the existing allowed statuses: pass, unrecognized, required missing, and review. It does not add a new end-user status solely for judge failures.

## Privacy and Logging

Evidence crops contain identity and immigration information. The judge request therefore sends only the smallest combined field-and-value crop, never a full document page unless explicitly required by a future design.

- Do not log authorization headers, request bodies, base64 images, extracted values, or judge-observed values in normal logs.
- Log request ID, field key hash or safe key, model, latency, HTTP category, judge status, token usage, and reason code.
- Do not expose the judge API key to the frontend.
- Keep existing JSON-preview compaction for data URLs.

## Rollout

Implement both modes behind a single judge feature setting:

- `shadow`: calculate and expose judge results for calibration, but preserve the current decision logic.
- `enforce`: replace extraction confidence with verification score and apply fail-closed review behavior.

Development and automated tests target `enforce`. A real-data rollout starts in `shadow`, validates a labeled Student/FDH sample, then switches to `enforce`. This is a deployment control, not a second implementation.

## Testing

### OCR and localization tests

- Exact, partial, and bilingual label matching.
- Same-row, below-label, multiline, checkbox, table-cell, and signature layouts.
- Printed, handwritten, low-contrast, and weak-recognition value regions.
- Duplicate labels and ambiguous matches.
- Combined-box union, padding, clamping, and page-boundary behavior.
- OCR sidecar page-detection response containing polygons with weak or empty text.

### Judge client and scorer tests

- OpenAI-compatible multimodal request shape with top-level `enable_thinking: false`.
- Valid responses for every allowed match type.
- Contradictory judge output is downgraded to review.
- Exact and normalized scoring boundaries.
- Threshold behavior at `84` and `85`.
- Timeout, `429`, retryable `5xx`, invalid JSON, missing fields, and unknown enums all fail closed.
- Logs and exported JSON do not contain API keys or full base64 images.

### Flow tests

- Student IANG and FDH both receive judge-enriched field sources.
- A high extraction confidence with a judge mismatch becomes review.
- A low extraction confidence with a clear exact judge match can pass when no other rule blocks it.
- Payment-not-complete, missing material, required blank, and cross-document conflict remain review/fail regardless of judge score.
- Frontend review filtering, score labels, selected evidence highlight, and judge explanation render correctly.

### Calibration set

Before enabling enforcement on real cases, evaluate at least 200 labeled field crops split across Student IANG and FDH, including at least 30 handwritten values and 30 deliberate extraction mismatches. No known mismatch in this set may auto-pass at the selected threshold. Record coverage, review rate, false-pass rate, false-review rate, judge latency, and token cost before switching from shadow to enforce.

