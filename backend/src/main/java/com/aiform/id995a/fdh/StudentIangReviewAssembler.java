package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.StructuredFieldDetail;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class StudentIangReviewAssembler {

  private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final Pattern PAGE_KEY_PATTERN = Pattern.compile("^page[_-]?(\\d+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PROGRAMME_IN_CERTIFICATION_TEXT = Pattern.compile(
      "\\bfor\\s+the\\s+(.+?)\\s+of\\s+this\\s+University\\b",
      Pattern.CASE_INSENSITIVE
  );
  private static final int ID990A_REQUIRED_PAGE_COUNT = 5;

  private final Clock clock;

  public StudentIangReviewAssembler() {
    this(Clock.systemDefaultZone());
  }

  StudentIangReviewAssembler(Clock clock) {
    this.clock = clock == null ? Clock.systemDefaultZone() : clock;
  }

  public FdhReviewResult assemble(String applicationTypeId, List<FdhReviewDocument> documents) {
    List<FdhReviewDocument> safeDocuments = documents == null ? List.of() : List.copyOf(documents);
    List<FdhReviewResult.MaterialRow> materialRows = buildMaterialRows(safeDocuments);
    List<FdhReviewResult.StandardField> fields = buildFields(safeDocuments);
    String decision = decision(materialRows, fields);
    return new FdhReviewResult(
        StudentIangMaterialCatalog.APPLICATION_TYPE_ID,
        uploadedFiles(safeDocuments),
        materialRows,
        fields,
        documentFieldGroups(safeDocuments),
        reviewPages(safeDocuments),
        decision,
        decisionText(decision, materialRows, fields),
        stats(fields),
        LocalDateTime.now(clock).format(GENERATED_AT_FORMAT)
    );
  }

  private List<FdhReviewResult.ReviewPage> reviewPages(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.ReviewPage> pages = new ArrayList<>();
    for (FdhReviewDocument document : documents) {
      OcrDemoResponse response = document.ocrResult();
      if (response == null || response.pages() == null) {
        continue;
      }
      for (OcrPage page : response.pages()) {
        pages.add(new FdhReviewResult.ReviewPage(
            document.materialId(),
            materialName(document.materialId()),
            document.filename(),
            page.page(),
            pageTitle(document.materialId(), page.page()),
            page.sourceImageDataUrl(),
            page.imageWidth(),
            page.imageHeight()
        ));
      }
    }
    return pages;
  }

  private List<FdhReviewResult.UploadedFile> uploadedFiles(List<FdhReviewDocument> documents) {
    return documents.stream()
        .map(document -> {
          DocumentTemplate template = document.template();
          return new FdhReviewResult.UploadedFile(
              document.materialId(),
              StudentIangMaterialCatalog.displayName(document.materialId()),
              document.filename(),
              document.pageCount(),
              template == null ? "" : template.footerId(),
              template == null ? "" : template.templateId(),
              template == null ? "" : template.matchSource()
          );
        })
        .toList();
  }

  private List<FdhReviewResult.MaterialRow> buildMaterialRows(List<FdhReviewDocument> documents) {
    Map<String, List<FdhReviewDocument>> byMaterial = documentsByMaterial(documents);
    return StudentIangMaterialCatalog.materials().stream()
        .map(material -> materialRow(material, byMaterial.getOrDefault(material.id(), List.of())))
        .toList();
  }

  private FdhReviewResult.MaterialRow materialRow(
      StudentIangMaterialDefinition material,
      List<FdhReviewDocument> documents
  ) {
    boolean uploaded = !documents.isEmpty();
    String status = "muted";
    String issue = "";
    if (!material.applicable()) {
      status = "muted";
    } else if (!uploaded && material.core()) {
      status = "fail";
      issue = "未上传：" + material.shortName() + " 属于当前 demo 审批材料。";
    } else if (!uploaded) {
      status = "warn";
      issue = material.conditional()
          ? "适用时提交；当前 demo 不纳入最终阻断。"
          : "官方应交材料未上传；当前 demo 不纳入最终阻断。";
    } else if ("id990a".equals(material.id())) {
      Id990aPageAssessment assessment = assessId990aPages(documents);
      status = assessment.status();
      issue = assessment.issue();
    } else if ("paymentStatus".equals(material.id()) && paymentIncomplete(documents)) {
      status = "review";
      issue = "付款页显示在线申请流程尚未完成，需要补缴或上传付款成功记录。";
    } else if (documents.size() > 1 && material.core()) {
      status = "review";
      issue = material.shortName() + " 被识别到多份上传文件，需要人工确认是否重复或错件。";
    } else {
      status = "pass";
    }

    return new FdhReviewResult.MaterialRow(
        material.id(),
        material.no(),
        material.name(),
        material.shortName(),
        material.templateId(),
        material.expectedPages(),
        material.applicable(),
        uploaded,
        material.core(),
        material.core(),
        material.conditional(),
        status,
        materialStatusText(material, status, uploaded),
        material.scopeText(),
        documents.stream().map(FdhReviewDocument::filename).toList(),
        issue
    );
  }

  private String materialStatusText(StudentIangMaterialDefinition material, String status, boolean uploaded) {
    if (!uploaded) {
      return "muted".equals(status) ? "不适用" : "未上传";
    }
    if ("id990a".equals(material.id()) && "pass".equals(status)) {
      return "已识别前 5 页";
    }
    if ("paymentStatus".equals(material.id()) && "review".equals(status)) {
      return "显示尚未完成付款";
    }
    return switch (status) {
      case "pass" -> "已识别";
      case "fail" -> "缺失或不完整";
      case "review" -> "需人工复核";
      case "warn" -> "未上传";
      default -> "不适用";
    };
  }

  private Id990aPageAssessment assessId990aPages(List<FdhReviewDocument> documents) {
    List<Integer> officialPageNumbers = documents.stream()
        .flatMap(document -> document.officialPageNumbers().stream())
        .toList();
    if (!officialPageNumbers.isEmpty()) {
      List<Integer> missingPages = missingOfficialPages(officialPageNumbers, ID990A_REQUIRED_PAGE_COUNT);
      if (missingPages.isEmpty()) {
        return new Id990aPageAssessment("pass", "");
      }
      return new Id990aPageAssessment(
          "fail",
          "ID 990A 按页尾页码判断缺少" + missingPageText(missingPages) + "。"
      );
    }
    int extractedPages = documents.stream()
        .map(FdhReviewDocument::ocrResult)
        .filter(Objects::nonNull)
        .mapToInt(OcrDemoResponse::pageCount)
        .max()
        .orElse(0);
    if (extractedPages < ID990A_REQUIRED_PAGE_COUNT) {
      return new Id990aPageAssessment(
          "fail",
          "ID 990A 当前仅识别前 5 页，但上传材料可识别页数不足 5 页。"
      );
    }
    return new Id990aPageAssessment(
        "review",
        "ID 990A 未能识别官方页码，需人工确认是否包含第 1 至第 5 页。"
    );
  }

  private List<Integer> missingOfficialPages(List<Integer> officialPageNumbers, int requiredPageCount) {
    List<Integer> missing = new ArrayList<>();
    for (int page = 1; page <= requiredPageCount; page += 1) {
      if (!officialPageNumbers.contains(page)) {
        missing.add(page);
      }
    }
    return missing;
  }

  private String missingPageText(List<Integer> missingPages) {
    return missingPages.stream()
        .map(page -> "第 " + page + " 页")
        .reduce((left, right) -> left + "、" + right)
        .orElse("官方页码");
  }

  private List<FdhReviewResult.StandardField> buildFields(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.StandardField> fields = new ArrayList<>();
    fields.add(applicantNameField(documents));
    fields.add(standardField(
        "applicant.hk_identity_card_no",
        "申请人及身份信息",
        "香港身份证号码",
        false,
        true,
        "HKID 在申请表、毕业证明和身份证样本中应一致；如无 HKID 则按官方 if any 处理。",
        documents,
        List.of(spec("id990a", "hk", "identity"), spec("educationProof", "hk", "identity"), spec("identityDocs", "hk", "identity"))
    ));
    fields.add(standardField(
        "applicant.travel_doc.number",
        "申请人及身份信息",
        "港澳通行证 / 护照号码",
        true,
        true,
        "申请表上的旅行证件号码应与港澳通行证或护照资料页一致。",
        documents,
        List.of(
            spec("id990a", "travel", "document", "no"),
            spec("id990a", "travel", "document", "number"),
            spec("identityDocs", "document", "number"),
            spec("identityDocs", "permit", "no"),
            spec("identityDocs", "passport", "no")
        )
    ));
    fields.add(standardField(
        "applicant.date_of_birth",
        "申请人及身份信息",
        "出生日期",
        true,
        true,
        "申请表和旅行证件资料页的出生日期应一致。",
        documents,
        List.of(spec("id990a", "date", "birth"), spec("identityDocs", "date", "birth"))
    ));
    fields.add(standardField(
        "education.institution",
        "学历与毕业资格",
        "毕业院校",
        true,
        true,
        "毕业院校应为香港认可院校或符合 IANG 资格的院校范围。",
        documents,
        List.of(spec("educationProof", "institution"), spec("educationProof", "university"), spec("id990a", "institution"))
    ));
    fields.add(standardField(
        "education.programme",
        "学历与毕业资格",
        "课程 / 学位",
        true,
        true,
        "申请人应已完成本科或以上课程；课程信息以院校证明为主。",
        documents,
        List.of(
            spec("educationProof", "programme", "degree"),
            spec("educationProof", "programme"),
            spec("educationProof", "degree"),
            spec("id990a", "subject", "degree"),
            spec("id990a", "subject"),
            spec("id990a", "major")
        )
    ));
    fields.add(standardField(
        "education.graduation_date",
        "学历与毕业资格",
        "毕业 / 完成课程日期",
        true,
        true,
        "应届毕业生通常以毕业证书或院校证明所载日期判断 6 个月申请窗口。",
        documents,
        List.of(
            spec("educationProof", "completion", "date"),
            spec("educationProof", "graduation", "date"),
            spec("educationProof", "date"),
            spec("id990a", "graduation", "date")
        )
    ));
    fields.add(paymentStatusField(documents));
    fields.add(signatureField(documents));
    return fields;
  }

  private FdhReviewResult.StandardField paymentStatusField(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = new ArrayList<>(sourcesFor(
        documents,
        List.of(
            spec("paymentStatus", "payment", "status"),
            spec("paymentStatus", "application", "process"),
            spec("paymentStatus", "not", "yet", "complete")
        )
    ));
    if (sources.isEmpty()) {
      sources.addAll(paymentIncompleteTitleSources(documents));
    }
    String value = normalizeDisplayValue(sources);
    boolean incomplete = sources.stream().anyMatch(source -> isIncompletePayment(source.value()));
    String status = sources.isEmpty() || incomplete ? "fail" : "pass";
    String issue = sources.isEmpty()
        ? "未识别到申请费付款状态。"
        : incomplete ? "付款页显示在线申请流程尚未完成，需要补缴或上传付款成功记录。" : "";
    return new FdhReviewResult.StandardField(
        "payment.application_fee_status",
        "付款状态",
        "申请费付款状态",
        true,
        value,
        status,
        issue,
        true,
        sources,
        "付款状态应与申请阶段一致；若仍显示未完成，不应自动通过。"
    );
  }

  private FdhReviewResult.StandardField applicantNameField(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = new ArrayList<>();
    sources.addAll(combinedId990aEnglishNameSources(documents));
    if (sources.isEmpty()) {
      sources.addAll(sourcesFor(documents, List.of(spec("id990a", "name", "english"))));
    }
    sources.addAll(sourcesFor(documents, List.of(
        spec("educationProof", "student", "name"),
        spec("identityDocs", "name", "english"),
        spec("paymentStatus", "applicant", "name")
    )));
    FieldAssessment assessment = assessSources("applicant.name.full_en", true, sources);
    return new FdhReviewResult.StandardField(
        "applicant.name.full_en",
        "申请人及身份信息",
        "申请人英文姓名",
        true,
        normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        true,
        sources,
        "申请表、学历证明、旅行证件及付款记录中的申请人姓名应可归一到同一申请人。"
    );
  }

  private FdhReviewResult.StandardField signatureField(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = sourcesFor(
        documents,
        List.of(spec("id990a", "signature"), spec("id990a", "declaration", "date"))
    );
    boolean hasSignature = sources.stream()
        .anyMatch(source -> normalized(source.value()).contains("signature") || normalized(source.value()).contains("detected"));
    return new FdhReviewResult.StandardField(
        "id990a.signature.present",
        "IANG/专业人士申请表",
        "申请人声明及签署",
        true,
        hasSignature ? "signature detected" : normalizeDisplayValue(sources),
        sources.isEmpty() || !hasSignature ? "review" : "pass",
        sources.isEmpty() || !hasSignature ? "ID 990A 前 5 页未能确认申请人声明及签署。" : "",
        true,
        sources,
        "ID 990A 前 5 页识别范围内，申请人声明及签署应存在。"
    );
  }

  private FdhReviewResult.StandardField standardField(
      String key,
      String category,
      String label,
      boolean required,
      boolean blocking,
      String rule,
      List<FdhReviewDocument> documents,
      List<SourceSpec> specs
  ) {
    List<FdhReviewResult.FieldSource> sources = sourcesFor(documents, specs);
    FieldAssessment assessment = assessSources(key, required, sources);
    return new FdhReviewResult.StandardField(
        key,
        category,
        label,
        required,
        normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        blocking,
        sources,
        rule
    );
  }

  private FieldAssessment assessSources(String fieldKey, boolean required, List<FdhReviewResult.FieldSource> sources) {
    if (sources.isEmpty()) {
      return required
          ? new FieldAssessment("fail", "必填字段未识别。")
          : new FieldAssessment("review", "未识别到可用字段；如官方填写为 if any，可由人工确认。");
    }
    if (sources.stream().anyMatch(source -> source.confidence() > 0 && source.confidence() < 75)) {
      return new FieldAssessment("review", "字段置信度偏低，需要人工复核。");
    }
    Set<String> normalizedValues = sources.stream()
        .map(FdhReviewResult.FieldSource::value)
        .map(value -> normalizedComparable(fieldKey, value))
        .filter(value -> !value.isBlank())
        .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    if (normalizedValues.size() > 1) {
      return new FieldAssessment("review", "跨材料字段值不一致，需要人工复核。");
    }
    return new FieldAssessment("pass", "");
  }

  private List<FdhReviewResult.FieldSource> sourcesFor(List<FdhReviewDocument> documents, List<SourceSpec> specs) {
    List<FdhReviewResult.FieldSource> sources = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (SourceSpec spec : specs) {
      for (FdhReviewDocument document : docsFor(documents, spec.materialId())) {
        Optional<ExtractedValue> value = findValue(document, spec.tokens());
        if (value.isEmpty()) {
          continue;
        }
        ExtractedValue extracted = value.get();
        String sourceKey = document.filename() + ":" + extracted.path();
        if (!seen.add(sourceKey)) {
          continue;
        }
        sources.add(new FdhReviewResult.FieldSource(
            StudentIangMaterialCatalog.displayName(document.materialId()),
            document.filename(),
            sectionLabel(document.materialId(), extracted.page()),
            prettyLabel(extracted.label()),
            extracted.value(),
            extracted.confidence(),
            extracted.value(),
            "",
            document.materialId(),
            extracted.page(),
            extracted.imageWidth(),
            extracted.imageHeight(),
            extracted.bbox(),
            locatorConfidence(extracted)
        ));
      }
    }
    return sources;
  }

  private List<FdhReviewResult.FieldSource> combinedId990aEnglishNameSources(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = new ArrayList<>();
    for (FdhReviewDocument document : docsFor(documents, "id990a")) {
      Optional<ExtractedValue> surname = firstEnglishNamePart(document, "surname");
      Optional<ExtractedValue> given = firstEnglishNamePart(document, "given");
      if (surname.isEmpty() || given.isEmpty()) {
        continue;
      }
      ExtractedValue left = surname.get();
      ExtractedValue right = given.get();
      String combined = (left.value() + " " + right.value()).trim();
      int page = Math.max(left.page(), right.page());
      sources.add(new FdhReviewResult.FieldSource(
          StudentIangMaterialCatalog.displayName(document.materialId()),
          document.filename(),
          sectionLabel(document.materialId(), page),
          "Surname in English + Given names in English",
          combined,
          Math.min(left.confidence(), right.confidence()),
          combined,
          "",
          document.materialId(),
          page,
          left.imageWidth() > 0 ? left.imageWidth() : right.imageWidth(),
          left.imageHeight() > 0 ? left.imageHeight() : right.imageHeight(),
          left.bbox().isEmpty() ? right.bbox() : left.bbox(),
          Math.min(locatorConfidence(left), locatorConfidence(right))
      ));
    }
    return sources;
  }

  private Optional<ExtractedValue> firstEnglishNamePart(FdhReviewDocument document, String token) {
    return flatten(document).stream()
        .filter(value -> matchesKey(value, List.of(token, "english")) || matchesKey(value, List.of(token, "en")))
        .filter(value -> isLatinNameValue(value.value()))
        .findFirst();
  }

  private static boolean isLatinNameValue(String value) {
    String cleaned = normalizedName(value);
    return !cleaned.isBlank() && cleaned.matches("[a-z]+");
  }

  private List<FdhReviewResult.FieldSource> paymentIncompleteTitleSources(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = new ArrayList<>();
    for (FdhReviewDocument document : docsFor(documents, "paymentStatus")) {
      Optional<FdhReviewResult.FieldSource> structured = flatten(document).stream()
          .filter(value -> isIncompletePayment(value.value()))
          .findFirst()
          .map(value -> new FdhReviewResult.FieldSource(
              StudentIangMaterialCatalog.displayName(document.materialId()),
              document.filename(),
              sectionLabel(document.materialId(), value.page()),
              "Payment page title",
              canonicalIncompletePaymentTitle(),
              value.confidence(),
              canonicalIncompletePaymentTitle(),
              "",
              document.materialId(),
              value.page(),
              value.imageWidth(),
              value.imageHeight(),
              value.bbox(),
              locatorConfidence(value)
          ));
      if (structured.isPresent()) {
        sources.add(structured.get());
        continue;
      }
      paymentPageText(document)
          .filter(StudentIangReviewAssembler::isIncompletePayment)
          .findFirst()
          .ifPresent(text -> sources.add(new FdhReviewResult.FieldSource(
              StudentIangMaterialCatalog.displayName(document.materialId()),
              document.filename(),
              sectionLabel(document.materialId(), 1),
              "Payment page title",
              canonicalIncompletePaymentTitle(),
              88,
              canonicalIncompletePaymentTitle(),
              ""
          )));
      if (sources.stream().noneMatch(source -> document.filename().equals(source.filename()))
          && looksLikeIncompletePaymentPage(document)) {
        sources.add(new FdhReviewResult.FieldSource(
            StudentIangMaterialCatalog.displayName(document.materialId()),
            document.filename(),
            sectionLabel(document.materialId(), 1),
            "Payment page title",
            canonicalIncompletePaymentTitle(),
            88,
            canonicalIncompletePaymentTitle(),
            ""
        ));
      }
    }
    return sources;
  }

  private boolean looksLikeIncompletePaymentPage(FdhReviewDocument document) {
    List<ExtractedValue> values = flatten(document);
    boolean hasApplicant = values.stream().anyMatch(value -> matchesKey(value, List.of("applicant", "name")));
    boolean hasReference = values.stream().anyMatch(value -> matches(value, List.of("reference")))
        || values.stream().anyMatch(value -> matches(value, List.of("application", "number")));
    boolean hasFee = values.stream().anyMatch(value -> matches(value, List.of("application", "fee")))
        || values.stream().anyMatch(value -> normalized(value.value()).contains("hk$"));
    return hasApplicant && hasReference && hasFee;
  }

  private java.util.stream.Stream<String> paymentPageText(FdhReviewDocument document) {
    if (document == null || document.ocrResult() == null) {
      return java.util.stream.Stream.empty();
    }
    List<String> texts = new ArrayList<>();
    if (document.ocrResult().rawStructuredText() != null) {
      texts.add(document.ocrResult().rawStructuredText());
    }
    List<OcrPage> pages = document.ocrResult().pages() == null ? List.of() : document.ocrResult().pages();
    for (OcrPage page : pages) {
      if (page.markdown() != null) {
        texts.add(page.markdown());
      }
      for (var block : page.blocks()) {
        if (block.content() != null) {
          texts.add(block.content());
        }
      }
      for (var line : page.lines()) {
        String lineText = line.spans().stream()
            .map(span -> span.text() == null ? "" : span.text())
            .reduce("", (left, right) -> left + " " + right)
            .trim();
        if (!lineText.isBlank()) {
          texts.add(lineText);
        }
      }
    }
    return texts.stream();
  }

  private static String canonicalIncompletePaymentTitle() {
    return "the online application process is not yet complete.";
  }

  private Optional<ExtractedValue> findValue(FdhReviewDocument document, List<String> tokens) {
    return flatten(document).stream()
        .filter(value -> matches(value, tokens))
        .findFirst();
  }

  private Optional<ExtractedValue> findValueByKey(FdhReviewDocument document, List<String> tokens) {
    return flatten(document).stream()
        .filter(value -> matchesKey(value, tokens))
        .findFirst();
  }

  private boolean matches(ExtractedValue value, List<String> tokens) {
    String haystack = normalized(value.path() + " " + value.label() + " " + value.value());
    for (String token : tokens) {
      if (!haystack.contains(normalized(token))) {
        return false;
      }
    }
    return true;
  }

  private List<ExtractedValue> flatten(FdhReviewDocument document) {
    if (document == null || document.ocrResult() == null || document.ocrResult().structuredData() == null) {
      return List.of();
    }
    Map<String, StructuredFieldDetail> detailsByPath = detailsByPath(document.ocrResult());
    List<ExtractedValue> values = new ArrayList<>();
    flatten(document, document.ocrResult().structuredData(), "", 0, values, detailsByPath);
    return values;
  }

  private void flatten(
      FdhReviewDocument document,
      JsonNode node,
      String path,
      int page,
      List<ExtractedValue> values,
      Map<String, StructuredFieldDetail> detailsByPath
  ) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> {
        String key = entry.getKey();
        if (key.startsWith("_")) {
          return;
        }
        String nextPath = path.isBlank() ? key : path + "." + key;
        int nextPage = pageFromKey(key).orElse(page);
        flatten(document, entry.getValue(), nextPath, nextPage, values, detailsByPath);
      });
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index += 1) {
        flatten(document, node.get(index), path + "[" + index + "]", page, values, detailsByPath);
      }
      return;
    }
    String value = node.isTextual() ? node.asText() : node.asText("");
    if (value == null || value.isBlank()) {
      return;
    }
    StructuredFieldDetail detail = detailsByPath.get(path);
    OcrPage sourcePage = pageByNo(document.ocrResult(), detail == null || detail.page() <= 0 ? Math.max(1, page) : detail.page());
    values.add(new ExtractedValue(
        document,
        path,
        detail == null ? leaf(path) : detail.label(),
        value,
        detail == null || detail.page() <= 0 ? Math.max(1, page) : detail.page(),
        detail == null ? 88 : detail.confidence(),
        detail == null ? "" : detail.snapshotDataUrl(),
        sourcePage == null ? 0 : sourcePage.imageWidth(),
        sourcePage == null ? 0 : sourcePage.imageHeight(),
        detail == null ? List.of() : detail.bbox()
    ));
  }

  private OcrPage pageByNo(OcrDemoResponse response, int pageNo) {
    if (response == null || response.pages() == null) {
      return null;
    }
    return response.pages().stream()
        .filter(page -> page.page() == pageNo)
        .findFirst()
        .orElse(null);
  }

  private double locatorConfidence(ExtractedValue value) {
    return value.bbox().isEmpty() ? 0 : value.confidence();
  }

  private Map<String, StructuredFieldDetail> detailsByPath(OcrDemoResponse response) {
    Map<String, StructuredFieldDetail> details = new LinkedHashMap<>();
    if (response == null || response.pages() == null) {
      return details;
    }
    for (OcrPage page : response.pages()) {
      for (StructuredFieldDetail detail : page.structuredFields()) {
        details.putIfAbsent(detail.path(), detail);
        details.putIfAbsent("page_" + detail.page() + "." + detail.path(), detail);
      }
    }
    return details;
  }

  private List<FdhReviewResult.DocumentFieldGroup> documentFieldGroups(List<FdhReviewDocument> documents) {
    return documents.stream()
        .map(document -> new FdhReviewResult.DocumentFieldGroup(
            document.materialId(),
            materialName(document.materialId()),
            documentTemplateId(document),
            "id990a".equals(document.materialId()) ? "仅识别前 5 页" : "",
            documentFieldPages(document)
        ))
        .toList();
  }

  private String documentTemplateId(FdhReviewDocument document) {
    return StudentIangMaterialCatalog.find(document.materialId())
        .map(StudentIangMaterialDefinition::templateId)
        .orElseGet(() -> document.template() == null ? "" : document.template().templateId());
  }

  private List<FdhReviewResult.DocumentFieldPage> documentFieldPages(FdhReviewDocument document) {
    OcrDemoResponse response = document.ocrResult();
    List<OcrPage> pages = response == null || response.pages() == null ? List.of() : response.pages();
    Map<Integer, List<ExtractedValue>> valuesByPage = new LinkedHashMap<>();
    for (ExtractedValue value : flatten(document)) {
      valuesByPage.computeIfAbsent(Math.max(1, value.page()), ignored -> new ArrayList<>()).add(value);
    }
    if (pages.isEmpty()) {
      return valuesByPage.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(entry -> new FdhReviewResult.DocumentFieldPage(
              entry.getKey(),
              pageTitle(document.materialId(), entry.getKey()),
              documentFields(document.materialId(), entry.getValue())
          ))
          .toList();
    }
    return pages.stream()
        .sorted(Comparator.comparingInt(OcrPage::page))
        .map(page -> new FdhReviewResult.DocumentFieldPage(
            page.page(),
            pageTitle(document.materialId(), page.page()),
            documentFields(document.materialId(), valuesByPage.getOrDefault(page.page(), List.of()))
        ))
        .toList();
  }

  private List<FdhReviewResult.DocumentField> documentFields(String materialId, List<ExtractedValue> values) {
    if (isUnknownMaterial(materialId)) {
      return values.stream()
          .map(value -> documentFieldFromExtracted(prettyLabel(value.label()), materialId, value))
          .toList();
    }
    if ("educationProof".equals(materialId)) {
      return conciseEducationProofFields(values);
    }
    if ("paymentStatus".equals(materialId)) {
      return concisePaymentStatusFields(values);
    }
    return values.stream()
        .filter(this::hasDisplayableFilledValue)
        .map(value -> documentFieldFromExtracted(prettyLabel(value.label()), materialId, value))
        .toList();
  }

  private boolean isUnknownMaterial(String materialId) {
    return !StudentIangMaterialCatalog.find(materialId).isPresent();
  }

  private boolean hasDisplayableFilledValue(ExtractedValue value) {
    if (value == null) {
      return false;
    }
    String path = normalized(value.path());
    String label = normalized(value.label());
    String actual = normalized(value.value());
    if ((path.contains("declaration") || label.contains("declaration")) && isBooleanLiteral(actual)) {
      return false;
    }
    return true;
  }

  private static boolean isBooleanLiteral(String value) {
    return "true".equals(value) || "false".equals(value);
  }

  private List<FdhReviewResult.DocumentField> conciseEducationProofFields(List<ExtractedValue> values) {
    return List.of(
        conciseField("Ref", firstValueByKey(values, List.of("ref"), List.of("reference"))),
        conciseField("收件人", firstValueByKey(values, List.of("to"), List.of("recipient"))),
        conciseField("姓名", firstValueByKey(values, List.of("student", "name"), List.of("name"))),
        conciseField("身份证号", firstValueByKey(values, List.of("hk", "identity"), List.of("identity", "card"))),
        conciseField("大学", firstValueByKey(values, List.of("issuing", "institution"), List.of("institution"), List.of("university"))),
        conciseField("学科及学位", educationProgrammeValue(values)),
        conciseField("日期", firstValueByKey(values, List.of("completion", "date"), List.of("graduation", "date"), List.of("date")))
    );
  }

  private List<FdhReviewResult.DocumentField> concisePaymentStatusFields(List<ExtractedValue> values) {
    return List.of(
        conciseField("申请人", firstValueByKey(values, List.of("applicant", "name"))),
        conciseField("申请编号", firstValueByKey(
            values,
            List.of("temporary", "application", "reference"),
            List.of("application", "reference"),
            List.of("payment", "reference")
        )),
        conciseField("申请人数", firstValueByKey(
            values,
            List.of("total", "no", "applicant"),
            List.of("total", "applicant"),
            List.of("applicant", "count")
        )),
        conciseField("每份申请需缴纳的申请费", firstValueByKey(
            values,
            List.of("fee", "each", "application"),
            List.of("paid", "each", "application"),
            List.of("application", "fee", "each")
        )),
        conciseField("申请费总金额", firstValueByKey(
            values,
            List.of("total", "amount", "application", "fee"),
            List.of("total", "application", "fee"),
            List.of("amount", "application", "fee")
        ))
    );
  }

  @SafeVarargs
  private final Optional<ExtractedValue> firstValue(List<ExtractedValue> values, List<String>... tokenGroups) {
    for (List<String> tokens : tokenGroups) {
      Optional<ExtractedValue> matched = values.stream()
          .filter(value -> matches(value, tokens))
          .findFirst();
      if (matched.isPresent()) {
        return matched;
      }
    }
    return Optional.empty();
  }

  @SafeVarargs
  private final Optional<ExtractedValue> firstValueByKey(List<ExtractedValue> values, List<String>... tokenGroups) {
    for (List<String> tokens : tokenGroups) {
      Optional<ExtractedValue> matched = values.stream()
          .filter(value -> matchesKey(value, tokens))
          .findFirst();
      if (matched.isPresent()) {
        return matched;
      }
    }
    return Optional.empty();
  }

  private Optional<ExtractedValue> educationProgrammeValue(List<ExtractedValue> values) {
    Optional<ExtractedValue> direct = firstValueByKey(
        values,
        List.of("programme", "degree"),
        List.of("program", "degree"),
        List.of("programme"),
        List.of("program"),
        List.of("degree")
    );
    if (direct.isPresent()) {
      return direct;
    }
    return values.stream()
        .filter(value -> matchesKey(value, List.of("certification", "text")) || matchesKey(value, List.of("certify")))
        .map(this::programmeFromCertificationText)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private Optional<ExtractedValue> programmeFromCertificationText(ExtractedValue value) {
    Matcher matcher = PROGRAMME_IN_CERTIFICATION_TEXT.matcher(value.value());
    if (!matcher.find()) {
      return Optional.empty();
    }
    String programme = matcher.group(1).trim();
    if (programme.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new ExtractedValue(
        value.document(),
        value.path(),
        "programme_degree",
        programme,
        value.page(),
        value.confidence(),
        value.snapshotDataUrl(),
        value.imageWidth(),
        value.imageHeight(),
        value.bbox()
    ));
  }

  private boolean matchesKey(ExtractedValue value, List<String> tokens) {
    String haystack = keyText(value);
    for (String token : tokens) {
      if (!containsKeyToken(haystack, token)) {
        return false;
      }
    }
    return true;
  }

  private static String keyText(ExtractedValue value) {
    return normalized(value.path() + " " + value.label())
        .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", " ")
        .trim();
  }

  private static boolean containsKeyToken(String haystack, String token) {
    String normalizedToken = normalized(token)
        .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", " ")
        .trim();
    if (normalizedToken.isBlank()) {
      return true;
    }
    if (!normalizedToken.matches(".*[a-z0-9].*")) {
      return haystack.contains(normalizedToken);
    }
    return Pattern.compile("(^|\\s)" + Pattern.quote(normalizedToken) + "($|\\s)")
        .matcher(haystack)
        .find();
  }

  private FdhReviewResult.DocumentField conciseField(String label, Optional<ExtractedValue> value) {
    return value
        .map(extracted -> documentFieldFromExtracted(label, extracted.document().materialId(), extracted))
        .orElseGet(() -> new FdhReviewResult.DocumentField(label, "", "review"));
  }

  private FdhReviewResult.DocumentField documentFieldFromExtracted(String label, String materialId, ExtractedValue value) {
    return new FdhReviewResult.DocumentField(
        label,
        value.value(),
        documentFieldStatus(materialId, value),
        value.confidence(),
        value.page(),
        value.imageWidth(),
        value.imageHeight(),
        value.bbox(),
        value.bbox().isEmpty() ? 0 : value.confidence()
    );
  }

  private String documentFieldStatus(String materialId, ExtractedValue value) {
    if ("paymentStatus".equals(materialId) && isIncompletePayment(value.value())) {
      return "fail";
    }
    return value.value().isBlank() ? "review" : "pass";
  }

  private String pageTitle(String materialId, int page) {
    if ("id990a".equals(materialId)) {
      return switch (page) {
        case 1 -> "申请类别";
        case 2 -> "个人资料及旅行证件";
        case 3 -> "联络资料";
        case 4 -> "学历资料";
        case 5 -> "在港逗留及申请资料";
        case 6 -> "声明及签署";
        default -> "ID 990A 字段";
      };
    }
    if ("educationProof".equals(materialId)) {
      return "毕业资格证明";
    }
    if ("identityDocs".equals(materialId)) {
      return page <= 1 ? "港澳通行证 / 护照资料页" : "香港身份证资料";
    }
    if ("paymentStatus".equals(materialId)) {
      return "付款页面";
    }
    return "识别字段";
  }

  private String sectionLabel(String materialId, int page) {
    return "第 " + Math.max(1, page) + " 页 " + pageTitle(materialId, page);
  }

  private String materialName(String materialId) {
    return StudentIangMaterialCatalog.find(materialId)
        .map(material -> material.shortName() + " " + material.name())
        .orElse(StudentIangMaterialCatalog.displayName(materialId));
  }

  private boolean paymentIncomplete(List<FdhReviewDocument> documents) {
    boolean explicitIncomplete = docsFor(documents, "paymentStatus").stream()
        .flatMap(document -> flatten(document).stream())
        .anyMatch(value -> isIncompletePayment(value.value()));
    return explicitIncomplete || !paymentIncompleteTitleSources(documents).isEmpty();
  }

  private static boolean isIncompletePayment(String value) {
    String normalized = normalized(value);
    return normalized.contains("not yet complete")
        || normalized.contains("not complete")
        || normalized.contains("未完成")
        || normalized.contains("尚未完成");
  }

  private Map<String, List<FdhReviewDocument>> documentsByMaterial(List<FdhReviewDocument> documents) {
    Map<String, List<FdhReviewDocument>> byMaterial = new LinkedHashMap<>();
    for (FdhReviewDocument document : documents) {
      byMaterial.computeIfAbsent(document.materialId(), ignored -> new ArrayList<>()).add(document);
    }
    return byMaterial;
  }

  private List<FdhReviewDocument> docsFor(List<FdhReviewDocument> documents, String materialId) {
    return documents.stream()
        .filter(document -> materialId.equals(document.materialId()))
        .toList();
  }

  private String decision(List<FdhReviewResult.MaterialRow> materials, List<FdhReviewResult.StandardField> fields) {
    boolean fail = materials.stream().anyMatch(row -> row.blocking() && "fail".equals(row.status()))
        || fields.stream().anyMatch(field -> field.blocking() && "fail".equals(field.status()));
    if (fail) {
      return "FAIL";
    }
    boolean review = materials.stream().anyMatch(row -> row.blocking() && "review".equals(row.status()))
        || fields.stream().anyMatch(field -> field.blocking() && "review".equals(field.status()));
    return review ? "REVIEW" : "PASS";
  }

  private String decisionText(
      String decision,
      List<FdhReviewResult.MaterialRow> materials,
      List<FdhReviewResult.StandardField> fields
  ) {
    if ("PASS".equals(decision)) {
      return "核心材料及关键字段可通过。";
    }
    Optional<FdhReviewResult.MaterialRow> materialIssue = materials.stream()
        .filter(row -> row.blocking() && ("fail".equals(row.status()) || "review".equals(row.status())))
        .findFirst();
    if (materialIssue.isPresent()) {
      return decision + " - " + materialIssue.get().shortName() + "：" + materialIssue.get().statusText() + "。";
    }
    Optional<FdhReviewResult.StandardField> fieldIssue = fields.stream()
        .filter(field -> field.blocking() && ("fail".equals(field.status()) || "review".equals(field.status())))
        .findFirst();
    return fieldIssue
        .map(field -> decision + " - " + field.label() + "：" + field.issue())
        .orElse(decision + " - 需人工复核。");
  }

  private FdhReviewResult.FieldStats stats(List<FdhReviewResult.StandardField> fields) {
    int pass = (int) fields.stream().filter(field -> "pass".equals(field.status())).count();
    int fail = (int) fields.stream().filter(field -> "fail".equals(field.status())).count();
    int review = (int) fields.stream().filter(field -> "review".equals(field.status())).count();
    int required = (int) fields.stream().filter(FdhReviewResult.StandardField::required).count();
    return new FdhReviewResult.FieldStats(fields.size(), pass, fail, review, required);
  }

  private static String normalizeDisplayValue(List<FdhReviewResult.FieldSource> sources) {
    return sources.stream()
        .map(FdhReviewResult.FieldSource::value)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse("");
  }

  private static Optional<Integer> pageFromKey(String key) {
    Matcher matcher = PAGE_KEY_PATTERN.matcher(key == null ? "" : key);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(Integer.parseInt(matcher.group(1)));
  }

  private static String leaf(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    int dot = path.lastIndexOf('.');
    return dot >= 0 ? path.substring(dot + 1) : path;
  }

  private static String prettyLabel(String value) {
    String label = value == null ? "" : value;
    int dot = label.lastIndexOf('.');
    if (dot >= 0) {
      label = label.substring(dot + 1);
    }
    label = label.replace('_', ' ').replace('-', ' ').trim();
    if (label.isBlank()) {
      return "识别字段";
    }
    return Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  private static String normalizedComparable(String fieldKey, String value) {
    String normalized = normalized(value);
    if ("applicant.name.full_en".equals(fieldKey)) {
      return normalizedName(normalized);
    }
    if ("education.institution".equals(fieldKey)) {
      return normalizedInstitution(normalized);
    }
    if ("education.programme".equals(fieldKey)) {
      return normalizedProgramme(normalized);
    }
    if ("education.graduation_date".equals(fieldKey)) {
      String month = normalizedYearMonth(normalized);
      if (!month.isBlank()) {
        return month;
      }
    }
    String date = normalizedDate(normalized);
    if (!date.isBlank()) {
      return date;
    }
    return normalized.replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
  }

  private static String normalizedName(String value) {
    String cleaned = value
        .replaceAll("\\b(mr|mrs|ms|miss|dr)\\.?\\b", " ")
        .replaceAll("\\([^)]*\\)", " ")
        .replace("*", " ")
        .replaceAll("[^a-z\\s]", " ")
        .replaceAll("\\s+", " ")
        .trim();
    return cleaned.replace(" ", "");
  }

  private static String normalizedInstitution(String value) {
    String compact = value.replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
    if (compact.contains("thechineseuniversityofhongkong")
        || compact.contains("chineseuniversityofhongkong")
        || compact.contains("cuhk")
        || compact.contains("香港中文大学")
        || compact.contains("香港中文大學")) {
      return "cuhk";
    }
    return compact;
  }

  private static String normalizedProgramme(String value) {
    String compact = value.replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
    if (compact.contains("computerscience") || compact.contains("计算机科学") || compact.contains("計算機科學")) {
      return "computerscience";
    }
    return compact
        .replace("masterofsciencein", "")
        .replace("masterofscience", "")
        .replace("fulltime", "");
  }

  private static String normalizedYearMonth(String value) {
    String exact = normalizedDate(value);
    if (!exact.isBlank()) {
      return exact.substring(0, 7);
    }
    Matcher chinese = Pattern.compile("\\b((?:19|20)\\d{2})\\s*年\\s*(\\d{1,2})\\s*月").matcher(value);
    if (chinese.find()) {
      return "%04d-%02d".formatted(Integer.parseInt(chinese.group(1)), Integer.parseInt(chinese.group(2)));
    }
    Matcher numeric = Pattern.compile("\\b((?:19|20)\\d{2})[.\\-/](\\d{1,2})\\b").matcher(value);
    if (numeric.find()) {
      return "%04d-%02d".formatted(Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)));
    }
    Matcher english = Pattern.compile("\\b([a-z]+)\\s+((?:19|20)\\d{2})\\b").matcher(value);
    if (english.find()) {
      int month = monthNumber(english.group(1));
      if (month > 0) {
        return "%04d-%02d".formatted(Integer.parseInt(english.group(2)), month);
      }
    }
    return "";
  }

  private static String normalizedDate(String value) {
    Matcher slash = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").matcher(value);
    if (slash.find()) {
      return "%04d-%02d-%02d".formatted(
          Integer.parseInt(slash.group(3)),
          Integer.parseInt(slash.group(2)),
          Integer.parseInt(slash.group(1))
      );
    }
    Matcher dotted = Pattern.compile("\\b(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})\\b").matcher(value);
    if (dotted.find()) {
      return "%04d-%02d-%02d".formatted(
          Integer.parseInt(dotted.group(1)),
          Integer.parseInt(dotted.group(2)),
          Integer.parseInt(dotted.group(3))
      );
    }
    Matcher english = Pattern.compile("\\b(\\d{1,2})\\s+([a-z]+)\\s+(\\d{4})\\b").matcher(value);
    if (english.find()) {
      int month = monthNumber(english.group(2));
      if (month > 0) {
        return "%04d-%02d-%02d".formatted(
            Integer.parseInt(english.group(3)),
            month,
            Integer.parseInt(english.group(1))
        );
      }
    }
    return "";
  }

  private static int monthNumber(String value) {
    return switch ((value == null ? "" : value).toLowerCase(Locale.ROOT)) {
      case "jan", "january" -> 1;
      case "feb", "february" -> 2;
      case "mar", "march" -> 3;
      case "apr", "april" -> 4;
      case "may" -> 5;
      case "jun", "june" -> 6;
      case "jul", "july" -> 7;
      case "aug", "august" -> 8;
      case "sep", "sept", "september" -> 9;
      case "oct", "october" -> 10;
      case "nov", "november" -> 11;
      case "dec", "december" -> 12;
      default -> 0;
    };
  }

  private static String normalized(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }

  private static SourceSpec spec(String materialId, String... tokens) {
    return new SourceSpec(materialId, List.of(tokens));
  }

  private record SourceSpec(String materialId, List<String> tokens) {}

  private record FieldAssessment(String status, String issue) {}

  private record Id990aPageAssessment(String status, String issue) {}

  private record ExtractedValue(
      FdhReviewDocument document,
      String path,
      String label,
      String value,
      int page,
      double confidence,
      String snapshotDataUrl,
      int imageWidth,
      int imageHeight,
      List<Integer> bbox
  ) {
    private ExtractedValue(
        FdhReviewDocument document,
        String path,
        String label,
        String value,
        int page,
        double confidence,
        String snapshotDataUrl
    ) {
      this(document, path, label, value, page, confidence, snapshotDataUrl, 0, 0, List.of());
    }
  }
}

