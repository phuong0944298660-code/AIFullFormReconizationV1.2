package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.ParallelRecognitionOutput;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FdhReviewAssembler {

  private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final Pattern MONEY_PATTERN = Pattern.compile("([0-9][0-9,]*(?:\\.\\d+)?)");
  private static final Pattern CJK_PATTERN = Pattern.compile("\\p{IsHan}");
  private static final Pattern WORK_EXPERIENCE_PERIOD_PATH_PATTERN =
      Pattern.compile("(?:^|\\.)employer_(\\d+)_(name|address|period_from|period_to)$");
  private static final Pattern WORK_EXPERIENCE_TOTAL_DURATION_PATH_PATTERN =
      Pattern.compile("(?:^|\\.)total_duration_(years|months)$");
  private static final String ID988A_ENTRY_TO_HK_APPLICATION_TYPE =
      "entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad";
  private static final String ID988A_CONTRACT_RENEWAL_APPLICATION_TYPE =
      "contract_renewal_with_the_same_employer_or_change_of_employer";
  private static final String ID988A_REMAINING_CONTRACT_APPLICATION_TYPE =
      "complete_the_remaining_extended_period_of_the_current_contract";
  private static final String ID988A_ENTRY_TO_HK_LABEL =
      "Entry to Hong Kong to take up employment as a domestic helper from abroad";
  private static final String ID988A_CONTRACT_RENEWAL_LABEL =
      "Contract renewal with the same employer or change of employer";
  private static final String ID988A_REMAINING_CONTRACT_LABEL =
      "Complete the remaining/extended period of the current contract";

  // 姓名/雇主名相关 token 组：这些字段由标准化字段 helper.name.full_en /
  // employer.name.full_en 拼接消费，extractedMaterialFields 需按语义跳过，避免与标准化字段重复展示。
  private static final List<List<String>> NAME_TOKEN_GROUPS = List.of(
      group("surname"),
      group("given"),
      group("family", "name"),
      group("given", "name"),
      group("helper", "name"),
      group("name", "helper"),
      group("full", "name"),
      group("english", "name"),
      group("employer", "name"),
      group("name", "employer"),
      group("name", "chinese"),
      group("chinese", "name")
  );

  private final int minimumMonthlyWageHkd;
  private final int minimumFoodAllowanceHkd;
  private final Clock clock;

  @Autowired
  public FdhReviewAssembler(
      @Value("${fdh.minimum-monthly-wage-hkd:5100}") int minimumMonthlyWageHkd,
      @Value("${fdh.minimum-food-allowance-hkd:1236}") int minimumFoodAllowanceHkd
  ) {
    this(minimumMonthlyWageHkd, minimumFoodAllowanceHkd, Clock.systemDefaultZone());
  }

  FdhReviewAssembler(int minimumMonthlyWageHkd, int minimumFoodAllowanceHkd, Clock clock) {
    this.minimumMonthlyWageHkd = Math.max(0, minimumMonthlyWageHkd);
    this.minimumFoodAllowanceHkd = Math.max(0, minimumFoodAllowanceHkd);
    this.clock = clock == null ? Clock.systemDefaultZone() : clock;
  }

  public FdhReviewResult assemble(String applicationTypeId, List<FdhReviewDocument> documents) {
    String resolvedApplicationType = normalizeApplicationType(applicationTypeId);
    List<FdhReviewDocument> safeDocuments = documents == null ? List.of() : List.copyOf(documents);
    List<FdhReviewResult.MaterialRow> materialRows = buildMaterialRows(resolvedApplicationType, safeDocuments);
    List<FdhReviewResult.StandardField> fields = buildFields(resolvedApplicationType, safeDocuments);
    FdhReviewResult.FieldStats stats = stats(fields);
    String decision = decision(materialRows, fields);
    return new FdhReviewResult(
        resolvedApplicationType,
        uploadedFiles(safeDocuments),
        materialRows,
        fields,
        documentFieldGroups(safeDocuments),
        reviewPages(safeDocuments),
        decision,
        decisionText(decision, materialRows, fields),
        stats,
        LocalDateTime.now(clock).format(GENERATED_AT_FORMAT)
    );
  }

  private List<FdhReviewResult.DocumentFieldGroup> documentFieldGroups(List<FdhReviewDocument> documents) {
    return documents.stream()
        .map(document -> new FdhReviewResult.DocumentFieldGroup(
            document.materialId(),
            FdhMaterialCatalog.displayName(document.materialId()),
            document.template() == null ? "" : document.template().templateId(),
            document.filename() + " · " + Math.max(0, document.pageCount()) + " 页",
            documentFieldPages(document)
        ))
        .toList();
  }

  private List<FdhReviewResult.DocumentFieldPage> documentFieldPages(FdhReviewDocument document) {
    int pageLimit = recognisedPageLimit(document);
    Map<Integer, List<ExtractedValue>> valuesByPage = new LinkedHashMap<>();
    for (ExtractedValue value : flatten(document)) {
      if (!shouldDisplayExtractedValue(value)) {
        continue;
      }
      int pageNo = documentFieldPageNo(value);
      if (pageNo <= pageLimit) {
        valuesByPage.computeIfAbsent(pageNo, ignored -> new ArrayList<>()).add(value);
      }
    }
    boolean multipleApplicationTypeRows = hasMultipleApplicationTypeRows(document);

    Set<Integer> pageNumbers = new TreeSet<>();
    OcrDemoResponse response = document.ocrResult();
    if (response != null && response.pages() != null) {
      response.pages().stream()
          .map(OcrPage::page)
          .filter(page -> page > 0 && page <= pageLimit)
          .forEach(pageNumbers::add);
    }
    pageNumbers.addAll(valuesByPage.keySet());

    return pageNumbers.stream()
        .map(pageNo -> new FdhReviewResult.DocumentFieldPage(
            pageNo,
            documentFieldPageTitle(document, pageNo),
            valuesByPage.getOrDefault(pageNo, List.of()).stream()
                .map(value -> documentFieldFromExtracted(value, multipleApplicationTypeRows))
                .toList()
        ))
        .toList();
  }

  private int recognisedPageLimit(FdhReviewDocument document) {
    String templateId = document.template() == null ? "" : document.template().templateId();
    return switch (templateId) {
      case "id988a_2024_06", "id407_2016_11" -> 4;
      case "id988b_2024_06" -> 3;
      default -> Integer.MAX_VALUE;
    };
  }

  private int documentFieldPageNo(ExtractedValue value) {
    if (value.pageNo() > 0) {
      return value.pageNo();
    }
    Matcher matcher = Pattern.compile("(?:^|\\.)page_(\\d+)(?:\\.|$)").matcher(value.path() + "." + value.section());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
  }

  private String documentFieldPageTitle(FdhReviewDocument document, int pageNo) {
    return FdhMaterialCatalog.displayName(document.materialId()) + " 第 " + pageNo + " 页";
  }

  private FdhReviewResult.DocumentField documentFieldFromExtracted(ExtractedValue value, boolean multipleApplicationTypeRows) {
    return new FdhReviewResult.DocumentField(
        documentFieldLabel(value),
        value.value(),
        documentFieldStatus(value, multipleApplicationTypeRows),
        value.confidence(),
        documentFieldPageNo(value),
        value.imageWidth(),
        value.imageHeight(),
        value.bbox(),
        locatorConfidence(value),
        value.verificationScore(),
        value.verificationStatus(),
        value.judgeObservedValue(),
        value.verificationReason(),
        value.labelBbox(),
        value.valueBbox(),
        value.evidenceBbox(),
        value.locationStatus()
    );
  }

  private String documentFieldLabel(ExtractedValue value) {
    return applicationTypeCandidate(value)
        .map(ApplicationTypeCandidate::label)
        .orElse(value.fieldName());
  }

  private String documentFieldStatus(ExtractedValue value, boolean multipleApplicationTypeRows) {
    if ("disagree".equals(value.modelAgreement())) {
      return "review";
    }
    if (value.value().isBlank()) {
      return "review";
    }
    if (multipleApplicationTypeRows && applicationTypeCandidate(value).isPresent()) {
      return "review";
    }
    if ("review".equals(value.verificationStatus())) {
      return "review";
    }
    return "pass";
  }

  private boolean hasMultipleApplicationTypeRows(FdhReviewDocument document) {
    if (document == null || !"id988a".equalsIgnoreCase(document.materialId())) {
      return false;
    }
    return applicationTypeExtractions(List.of(document)).stream()
        .map(ApplicationTypeExtraction::applicationTypeKey)
        .distinct()
        .count() > 1;
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
            FdhMaterialCatalog.displayName(document.materialId()),
            document.filename(),
            page.page(),
            "第 " + page.page() + " 页",
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
              FdhMaterialCatalog.displayName(document.materialId()),
              document.filename(),
              document.pageCount(),
              template == null ? "" : template.footerId(),
              template == null ? "" : template.templateId(),
              template == null ? "" : template.matchSource()
          );
        })
        .toList();
  }

  private List<FdhReviewResult.MaterialRow> buildMaterialRows(
      String applicationTypeId,
      List<FdhReviewDocument> documents
  ) {
    Map<String, List<FdhReviewDocument>> byMaterial = new LinkedHashMap<>();
    for (FdhReviewDocument document : documents) {
      byMaterial.computeIfAbsent(document.materialId(), ignored -> new ArrayList<>()).add(document);
    }

    return FdhMaterialCatalog.materials().stream()
        .map(material -> materialRow(material, applicationTypeId, byMaterial.getOrDefault(material.id(), List.of())))
        .toList();
  }

  private FdhReviewResult.MaterialRow materialRow(
      FdhMaterialDefinition material,
      String applicationTypeId,
      List<FdhReviewDocument> documents
  ) {
    boolean applicable = material.applicableTo(applicationTypeId);
    boolean uploaded = !documents.isEmpty();
    boolean core = material.coreFor(applicationTypeId);
    String status = "muted";
    String issue = "";
    if (!applicable) {
      status = "muted";
    } else if (!uploaded && core) {
      status = "fail";
      issue = "未上传：" + "该申请类别必须提交 " + material.shortName() + "，但未识别到对应材料。";
    } else if (!uploaded) {
      status = "warn";
      issue = "官方清单要求或条件要求材料未上传；本 demo 不将材料 4-12 纳入最终通过判定。";
    } else if (documents.size() > 1 && core) {
      status = "review";
      issue = material.shortName() + " 被识别到多份上传文件，需要人工确认是否重复或错件。";
    } else {
      status = material.expectedPageCount()
          .map(expected -> pageStatus(expected, documents.get(0)))
          .orElse("pass");
      issue = materialIssue(material, documents.get(0), status);
    }

    if (!core && "pass".equals(status) && material.no() > 3) {
      issue = "材料已上传并被识别为官方清单展示项；不参与最终通过判定。";
    }

    return new FdhReviewResult.MaterialRow(
        material.id(),
        material.no(),
        material.name(),
        material.shortName(),
        material.templateId(),
        material.expectedPages(),
        applicable,
        uploaded,
        core,
        core,
        material.conditional(),
        status,
        materialStatusText(status, core, uploaded),
        materialScopeText(applicable, core),
        documents.stream().map(FdhReviewDocument::filename).toList(),
        issue
    );
  }

  private String pageStatus(int expectedPages, FdhReviewDocument document) {
    if (!missingOfficialPages(document, expectedPages).isEmpty()) {
      return "fail";
    }
    if (document.pageCount() < expectedPages) {
      return "fail";
    }
    if (document.pageCount() > expectedPages) {
      return "review";
    }
    DocumentTemplate template = document.template();
    if (template != null && template.confidence() > 0 && template.confidence() < 75) {
      return "review";
    }
    return "pass";
  }

  private String materialIssue(FdhMaterialDefinition material, FdhReviewDocument document, String status) {
    if ("fail".equals(status)) {
      int expected = material.expectedPageCount().orElse(0);
      return material.shortName() + " 已上传，但页数少于官方模板预期，预期 "
          + expected + " 页，识别到 " + document.pageCount() + " 页"
          + missingPageSuffix(document, expected) + "。";
    }
    if ("review".equals(status)) {
      return material.shortName() + " 页数或模板置信度异常，需人工确认是否缺页、错页或重复页。";
    }
    return "";
  }

  private String missingPageSuffix(FdhReviewDocument document, int expected) {
    if (expected <= 0) {
      return "";
    }
    List<Integer> missingPages = missingOfficialPages(document, expected);
    if (document == null || document.officialPageNumbers() == null || document.officialPageNumbers().isEmpty()) {
      int observed = document == null ? 0 : document.pageCount();
      return observed < expected ? "，缺失页码位置未识别" : "";
    }
    if (missingPages.isEmpty()) {
      int observed = document == null ? 0 : document.pageCount();
      if (observed >= expected) {
        return "";
      }
      for (int page = observed + 1; page <= expected; page += 1) {
        missingPages.add(page);
      }
    }
    if (missingPages.isEmpty()) {
      return "";
    }
    return "，缺" + missingPages.stream()
        .map(page -> "第 " + page + " 页")
        .collect(Collectors.joining("、"));
  }

  private List<Integer> missingOfficialPages(FdhReviewDocument document, int expected) {
    if (document == null || expected <= 0) {
      return new ArrayList<>();
    }
    List<Integer> officialPageNumbers = document.officialPageNumbers();
    if (officialPageNumbers == null || officialPageNumbers.isEmpty()) {
      return new ArrayList<>();
    }
    List<Integer> missing = new ArrayList<>();
    for (int page = 1; page <= expected; page += 1) {
      if (!officialPageNumbers.contains(page)) {
        missing.add(page);
      }
    }
    return missing;
  }

  private String materialStatusText(String status, boolean core, boolean uploaded) {
    return switch (status) {
      case "pass" -> core ? "核心材料齐全" : "已上传";
      case "fail" -> uploaded ? "缺页" : core ? "未上传核心材料" : "未上传";
      case "review" -> "需人工复核";
      case "warn" -> "未上传，不阻断";
      default -> "不适用";
    };
  }

  private String materialScopeText(boolean applicable, boolean core) {
    if (!applicable) {
      return "当前类别不适用";
    }
    return core ? "影响最终结论" : "官方清单项，本 demo 不阻断";
  }

  private List<FdhReviewResult.StandardField> buildFields(
      String applicationTypeId,
      List<FdhReviewDocument> documents
  ) {
    List<FdhReviewDocument> id988a = docsFor(documents, "id988a");
    List<FdhReviewDocument> id988b = docsFor(documents, "id988b");
    List<FdhReviewDocument> id407 = docsFor(documents, "id407");
    boolean id407Required = FdhMaterialCatalog.find("id407")
        .map(material -> material.coreFor(applicationTypeId))
        .orElse(false);

    List<FdhReviewResult.StandardField> fields = new ArrayList<>();
    fields.add(applicationTypeField(applicationTypeId, id988a));
    fields.add(standardField(
        "helper.name.full_en",
        "傭工字段",
        "傭工英文姓名",
        true,
        List.of(
            helperName(id988a, "ID 988A"),
            helperName(id407, "ID 407"),
            evidence(documents, "helperTravelCopy", "Name", List.of(group("passport", "name"), group("travel", "name")))
        ),
        "ID 988A、ID 407、旅行证件上的傭工英文姓名应一致；明显不一致判为 FAIL。"
    ));
    fields.add(standardField(
        "helper.travel_doc.number",
        "傭工字段",
        "傭工旅行证件号码",
        true,
        List.of(
            evidence(id988a, "ID 988A", "Travel document no.", List.of(
                group("travel", "document", "no"),
                group("travel", "doc", "no"),
                group("passport", "no")
            )),
            evidence(documents, "helperTravelCopy", "Passport No.", List.of(
                group("passport", "no"),
                group("travel", "document", "no")
            ))
        ),
        "ID 988A 与旅行证件副本号码应一致；轻微 OCR 差异进入 REVIEW。"
    ));
    fields.add(standardField(
        "helper.date_of_birth",
        "傭工字段",
        "傭工出生日期",
        true,
        List.of(
            evidence(id988a, "ID 988A", "Date of birth", List.of(group("date", "birth"), group("dob"))),
            evidence(documents, "helperTravelCopy", "Date of birth", List.of(group("date", "birth"), group("dob")))
        ),
        "日期标准化后应一致。"
    ));
    fields.add(standardField(
        "helper.nationality",
        "傭工字段",
        "傭工国籍",
        true,
        List.of(
            evidence(id988a, "ID 988A", "Nationality", List.of(group("nationality"))),
            evidence(documents, "helperTravelCopy", "Nationality", List.of(group("nationality")))
        ),
        "可做国家名 / 国籍词标准化，例如 Indonesia 与 Indonesian 视为一致。"
    ));
    fields.add(signatureField(
        "helper.signature.present",
        "傭工字段",
        "傭工签名",
        true,
        id988a,
        "Signature of applicant",
        "ID 988A 申请人签名栏必须存在签署痕迹；明确空白判为 FAIL，无法识别判为 REVIEW。"
    ));
    fields.add(employerNameField(id988b, id407));
    fields.add(multiSignatureField(
        "employer.signature.present",
        "雇主字段",
        "雇主签名",
        true,
        List.of(
            evidence(id988b, "ID 988B", "Signature of employer", List.of(group("signature"), group("sign"))),
            evidence(id407, "ID 407", "Signature of employer", List.of(group("signature"), group("sign")))
        ),
        "ID 988B 与 ID 407 的雇主签名栏应存在签署痕迹；无法识别时进入 REVIEW。"
    ));
    fields.add(contractNumberField(id407Required, id988a, id988b, id407));
    fields.add(wageField(
        "contract.monthly_wage_hkd",
        "每月工资",
        id407Required,
        id407,
        "Monthly wages",
        List.of(group("monthly", "wage"), group("wages"), group("salary")),
        minimumMonthlyWageHkd,
        "标准雇佣合约月薪不得低于当前政府公布的外籍家庭傭工最低允许工资。"
    ));
    fields.add(wageField(
        "contract.food.allowance_hkd",
        "膳食津贴",
        id407Required,
        id407,
        "Food allowance",
        List.of(group("food", "allowance"), group("allowance", "food")),
        minimumFoodAllowanceHkd,
        "如雇主不免费提供膳食，膳食津贴不得低于当前政府公布的最低金额。"
    ));
    fields.add(hkIdentityCardNoField(id988a, id988b, id407));
    fields.add(documentFooterField(documents));
    Set<String> consumedKeys = collectConsumedFieldKeys(fields);
    fields.addAll(extractedMaterialFields(documents, consumedKeys));
    // 按「页」排序（页内保持识别顺序），让字段顺序与材料物理顺序一致（从前页到后页）。
    fields.sort(Comparator.comparingInt(FdhReviewAssembler::pageIndexOfField));
    return fields;
  }

  private static int pageIndexOfField(FdhReviewResult.StandardField field) {
    if (field == null || field.sources() == null || field.sources().isEmpty()) {
      return Integer.MAX_VALUE;
    }
    Matcher matcher = Pattern.compile("page_(\\d+)").matcher(field.sources().get(0).section());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
  }

  private static Set<String> collectConsumedFieldKeys(List<FdhReviewResult.StandardField> fields) {
    Set<String> keys = new LinkedHashSet<>();
    for (FdhReviewResult.StandardField field : fields) {
      for (FdhReviewResult.FieldSource source : field.sources()) {
        keys.add(source.filename() + "|" + source.section() + "|" + normalizeTokens(source.value()));
      }
    }
    return keys;
  }

  private FdhReviewResult.StandardField applicationTypeField(
      String applicationTypeId,
      List<FdhReviewDocument> documents
  ) {
    List<ApplicationTypeExtraction> extractedTypes = applicationTypeExtractions(documents);
    List<FdhReviewResult.FieldSource> sources = extractedTypes.stream()
        .map(extracted -> {
          ExtractedValue value = extracted.value();
          String displayValue = extracted.label() + " - " + value.value();
          return new FdhReviewResult.FieldSource(
              "ID 988A",
              extracted.filename(),
              value.section(),
              "Application Type",
              displayValue,
              value.confidence(),
              displayValue,
              value.snapshotDataUrl(),
              extracted.materialId(),
              value.pageNo(),
              value.imageWidth(),
              value.imageHeight(),
              value.bbox(),
              locatorConfidence(value),
              value.verificationScore(), value.verificationStatus(), value.judgeObservedValue(), value.verificationReason(),
              value.labelBbox(), value.valueBbox(), value.evidenceBbox(), value.locationStatus()
          );
        })
        .toList();
    FieldAssessment assessment = assessRequiredSources(sources);
    String expected = applicationTypeLabel(applicationTypeId);
    long distinctRows = extractedTypes.stream()
        .map(ApplicationTypeExtraction::applicationTypeKey)
        .distinct()
        .count();
    if (distinctRows > 1) {
      boolean selectedRowsContainHomepageChoice = extractedTypes.stream()
          .anyMatch(extracted -> applicationTypeMatches(applicationTypeId, extracted));
      assessment = selectedRowsContainHomepageChoice
          ? new FieldAssessment(
              "review",
              "ID 988A application type has multiple selected business rows including the homepage choice; auditor review is required."
          )
          : new FieldAssessment(
              "fail",
              "Homepage application type is not among the selected ID 988A business rows."
          );
    } else if (!sources.isEmpty() && !applicationTypeMatches(applicationTypeId, extractedTypes.get(0))) {
      assessment = new FieldAssessment(
          "fail",
          "ID 988A 申请类别与用户在首页选择的四类情形不一致。"
      );
    }
    return new FdhReviewResult.StandardField(
        "case.application_type",
        "案件与文档",
        "申请类别",
        true,
        sources.isEmpty() ? "Unrecognized" : "Selected: " + expected + "; ID 988A: " + normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        true,
        sources,
        "必须与用户选择的四类情形一致；ID 988A 不得漏选或多选。"
    );
  }

  private FdhReviewResult.StandardField signatureField(
      String key,
      String category,
      String label,
      boolean required,
      List<FdhReviewDocument> documents,
      String fieldName,
      String rule
  ) {
    List<FdhReviewResult.FieldSource> sources = sources(List.of(evidence(
        documents,
        documents.isEmpty() ? "" : FdhMaterialCatalog.displayName(documents.get(0).materialId()),
        fieldName,
        List.of(group("signature"), group("sign"))
    )));
    FieldAssessment assessment = assessRequiredSources(sources);
    if (!sources.isEmpty() && sources.stream().anyMatch(source -> blankOrNegative(source.value()))) {
      assessment = new FieldAssessment("fail", label + " 字段明确为空或标记为未签署。");
    }
    return new FdhReviewResult.StandardField(
        key,
        category,
        label,
        required,
        sources.isEmpty() ? "未识别" : "已检测到",
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        rule
    );
  }

  private FdhReviewResult.StandardField multiSignatureField(
      String key,
      String category,
      String label,
      boolean required,
      List<Optional<FdhReviewResult.FieldSource>> optionalSources,
      String rule
  ) {
    List<FdhReviewResult.FieldSource> sources = sources(optionalSources);
    FieldAssessment assessment = assessRequiredSources(sources);
    if (!sources.isEmpty() && sources.stream().anyMatch(source -> blankOrNegative(source.value()))) {
      assessment = new FieldAssessment("fail", label + " 字段明确为空或标记为未签署。");
    }
    return new FdhReviewResult.StandardField(
        key,
        category,
        label,
        required,
        sources.isEmpty() ? "未识别" : "已检测到",
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        rule
    );
  }

  private FdhReviewResult.StandardField hkIdentityCardNoField(
      List<FdhReviewDocument> id988a,
      List<FdhReviewDocument> id988b,
      List<FdhReviewDocument> id407
  ) {
    List<List<String>> tokenGroups = List.of(
        group("hk", "identity", "card", "no"),
        group("hong", "kong", "identity", "card", "no"),
        group("hk", "id", "card", "no")
    );
    List<FdhReviewResult.FieldSource> sources = sources(List.of(
        evidence(id988a, "ID 988A", "HK identity card no.", tokenGroups),
        evidence(id988b, "ID 988B", "HK identity card no.", tokenGroups),
        evidence(id407, "ID 407", "HK identity card no.", tokenGroups)
    ));
    FieldAssessment assessment = assessHkIdentityCardNo(sources);
    return new FdhReviewResult.StandardField(
        "applicant.hk_identity_card_no",
        "傭工字段",
        "香港身份证号",
        true,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        !sources.isEmpty(),
        sources,
        "HK identity card no. Yes/No 行：选 Yes 须填写身份证号（仅输出号码，如 Y432189(6)）；选 No 输出\"没有\"；选 Yes 未填判 FAIL。"
    );
  }

  private FieldAssessment assessHkIdentityCardNo(List<FdhReviewResult.FieldSource> sources) {
    if (sources.isEmpty()) {
      return new FieldAssessment("review", "未能从材料识别香港身份证号字段，需要人工复核。");
    }
    if (sources.stream().anyMatch(source -> source.value().trim().equals("未填写"))) {
      return new FieldAssessment("fail", "选 Yes 但未填写香港身份证号。");
    }
    List<String> normalized = sources.stream()
        .map(source -> normalizeTokens(source.value()))
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
    if (normalized.size() > 1) {
      return new FieldAssessment("review", "跨材料香港身份证号不一致，需要人工复核。");
    }
    return new FieldAssessment("pass", "");
  }

  private FdhReviewResult.StandardField contractNumberField(
      boolean required,
      List<FdhReviewDocument> id988a,
      List<FdhReviewDocument> id988b,
      List<FdhReviewDocument> id407
  ) {
    Optional<FdhReviewResult.FieldSource> id988aSource = evidence(
        id988a,
        "ID 988A",
        "Employment contract no.",
        contractNumberGroups()
    );
    Optional<FdhReviewResult.FieldSource> id988bSource = evidence(
        id988b,
        "ID 988B",
        "Employment contract no.",
        contractNumberGroups()
    );
    Optional<FdhReviewResult.FieldSource> id407Source = evidence(
        id407,
        "ID 407",
        "Contract No.",
        contractNumberGroups()
    );
    List<FdhReviewResult.FieldSource> sources = sources(List.of(id988aSource, id988bSource, id407Source));
    FieldAssessment assessment = contractNumberAssessment(required, id988aSource, id988bSource, id407Source, sources);
    return new FdhReviewResult.StandardField(
        "contract.dh_contract_no",
        "合约字段",
        "标准雇佣合约编号",
        required,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        "ID 988A、ID 988B 与 ID 407 的标准雇佣合约编号必须完整填写并保持一致。"
    );
  }

  private FieldAssessment contractNumberAssessment(
      boolean required,
      Optional<FdhReviewResult.FieldSource> id988aSource,
      Optional<FdhReviewResult.FieldSource> id988bSource,
      Optional<FdhReviewResult.FieldSource> id407Source,
      List<FdhReviewResult.FieldSource> sources
  ) {
    if (!required) {
      return assessSources(false, sources);
    }
    List<String> missingDocuments = new ArrayList<>();
    if (id988aSource.isEmpty()) {
      missingDocuments.add("ID 988A");
    }
    if (id988bSource.isEmpty()) {
      missingDocuments.add("ID 988B");
    }
    if (id407Source.isEmpty()) {
      missingDocuments.add("ID 407");
    }
    if (!missingDocuments.isEmpty()) {
      return new FieldAssessment(
          "fail",
          String.join("、", missingDocuments) + " 未识别到标准雇佣合约编号，无法确认填写完整性。"
      );
    }
    return assessSources(true, sources);
  }

  private List<List<String>> contractNumberGroups() {
    return List.of(
        group("employment", "contract", "no"),
        group("employment", "contract", "number"),
        group("dh", "contract", "no"),
        group("d h", "contract", "no"),
        group("previous", "contract", "number"),
        group("contract", "no"),
        group("contract", "number"),
        group("合約", "號碼"),
        group("合约", "号码")
    );
  }

  private FdhReviewResult.StandardField contractField(
      String key,
      String label,
      boolean required,
      List<FdhReviewDocument> documents,
      String fieldName,
      List<List<String>> groups,
      String rule
  ) {
    List<FdhReviewResult.FieldSource> sources = sources(List.of(evidence(documents, "ID 407", fieldName, groups)));
    FieldAssessment assessment = required && documents.isEmpty()
        ? new FieldAssessment("fail", "ID 407 未上传，无法核验该合约字段。")
        : assessSources(required, sources);
    return new FdhReviewResult.StandardField(
        key,
        "合约字段",
        label,
        required,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        rule
    );
  }

  private FdhReviewResult.StandardField wageField(
      String key,
      String label,
      boolean required,
      List<FdhReviewDocument> documents,
      String fieldName,
      List<List<String>> groups,
      int minimum,
      String rule
  ) {
    List<FdhReviewResult.FieldSource> sources = sources(List.of(evidence(documents, "ID 407", fieldName, groups)));
    FieldAssessment assessment = required && documents.isEmpty()
        ? new FieldAssessment("fail", "ID 407 未上传，无法核验该合约字段。")
        : assessSources(required, sources);
    Optional<Integer> amount = sources.stream()
        .map(FdhReviewResult.FieldSource::value)
        .map(FdhReviewAssembler::money)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
    if (amount.isPresent() && amount.get() < minimum) {
      assessment = new FieldAssessment("fail", label + " HK$" + amount.get() + " 低于规则阈值 HK$" + minimum + "。");
    }
    return new FdhReviewResult.StandardField(
        key,
        "合约字段",
        label,
        required,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        rule + " 当前系统阈值可通过 fdh.minimum-* 配置更新。"
    );
  }

  private FdhReviewResult.StandardField documentFooterField(List<FdhReviewDocument> documents) {
    List<FdhReviewResult.FieldSource> sources = documents.stream()
        .filter(document -> document.template() != null)
        .map(document -> new FdhReviewResult.FieldSource(
            FdhMaterialCatalog.displayName(document.materialId()),
            document.filename(),
            "页尾",
            "Template footer",
            document.template().footerId().isBlank() ? document.template().templateId() : document.template().footerId(),
            document.template().confidence(),
            document.template().footerId().isBlank() ? document.template().templateId() : document.template().footerId(),
            firstPageSnapshot(document)
        ))
        .toList();
    boolean lowConfidenceCoreTemplate = documents.stream()
        .filter(document -> FdhMaterialCatalog.find(document.materialId())
            .map(material -> material.no() <= 3)
            .orElse(false))
        .map(FdhReviewDocument::template)
        .filter(Objects::nonNull)
        .anyMatch(template -> template.confidence() < 75);
    FieldAssessment assessment = sources.isEmpty()
        ? new FieldAssessment("review", "未能读取任何页尾 ID 或页面结构模板。")
        : lowConfidenceCoreTemplate
            ? new FieldAssessment("review", "存在模板识别低置信材料，需要人工确认模板版本。")
            : new FieldAssessment("pass", "");
    return new FdhReviewResult.StandardField(
        "document.footer_id",
        "案件与文档",
        "页尾模板标识",
        true,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        true,
        sources,
        "通过页尾 ID 与页面结构识别材料类型和模板版本。"
    );
  }

  private List<FdhReviewResult.StandardField> extractedMaterialFields(
      List<FdhReviewDocument> documents,
      Set<String> consumedKeys
  ) {
    Map<String, ExtractedFieldGroup> groups = new LinkedHashMap<>();
    for (FdhReviewDocument document : documents == null ? List.<FdhReviewDocument>of() : documents) {
      String documentName = FdhMaterialCatalog.displayName(document.materialId());
      for (ExtractedValue value : flatten(document)) {
        if (!shouldDisplayExtractedValue(value)) {
          continue;
        }
        if (isConsumedByStandardField(value, document, consumedKeys)) {
          continue;
        }
        String key = supplementalFieldKey(value);
        if (key.isBlank()) {
          continue;
        }
        ExtractedFieldGroup group = groups.computeIfAbsent(
            key,
            ignored -> new ExtractedFieldGroup(
                key,
                "材料识别字段",
                supplementalFieldLabel(value)
            )
        );
        group.add(new FdhReviewResult.FieldSource(
            documentName,
            document.filename(),
            value.section(),
            value.fieldName(),
            value.value(),
            value.confidence(),
            value.value(),
            value.snapshotDataUrl(),
            document.materialId(),
            value.pageNo(),
            value.imageWidth(),
            value.imageHeight(),
            value.bbox(),
            locatorConfidence(value),
            value.verificationScore(), value.verificationStatus(), value.judgeObservedValue(), value.verificationReason(),
            value.labelBbox(), value.valueBbox(), value.evidenceBbox(), value.locationStatus()
        ));
      }
    }
    return groups.values().stream()
        .map(ExtractedFieldGroup::toStandardField)
        .toList();
  }

  private boolean shouldDisplayExtractedValue(ExtractedValue value) {
    if (value == null || value.value().isBlank()) {
      return false;
    }
    String path = value.path();
    if (path == null || path.isBlank()) {
      return false;
    }
    for (String part : path.split("\\.")) {
      String normalized = normalizeTokens(part);
      if (part.startsWith("_")
          || normalized.equals("field confidence")
          || normalized.equals("source image data url")
          || normalized.equals("raw structured text")
          || normalized.equals("characters")
          || normalized.equals("bbox")) {
        return false;
      }
    }
    return !supplementalFieldKey(value).isBlank();
  }

  private boolean isConsumedByStandardField(
      ExtractedValue value,
      FdhReviewDocument document,
      Set<String> consumedKeys
  ) {
    // 直传型标准化字段（护照号/出生日期/国籍/合约号/工资/签名等）消费的同一识别记录：
    // (filename, section, value) 三元组与标准化字段 source 同源，可精确命中。
    String directKey = document.filename() + "|" + value.section() + "|" + normalizeTokens(value.value());
    if (consumedKeys.contains(directKey)) {
      return true;
    }
    // 申请类别：已被 case.application_type 标准化字段覆盖。
    if (applicationTypeCandidate(value).isPresent()) {
      return true;
    }
    // 姓名/雇主名：标准化字段为拼接值，三元组无法命中原始 surname/given/name，按语义跳过。
    // 但工作经验的 employer_N_name（过往雇主名）不是申请人姓名，需保留展示。
    if (matchesAnyGroup(value.searchText(), NAME_TOKEN_GROUPS)) {
      return !isId988aCurrentEmployerField(value, document)
          && !normalizeTokens(value.searchText()).matches(".*employer\\s+\\d+.*");
    }
    return false;
  }

  private boolean isId988aCurrentEmployerField(ExtractedValue value, FdhReviewDocument document) {
    if (document == null || !"id988a".equalsIgnoreCase(document.materialId())) {
      return false;
    }
    String normalized = normalizeTokens(value.searchText());
    return normalized.contains("current employer")
        && (normalized.contains("name") || normalized.contains("address"));
  }

  private String supplementalFieldKey(ExtractedValue value) {
    Optional<String> workExperienceKey = workExperienceFieldKeyFromPath(value.path());
    if (workExperienceKey.isPresent()) {
      return "extracted." + workExperienceKey.get();
    }
    String normalized = normalizeFieldName(supplementalFieldLabel(value));
    if (normalized.isBlank() || normalized.matches("\\d+")) {
      return "";
    }
    return "extracted." + normalized.replace(' ', '_');
  }

  private String supplementalFieldLabel(ExtractedValue value) {
    String fieldName = value.fieldName();
    if (fieldName == null || fieldName.isBlank()) {
      return fieldNameFromPath(value.path());
    }
    return fieldName.trim();
  }

  private String normalizeFieldName(String value) {
    String normalized = normalizeTokens(value);
    if (normalized.isBlank()) {
      return "";
    }
    normalized = (" " + normalized + " ")
        .replace(" no ", " number ")
        .replace(" nos ", " numbers ")
        .replace(" tel ", " telephone ")
        .replace(" dob ", " date birth ")
        .trim()
        .replaceAll("\\s+", " ");
    return normalized;
  }

  private FdhReviewResult.StandardField standardField(
      String key,
      String category,
      String label,
      boolean required,
      List<Optional<FdhReviewResult.FieldSource>> optionalSources,
      String rule
  ) {
    List<FdhReviewResult.FieldSource> sources = sources(optionalSources);
    FieldAssessment assessment = assessSources(required, sources);
    return new FdhReviewResult.StandardField(
        key,
        category,
        label,
        required,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        required,
        sources,
        rule
    );
  }

  private FieldAssessment assessRequiredSources(List<FdhReviewResult.FieldSource> sources) {
    return assessSources(true, sources);
  }

  private FieldAssessment assessSources(boolean required, List<FdhReviewResult.FieldSource> sources) {
    if (sources.isEmpty()) {
      return required
          ? new FieldAssessment("review", "未能从已上传材料识别该必填字段，需要人工复核。")
          : new FieldAssessment("pass", "");
    }
    if (sources.stream().anyMatch(FdhReviewAssembler::requiresFieldReview)) {
      return new FieldAssessment("review", "字段识别置信度较低，需要人工复核。");
    }
    List<String> normalized = sources.stream()
        .map(source -> comparableValue(source.fieldName(), source.value()))
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
    if (normalized.size() <= 1) {
      return new FieldAssessment("pass", "");
    }
    return hasOnlyTinyDifference(normalized)
        ? new FieldAssessment("review", "跨文件字段值存在轻微差异，需要人工复核。")
        : new FieldAssessment("fail", "跨文件字段值明显不一致。");
  }

  private static boolean requiresFieldReview(FdhReviewResult.FieldSource source) {
    if ("review".equals(source.verificationStatus())) {
      return true;
    }
    if ("pass".equals(source.verificationStatus())) {
      return false;
    }
    return source.confidence() < 70;
  }

  private static Integer combinedVerificationScore(Integer left, Integer right) {
    return left == null || right == null ? null : Math.min(left, right);
  }

  private static String combinedVerificationStatus(String left, String right) {
    if ("review".equals(left) || "review".equals(right)) return "review";
    if ("shadow".equals(left) || "shadow".equals(right)) return "shadow";
    if ("pass".equals(left) && "pass".equals(right)) return "pass";
    return "not_run";
  }

  private static String combinedReason(String left, String right) {
    return java.util.stream.Stream.of(left, right)
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .collect(java.util.stream.Collectors.joining("; "));
  }

  private FdhReviewResult.StandardField employerNameField(
      List<FdhReviewDocument> id988b,
      List<FdhReviewDocument> id407
  ) {
    Optional<FdhReviewResult.FieldSource> id407EmployerName = evidence(
        id407,
        "ID 407",
        "Name of employer",
        List.of(
            group("employer", "name"),
            group("name", "employer")
        )
    );
    boolean id407UsesChinese = id407EmployerName
        .map(source -> containsCjk(source.value()))
        .orElse(false);
    Optional<FdhReviewResult.FieldSource> id988bEmployerName = id407UsesChinese
        ? employerChineseName(id988b, "ID 988B")
        : employerEnglishName(id988b, "ID 988B");
    List<FdhReviewResult.FieldSource> sources = sources(List.of(id988bEmployerName, id407EmployerName));
    FieldAssessment assessment = sources.size() < 2
        ? new FieldAssessment("review", "Unable to identify comparable employer name in both ID 988B and ID 407.")
        : assessSources(true, sources);
    if (sources.isEmpty()) {
      assessment = assessRequiredSources(sources);
    }
    return new FdhReviewResult.StandardField(
        "employer.name.full_en",
        "雇主字段",
        "雇主姓名",
        true,
        sources.isEmpty() ? "未识别" : normalizeDisplayValue(sources),
        assessment.status(),
        assessment.issue(),
        true,
        sources,
        "ID 407 employer name is first classified as Chinese or English; then it is compared with the matching ID 988B employer name field."
    );
  }

  private Optional<FdhReviewResult.FieldSource> helperName(List<FdhReviewDocument> documents, String documentName) {
    Optional<FdhReviewResult.FieldSource> surname = evidence(
        documents,
        documentName,
        "Surname in English",
        List.of(group("surname"), group("family", "name"))
    );
    Optional<FdhReviewResult.FieldSource> given = evidence(
        documents,
        documentName,
        "Given names in English",
        List.of(group("given"), group("given", "name"))
    );
    if (surname.isPresent() && given.isPresent()) {
      FdhReviewResult.FieldSource surnameSource = surname.get();
      FdhReviewResult.FieldSource givenSource = given.get();
      FdhReviewResult.FieldSource locationSource = surnameSource.bbox().isEmpty() && !givenSource.bbox().isEmpty()
          ? givenSource
          : surnameSource;
      String fullName = givenSource.value() + " " + surnameSource.value();
      return Optional.of(new FdhReviewResult.FieldSource(
          surnameSource.documentName(),
          surnameSource.filename(),
          surnameSource.section(),
          "Given names / Surname",
          fullName,
          Math.min(surnameSource.confidence(), givenSource.confidence()),
          fullName,
          surnameSource.snapshotDataUrl().isBlank() ? givenSource.snapshotDataUrl() : surnameSource.snapshotDataUrl(),
          locationSource.materialId(),
          locationSource.pageNo(),
          locationSource.imageWidth(),
          locationSource.imageHeight(),
          locationSource.bbox(),
          locationSource.locatorConfidence(),
          combinedVerificationScore(surnameSource.verificationScore(), givenSource.verificationScore()),
          combinedVerificationStatus(surnameSource.verificationStatus(), givenSource.verificationStatus()),
          (givenSource.judgeObservedValue() + " " + surnameSource.judgeObservedValue()).trim(),
          combinedReason(surnameSource.verificationReason(), givenSource.verificationReason()),
          locationSource.labelBbox(), locationSource.valueBbox(), locationSource.evidenceBbox(), locationSource.locationStatus()
      ));
    }
    Optional<FdhReviewResult.FieldSource> full = evidence(
        documents,
        documentName,
        "Name of Helper",
        List.of(group("helper", "name"), group("name", "helper"), group("full", "name"), group("english", "name"))
    );
    return full.or(() -> surname).or(() -> given);
  }

  private Optional<FdhReviewResult.FieldSource> employerChineseName(
      List<FdhReviewDocument> documents,
      String documentName
  ) {
    return evidence(
        documents,
        documentName,
        "Name in Chinese",
        List.of(group("name", "chinese"), group("chinese", "name"))
    ).filter(source -> containsCjk(source.value()));
  }

  private Optional<FdhReviewResult.FieldSource> employerEnglishName(List<FdhReviewDocument> documents, String documentName) {
    Optional<FdhReviewResult.FieldSource> surname = evidence(
        documents,
        documentName,
        "Surname in English",
        List.of(group("surname"), group("family", "name"))
    );
    Optional<FdhReviewResult.FieldSource> given = evidence(
        documents,
        documentName,
        "Given names in English",
        List.of(group("given"), group("given", "name"))
    );
    if (surname.isPresent() && given.isPresent()) {
      FdhReviewResult.FieldSource first = surname.get();
      FdhReviewResult.FieldSource second = given.get();
      FdhReviewResult.FieldSource locationSource = first.bbox().isEmpty() && !second.bbox().isEmpty()
          ? second
          : first;
      String fullName = first.value() + " " + second.value();
      return Optional.of(new FdhReviewResult.FieldSource(
          first.documentName(),
          first.filename(),
          first.section(),
          "Surname / Given names",
          fullName,
          Math.min(first.confidence(), second.confidence()),
          fullName,
          first.snapshotDataUrl().isBlank() ? second.snapshotDataUrl() : first.snapshotDataUrl(),
          locationSource.materialId(),
          locationSource.pageNo(),
          locationSource.imageWidth(),
          locationSource.imageHeight(),
          locationSource.bbox(),
          locationSource.locatorConfidence(),
          combinedVerificationScore(first.verificationScore(), second.verificationScore()),
          combinedVerificationStatus(first.verificationStatus(), second.verificationStatus()),
          (first.judgeObservedValue() + " " + second.judgeObservedValue()).trim(),
          combinedReason(first.verificationReason(), second.verificationReason()),
          locationSource.labelBbox(), locationSource.valueBbox(), locationSource.evidenceBbox(), locationSource.locationStatus()
      ));
    }
    Optional<FdhReviewResult.FieldSource> full = evidence(
        documents,
        documentName,
        "Name of employer",
        List.of(
            group("employer", "name"),
            group("name", "employer")
        )
    );
    return full.filter(source -> !containsCjk(source.value()))
        .or(() -> surname.filter(source -> !containsCjk(source.value())))
        .or(() -> given.filter(source -> !containsCjk(source.value())));
  }

  private List<ApplicationTypeExtraction> applicationTypeExtractions(List<FdhReviewDocument> documents) {
    Map<String, ApplicationTypeExtraction> extractions = new LinkedHashMap<>();
    for (FdhReviewDocument document : documents == null ? List.<FdhReviewDocument>of() : documents) {
      for (ExtractedValue value : flatten(document)) {
        applicationTypeCandidate(value).ifPresent(candidate -> {
          String key = document.filename() + "|" + candidate.applicationTypeKey() + "|" + value.value();
          extractions.putIfAbsent(key, new ApplicationTypeExtraction(
              document.materialId(),
              document.filename(),
              candidate.applicationTypeKey(),
              candidate.label(),
              value
          ));
        });
      }
    }
    return List.copyOf(extractions.values());
  }

  private Optional<ApplicationTypeCandidate> applicationTypeCandidate(ExtractedValue value) {
    String path = value.path() == null ? "" : value.path();
    if (path.isBlank() || path.startsWith("_")) {
      return Optional.empty();
    }
    String normalizedPath = normalizeTokens(value.searchText());
    if (!normalizedPath.contains("application type")) {
      return Optional.empty();
    }
    if (path.contains(ID988A_ENTRY_TO_HK_APPLICATION_TYPE)) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_ENTRY_TO_HK_APPLICATION_TYPE, ID988A_ENTRY_TO_HK_LABEL));
    }
    if (path.contains(ID988A_CONTRACT_RENEWAL_APPLICATION_TYPE)) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_CONTRACT_RENEWAL_APPLICATION_TYPE, ID988A_CONTRACT_RENEWAL_LABEL));
    }
    if (path.contains(ID988A_REMAINING_CONTRACT_APPLICATION_TYPE)) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_REMAINING_CONTRACT_APPLICATION_TYPE, ID988A_REMAINING_CONTRACT_LABEL));
    }

    String normalizedValue = normalizeTokens(value.value());
    if (normalizedValue.equals("entry visa")) {
      return Optional.empty();
    }
    if (containsAny(normalizedValue, "take up employment", "domestic helper from abroad", "from abroad")) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_ENTRY_TO_HK_APPLICATION_TYPE, ID988A_ENTRY_TO_HK_LABEL));
    }
    if (containsAny(normalizedValue, "contract renewal", "same employer", "change of employer", "change employer")) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_CONTRACT_RENEWAL_APPLICATION_TYPE, ID988A_CONTRACT_RENEWAL_LABEL));
    }
    if (containsAny(normalizedValue, "remaining", "extended period", "extension of stay")) {
      return Optional.of(new ApplicationTypeCandidate(ID988A_REMAINING_CONTRACT_APPLICATION_TYPE, ID988A_REMAINING_CONTRACT_LABEL));
    }
    return Optional.empty();
  }

  private Optional<FdhReviewResult.FieldSource> evidence(
      List<FdhReviewDocument> documents,
      String expectedMaterialOrDocumentName,
      String fieldName,
      List<List<String>> tokenGroups
  ) {
    List<FdhReviewDocument> candidates = documents == null ? List.of() : documents;
    if (FdhMaterialCatalog.find(expectedMaterialOrDocumentName).isPresent()) {
      candidates = docsFor(candidates, expectedMaterialOrDocumentName);
    }
    for (FdhReviewDocument document : candidates) {
      Optional<ExtractedValue> value = findValue(document, tokenGroups);
      if (value.isPresent()) {
        ExtractedValue extracted = value.get();
        String documentName = FdhMaterialCatalog.displayName(document.materialId());
        if (!expectedMaterialOrDocumentName.isBlank() && !FdhMaterialCatalog.find(expectedMaterialOrDocumentName).isPresent()) {
          documentName = expectedMaterialOrDocumentName;
        }
        return Optional.of(new FdhReviewResult.FieldSource(
            documentName,
            document.filename(),
            extracted.section(),
            fieldName,
            extracted.value(),
            extracted.confidence(),
            extracted.value(),
            extracted.snapshotDataUrl(),
            document.materialId(),
            extracted.pageNo(),
            extracted.imageWidth(),
            extracted.imageHeight(),
            extracted.bbox(),
            locatorConfidence(extracted),
            extracted.verificationScore(),
            extracted.verificationStatus(),
            extracted.judgeObservedValue(),
            extracted.verificationReason(),
            extracted.labelBbox(),
            extracted.valueBbox(),
            extracted.evidenceBbox(),
            extracted.locationStatus()
        ));
      }
    }
    return Optional.empty();
  }

  private double locatorConfidence(ExtractedValue value) {
    return value.bbox().isEmpty() ? 0 : value.confidence();
  }

  private Optional<ExtractedValue> findValue(FdhReviewDocument document, List<List<String>> tokenGroups) {
    List<ExtractedValue> fields = flatten(document);
    return fields.stream()
        .filter(value -> matchesAnyGroup(value.searchText(), tokenGroups))
        .max(Comparator
            .comparingInt((ExtractedValue value) -> verificationStatusRank(value.verificationStatus()))
            .thenComparingInt(value -> value.verificationScore() == null ? 0 : value.verificationScore()));
  }

  private List<ExtractedValue> flatten(FdhReviewDocument document) {
    List<ExtractedValue> values = new ArrayList<>();
    OcrDemoResponse response = document.ocrResult();
    if (response != null) {
      // structuredFields 路（带定位信息、真实截图、原始 label）已覆盖的字段：记录归一化键，
      // 让 flattenJson 跳过这些字段的「无截图副本」，避免同一字段展示两条证据。
      Set<String> coveredByStructuredFields = new LinkedHashSet<>();
      for (OcrPage page : response.pages()) {
        for (StructuredFieldDetail detail : page.structuredFields()) {
          if (detail.displayValue() != null && !detail.displayValue().isBlank()) {
            values.add(new ExtractedValue(
                detail.path(),
                detail.label(),
                "page_" + detail.page() + "." + sectionFromPath(detail.path()),
                detail.displayValue(),
                detail.confidence(),
                detail.snapshotDataUrl(),
                page.page(),
                page.imageWidth(),
                page.imageHeight(),
                detail.bbox(),
                detail.verificationScore(),
                detail.verificationStatus(),
                detail.judgeObservedValue(),
                detail.verificationReason(),
                detail.labelBbox(),
                detail.valueBbox(),
                detail.evidenceBbox(),
                detail.locationStatus()
            ));
            coveredByStructuredFields.add(fieldDedupKey(detail.label(), detail.displayValue()));
            coveredByStructuredFields.add(fieldDedupKey(fieldNameFromPath(detail.path()), detail.displayValue()));
          }
        }
      }
      boolean enforceVerification = values.stream()
          .anyMatch(value -> Set.of("pass", "review").contains(value.verificationStatus()));
      flattenJson(response.structuredData(), "", values, coveredByStructuredFields, enforceVerification);
    }
    return values;
  }

  private String fieldDedupKey(String label, String value) {
    return normalizeFieldName(label) + "|" + normalizeTokens(value);
  }

  private void flattenJson(
      JsonNode node,
      String path,
      List<ExtractedValue> values,
      Set<String> coveredByStructuredFields,
      boolean enforceVerification
  ) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> flattenJson(
          entry.getValue(), append(path, entry.getKey()), values, coveredByStructuredFields, enforceVerification
      ));
      return;
    }
    if (node.isArray()) {
      int index = 0;
      for (JsonNode child : node) {
        flattenJson(child, append(path, String.valueOf(index)), values, coveredByStructuredFields, enforceVerification);
        index += 1;
      }
      return;
    }
    String value = node.asText("");
    if (value.isBlank()) {
      return;
    }
    // structuredFields 路已覆盖该字段（带截图、原始 label），跳过此处的无截图副本，保持页面简洁。
    if (coveredByStructuredFields.contains(fieldDedupKey(fieldNameFromPath(path), value))) {
      return;
    }
    values.add(new ExtractedValue(
        path, fieldNameFromPath(path), sectionFromPath(path), value, 78, "", 0, 0, 0, List.of(),
        null, enforceVerification ? "review" : "not_run", "",
        enforceVerification ? "judge_not_run" : "", List.of(), List.of(), List.of(), "not_run"
    ));
  }

  private String append(String path, String key) {
    return path == null || path.isBlank() ? key : path + "." + key;
  }

  private String sectionFromPath(String path) {
    if (path == null || path.isBlank()) {
      return "结构化字段";
    }
    String[] parts = path.split("\\.");
    if (parts.length <= 2) {
      return path;
    }
    return parts[0] + "." + parts[1];
  }

  private String fieldNameFromPath(String path) {
    if (path == null || path.isBlank()) {
      return "field";
    }
    String[] parts = path.split("\\.");
    String leaf = parts.length == 0 ? path : parts[parts.length - 1];
    return leaf
        .replaceAll("[_-]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private Optional<String> workExperienceFieldKeyFromPath(String path) {
    String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
    Matcher periodMatcher = WORK_EXPERIENCE_PERIOD_PATH_PATTERN.matcher(normalizedPath);
    if (periodMatcher.find()) {
      return Optional.of("employer_" + periodMatcher.group(1) + "_" + periodMatcher.group(2));
    }
    Matcher totalDurationMatcher = WORK_EXPERIENCE_TOTAL_DURATION_PATH_PATTERN.matcher(normalizedPath);
    if (totalDurationMatcher.find()) {
      return Optional.of("total_duration_" + totalDurationMatcher.group(1));
    }
    return Optional.empty();
  }

  private boolean matchesAnyGroup(String path, List<List<String>> groups) {
    String normalized = normalizeTokens(path);
    for (List<String> group : groups) {
      boolean allMatch = true;
      for (String token : group) {
        if (!normalized.contains(normalizeTokens(token))) {
          allMatch = false;
          break;
        }
      }
      if (allMatch) {
        return true;
      }
    }
    return false;
  }

  private static List<String> group(String... tokens) {
    return List.of(tokens);
  }

  private List<FdhReviewResult.FieldSource> sources(List<Optional<FdhReviewResult.FieldSource>> optionalSources) {
    return optionalSources.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private List<FdhReviewDocument> docsFor(List<FdhReviewDocument> documents, String materialId) {
    return documents.stream()
        .filter(document -> Objects.equals(document.materialId(), materialId))
        .toList();
  }

  private String decision(List<FdhReviewResult.MaterialRow> materials, List<FdhReviewResult.StandardField> fields) {
    boolean fail = materials.stream().anyMatch(material -> material.blocking() && "fail".equals(material.status()))
        || fields.stream().anyMatch(field -> field.blocking() && "fail".equals(field.status()));
    if (fail) {
      return "FAIL";
    }
    boolean review = materials.stream().anyMatch(material -> material.blocking() && "review".equals(material.status()))
        || fields.stream().anyMatch(field -> field.blocking() && "review".equals(field.status()));
    return review ? "REVIEW" : "PASS";
  }

  private String decisionText(
      String decision,
      List<FdhReviewResult.MaterialRow> materials,
      List<FdhReviewResult.StandardField> fields
  ) {
    if ("FAIL".equals(decision)) {
      Optional<String> materialIssue = materials.stream()
          .filter(material -> material.blocking() && "fail".equals(material.status()))
          .map(material -> material.shortName() + "：" + material.issue())
          .findFirst();
      Optional<String> fieldIssue = fields.stream()
          .filter(field -> field.blocking() && "fail".equals(field.status()))
          .map(field -> field.label() + "：" + field.issue())
          .findFirst();
      return materialIssue.or(() -> fieldIssue).orElse("存在阻断规则失败，当前申请不允许自动通过。");
    }
    if ("REVIEW".equals(decision)) {
      Optional<String> reviewIssue = fields.stream()
          .filter(field -> field.blocking() && "review".equals(field.status()))
          .map(field -> field.label() + "：" + field.issue())
          .findFirst();
      return reviewIssue.orElse("核心材料或字段存在待人工复核项，复核前不建议自动通过。");
    }
    return "核心材料 1-3 齐全，必填字段可识别，关键字段跨文件一致，允许通过。";
  }

  private FdhReviewResult.FieldStats stats(List<FdhReviewResult.StandardField> fields) {
    int pass = (int) fields.stream().filter(field -> "pass".equals(field.status())).count();
    int fail = (int) fields.stream().filter(field -> "fail".equals(field.status())).count();
    int review = (int) fields.stream().filter(field -> "review".equals(field.status())).count();
    int required = (int) fields.stream().filter(FdhReviewResult.StandardField::required).count();
    return new FdhReviewResult.FieldStats(fields.size(), pass, fail, review, required);
  }

  private static String normalizeApplicationType(String applicationTypeId) {
    Set<String> allowed = Set.of("entry_visa", "renewal", "remaining_period", "change_employer");
    return allowed.contains(applicationTypeId) ? applicationTypeId : "entry_visa";
  }

  private String applicationTypeLabel(String applicationTypeId) {
    return switch (applicationTypeId) {
      case "renewal" -> "于两年合约期届满后续约";
      case "remaining_period" -> "完成现有合约的余下期间";
      case "change_employer" -> "转换雇主";
      default -> "入境签证";
    };
  }

  private boolean applicationTypeMatches(String applicationTypeId, ApplicationTypeExtraction extraction) {
    String applicationTypeKey = extraction.applicationTypeKey();
    return switch (applicationTypeId) {
      case "renewal", "change_employer" -> ID988A_CONTRACT_RENEWAL_APPLICATION_TYPE.equals(applicationTypeKey);
      case "remaining_period" -> ID988A_REMAINING_CONTRACT_APPLICATION_TYPE.equals(applicationTypeKey);
      default -> ID988A_ENTRY_TO_HK_APPLICATION_TYPE.equals(applicationTypeKey);
    };
  }

  private boolean containsAny(String value, String... needles) {
    for (String needle : needles) {
      if (value.contains(normalizeTokens(needle))) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeDisplayValue(List<FdhReviewResult.FieldSource> sources) {
    Set<String> comparableValues = new LinkedHashSet<>();
    List<String> values = new ArrayList<>();
    for (FdhReviewResult.FieldSource source : sources) {
      if (source.value() != null && !source.value().isBlank()) {
        String value = source.value().trim();
        if (comparableValues.add(normalizeTokens(value))) {
          values.add(value);
        }
      }
    }
    return String.join(" / ", values);
  }

  private String comparableValue(String fieldName, String value) {
    if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("date")) {
      return normalizeDate(value);
    }
    if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("nationality")) {
      return normalizeNationality(value);
    }
    return normalizeTokens(value);
  }

  private static String normalizeTokens(String value) {
    return value == null ? "" : value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static boolean containsCjk(String value) {
    return value != null && CJK_PATTERN.matcher(value).find();
  }

  private String normalizeDate(String value) {
    String normalized = normalizeTokens(value);
    Matcher matcher = Pattern.compile("(\\d{1,2})\\s+(\\d{1,2}|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+(\\d{4})").matcher(normalized);
    if (matcher.find()) {
      return matcher.group(3) + "-" + monthNumber(matcher.group(2)) + "-" + twoDigits(matcher.group(1));
    }
    matcher = Pattern.compile("(\\d{4})\\s+(\\d{1,2})\\s+(\\d{1,2})").matcher(normalized);
    if (matcher.find()) {
      return matcher.group(1) + "-" + twoDigits(matcher.group(2)) + "-" + twoDigits(matcher.group(3));
    }
    return normalized;
  }

  private String monthNumber(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "jan" -> "01";
      case "feb" -> "02";
      case "mar" -> "03";
      case "apr" -> "04";
      case "may" -> "05";
      case "jun" -> "06";
      case "jul" -> "07";
      case "aug" -> "08";
      case "sep" -> "09";
      case "oct" -> "10";
      case "nov" -> "11";
      case "dec" -> "12";
      default -> twoDigits(value);
    };
  }

  private String twoDigits(String value) {
    try {
      int number = Integer.parseInt(value);
      return number < 10 ? "0" + number : String.valueOf(number);
    } catch (NumberFormatException exception) {
      return value;
    }
  }

  private String normalizeNationality(String value) {
    String normalized = normalizeTokens(value);
    if (normalized.equals("indonesia")) {
      return "indonesian";
    }
    return normalized;
  }

  private boolean hasOnlyTinyDifference(List<String> values) {
    for (int first = 0; first < values.size(); first += 1) {
      for (int second = first + 1; second < values.size(); second += 1) {
        if (levenshtein(values.get(first), values.get(second)) > 1) {
          return false;
        }
      }
    }
    return true;
  }

  private int levenshtein(String left, String right) {
    String a = left == null ? "" : left;
    String b = right == null ? "" : right;
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int index = 0; index <= b.length(); index += 1) {
      previous[index] = index;
    }
    for (int i = 1; i <= a.length(); i += 1) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j += 1) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(
            current[j - 1] + 1,
            previous[j] + 1
        ), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  private static Optional<Integer> money(String value) {
    Matcher matcher = MONEY_PATTERN.matcher(value == null ? "" : value.replace(",", ""));
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of((int) Math.round(Double.parseDouble(matcher.group(1))));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private boolean blankOrNegative(String value) {
    String normalized = normalizeTokens(value);
    return normalized.isBlank()
        || normalized.equals("no")
        || normalized.equals("none")
        || normalized.contains("not detected")
        || normalized.contains("blank")
        || normalized.contains("missing")
        || normalized.contains("未签")
        || normalized.contains("未检测");
  }

  private String firstPageSnapshot(FdhReviewDocument document) {
    OcrDemoResponse response = document.ocrResult();
    if (response == null || response.pages().isEmpty()) {
      return "";
    }
    return response.pages().get(0).sourceImageDataUrl();
  }

  private record ApplicationTypeExtraction(
      String materialId,
      String filename,
      String applicationTypeKey,
      String label,
      ExtractedValue value
  ) {}

  private record ApplicationTypeCandidate(
      String applicationTypeKey,
      String label
  ) {}

  private static final class ExtractedFieldGroup {
    private final String key;
    private final String category;
    private final String label;
    private final Map<String, FdhReviewResult.FieldSource> sources = new LinkedHashMap<>();

    private ExtractedFieldGroup(String key, String category, String label) {
      this.key = key;
      this.category = category;
      this.label = label == null || label.isBlank() ? key : label;
    }

    private void add(FdhReviewResult.FieldSource source) {
      String sourceKey = String.join(
          "|",
          source.documentName(),
          source.filename(),
          source.section(),
          source.fieldName(),
          normalizeTokens(source.value())
      );
      FdhReviewResult.FieldSource existing = sources.get(sourceKey);
      if (existing == null || compareVerification(source, existing) > 0) {
        sources.put(sourceKey, source);
      }
    }

    private FdhReviewResult.StandardField toStandardField() {
      List<FdhReviewResult.FieldSource> sourceRows = List.copyOf(sources.values());
      FieldAssessment assessment = assessment(sourceRows);
      return new FdhReviewResult.StandardField(
          key,
          category,
          label,
          false,
          normalizeDisplayValue(sourceRows),
          assessment.status(),
          assessment.issue(),
          false,
          sourceRows,
          "从上传材料结构化识别结果展示；同名字段按材料来源合并，建议归一值优先采用裁判核验通过且分数更高的来源。"
      );
    }

    private FieldAssessment assessment(List<FdhReviewResult.FieldSource> sourceRows) {
      if (sourceRows.stream().anyMatch(FdhReviewAssembler::requiresFieldReview)) {
        return new FieldAssessment("review", "字段裁判核验未通过，需要人工复核。");
      }
      long distinctValues = sourceRows.stream()
          .map(source -> normalizeTokens(source.value()))
          .filter(value -> !value.isBlank())
          .distinct()
          .count();
      if (distinctValues > 1) {
        return new FieldAssessment("review", "同名字段在不同材料中的识别值不一致，需要人工复核。");
      }
      return new FieldAssessment("pass", "");
    }
  }

  private static int compareVerification(
      FdhReviewResult.FieldSource left,
      FdhReviewResult.FieldSource right
  ) {
    int status = Integer.compare(
        verificationStatusRank(left.verificationStatus()),
        verificationStatusRank(right.verificationStatus())
    );
    if (status != 0) return status;
    return Integer.compare(
        left.verificationScore() == null ? 0 : left.verificationScore(),
        right.verificationScore() == null ? 0 : right.verificationScore()
    );
  }

  private static int verificationStatusRank(String status) {
    return switch (status == null ? "" : status) {
      case "pass" -> 3;
      case "shadow" -> 2;
      case "review" -> 1;
      default -> 0;
    };
  }

  private record ExtractedValue(
      String path,
      String fieldName,
      String section,
      String value,
      double confidence,
      String snapshotDataUrl,
      int pageNo,
      int imageWidth,
      int imageHeight,
      List<Integer> bbox,
      Integer verificationScore,
      String verificationStatus,
      String judgeObservedValue,
      String verificationReason,
      List<Integer> labelBbox,
      List<Integer> valueBbox,
      List<Integer> evidenceBbox,
      String locationStatus,
      String suggestedValue,
      String issue,
      String modelAgreement,
      String conflictType,
      List<ParallelRecognitionOutput> modelOutputs
  ) {
    private ExtractedValue(
        String path,
        String fieldName,
        String section,
        String value,
        double confidence,
        String snapshotDataUrl
    ) {
      this(path, fieldName, section, value, confidence, snapshotDataUrl, 0, 0, 0, List.of(),
          null, "not_run", "", "", List.of(), List.of(), List.of(), "not_run", "", "", "", "", List.of());
    }

    private ExtractedValue(
        String path,
        String fieldName,
        String section,
        String value,
        double confidence,
        String snapshotDataUrl,
        int pageNo,
        int imageWidth,
        int imageHeight,
        List<Integer> bbox
    ) {
      this(path, fieldName, section, value, confidence, snapshotDataUrl, pageNo, imageWidth, imageHeight, bbox,
          null, "not_run", "", "", List.of(), List.of(), List.of(), "not_run", "", "", "", "", List.of());
    }

    private ExtractedValue(
        String path,
        String fieldName,
        String section,
        String value,
        double confidence,
        String snapshotDataUrl,
        int pageNo,
        int imageWidth,
        int imageHeight,
        List<Integer> bbox,
        Integer verificationScore,
        String verificationStatus,
        String judgeObservedValue,
        String verificationReason,
        List<Integer> labelBbox,
        List<Integer> valueBbox,
        List<Integer> evidenceBbox,
        String locationStatus
    ) {
      this(path, fieldName, section, value, confidence, snapshotDataUrl, pageNo, imageWidth, imageHeight, bbox,
          verificationScore, verificationStatus, judgeObservedValue, verificationReason,
          labelBbox, valueBbox, evidenceBbox, locationStatus, "", "", "", "", List.of());
    }

    private String searchText() {
      return path + " " + fieldName;
    }
  }

  private record FieldAssessment(String status, String issue) {}
}
