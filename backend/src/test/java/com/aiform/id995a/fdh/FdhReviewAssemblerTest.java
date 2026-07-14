package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.StructuredFieldDetail;
import com.aiform.id995a.review.EngineStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdhReviewAssemblerTest {

  private static final String CONTRACT_NO = "FH-CON-IDN2026-0612";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FdhReviewAssembler assembler = new FdhReviewAssembler(
      5100,
      1236,
      Clock.fixed(Instant.parse("2026-05-28T10:15:00Z"), ZoneOffset.UTC)
  );

  @Test
  void passesWhenCoreMaterialsAndCrossFileFieldsAreComplete() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"), passport("C8923745", 90))
    );

    assertThat(result.decision()).isEqualTo("PASS");
    assertThat(result.materials()).filteredOn(FdhReviewResult.MaterialRow::core)
        .allMatch(row -> "pass".equals(row.status()));
    assertThat(result.materials()).filteredOn(row -> row.no() > 3)
        .allMatch(row -> !row.blocking());
    assertThat(result.fields()).filteredOn(FdhReviewResult.StandardField::blocking)
        .noneMatch(field -> "fail".equals(field.status()));
  }

  @Test
  void failsWhenCoreMaterialIsMissing() throws Exception {
    FdhReviewResult result = assembler.assemble("entry_visa", List.of(id988a(), id988b()));

    FdhReviewResult.MaterialRow id407 = result.materials().stream()
        .filter(row -> row.id().equals("id407"))
        .findFirst()
        .orElseThrow();
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(id407.status()).isEqualTo("fail");
    assertThat(id407.blocking()).isTrue();
  }

  @Test
  void distinguishesUploadedIncompleteMaterialFromNotUploadedMaterial() throws Exception {
    FdhReviewResult result = assembler.assemble("entry_visa", List.of(id988a(), id988bWithOfficialPages(List.of(1, 3, 4))));

    FdhReviewResult.MaterialRow id988b = material(result, "id988b");
    FdhReviewResult.MaterialRow id407 = material(result, "id407");

    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(id988b.uploaded()).isTrue();
    assertThat(id988b.status()).isEqualTo("fail");
    assertThat(id988b.statusText()).isEqualTo("缺页");
    assertThat(id988b.issue()).contains("已上传").contains("缺第 2 页").doesNotContain("缺第 4 页");
    assertThat(id407.uploaded()).isFalse();
    assertThat(id407.status()).isEqualTo("fail");
    assertThat(id407.statusText()).isEqualTo("未上传核心材料");
    assertThat(id407.issue()).contains("未上传").contains("ID 407");
  }

  @Test
  void materialRowsAfterThreeDoNotBlockFinalDecision() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.MaterialRow financialProof = result.materials().stream()
        .filter(row -> row.id().equals("financialProof"))
        .findFirst()
        .orElseThrow();
    assertThat(financialProof.status()).isEqualTo("warn");
    assertThat(financialProof.blocking()).isFalse();
    assertThat(result.decision()).isEqualTo("PASS");
  }

  @Test
  void obviousCrossDocumentMismatchFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA BINTI", "HK$5,100", "HK$1,236"), passport("C8923745", 90))
    );

    FdhReviewResult.StandardField helperName = field(result, "helper.name.full_en");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(helperName.status()).isEqualTo("fail");
    assertThat(helperName.issue()).contains("明显不一致");
  }

  @Test
  void id988aSplitHelperNameUsesGivenNamesBeforeSurnameForWesternDisplay() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "change_employer",
        List.of(
            id988aWithHelperName("CRUZ", "ANGELICA"),
            id988b(),
            id407("ANGELICA CRUZ", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField helperName = field(result, "helper.name.full_en");
    assertThat(helperName.status()).isEqualTo("pass");
    assertThat(helperName.normalizedValue()).isEqualTo("ANGELICA CRUZ");
    assertThat(helperName.sources()).extracting(FdhReviewResult.FieldSource::value)
        .containsExactly("ANGELICA CRUZ", "ANGELICA CRUZ");
  }

  @Test
  void id988bSplitEmployerEnglishNameParticipatesInCrossDocumentCheck() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988bSplitEmployerName("CHAN", "LAI PING"), id407WithEmployerName("CHAN TAI MAN"))
    );

    FdhReviewResult.StandardField employerName = field(result, "employer.name.full_en");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(employerName.status()).isEqualTo("fail");
    assertThat(employerName.sources()).extracting(FdhReviewResult.FieldSource::documentName)
        .contains("ID 988B", "ID 407");
    assertThat(employerName.sources()).extracting(FdhReviewResult.FieldSource::value)
        .contains("CHAN LAI PING", "CHAN TAI MAN");
  }

  @Test
  void id407ChineseEmployerNameComparesAgainstId988bChineseName() throws Exception {
    String employerChineseName = "\u9673\u9E97\u840D";
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988bEmployerNames(employerChineseName, "CHAN", "LAI PING"), id407WithEmployerName(employerChineseName))
    );

    FdhReviewResult.StandardField employerName = field(result, "employer.name.full_en");
    assertThat(result.decision()).isEqualTo("PASS");
    assertThat(employerName.status()).isEqualTo("pass");
    assertThat(employerName.normalizedValue()).isEqualTo(employerChineseName);
    assertThat(employerName.sources()).extracting(FdhReviewResult.FieldSource::value)
        .doesNotContain("CHAN LAI PING");
  }

  @Test
  void lowConfidenceOrTinyDifferenceRequiresReview() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"), passport("C892374S", 62))
    );

    FdhReviewResult.StandardField travelDoc = field(result, "helper.travel_doc.number");
    assertThat(result.decision()).isEqualTo("REVIEW");
    assertThat(travelDoc.status()).isEqualTo("review");
  }

  @Test
  void wageBelowConfiguredThresholdFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$4,900", "HK$1,236"))
    );

    FdhReviewResult.StandardField wage = field(result, "contract.monthly_wage_hkd");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(wage.status()).isEqualTo("fail");
    assertThat(wage.issue()).contains("低于规则阈值");
  }

  @Test
  void contractNumberComparesAcrossId988aId988bAndId407() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField contractNo = field(result, "contract.dh_contract_no");
    assertThat(contractNo.status()).isEqualTo("pass");
    assertThat(contractNo.normalizedValue()).isEqualTo(CONTRACT_NO);
    assertThat(contractNo.sources()).extracting(FdhReviewResult.FieldSource::documentName)
        .containsExactly("ID 988A", "ID 988B", "ID 407");
    assertThat(contractNo.sources()).extracting(FdhReviewResult.FieldSource::value)
        .containsExactly(CONTRACT_NO, CONTRACT_NO, CONTRACT_NO);
  }

  @Test
  void includesAllExtractedMaterialFieldsAndMergesMatchingRows() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988aWithExtraFields(), id988bWithExtraFields(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField mobilePhone = field(result, "extracted.mobile_phone_number");
    assertThat(mobilePhone.required()).isFalse();
    assertThat(mobilePhone.blocking()).isFalse();
    assertThat(mobilePhone.status()).isEqualTo("pass");
    assertThat(mobilePhone.normalizedValue()).isEqualTo("91234567");
    assertThat(mobilePhone.sources()).extracting(FdhReviewResult.FieldSource::documentName)
        .containsExactly("ID 988A", "ID 988B");
    assertThat(mobilePhone.sources()).extracting(FdhReviewResult.FieldSource::fieldName)
        .containsExactly("mobile phone no", "mobile phone number");

    FdhReviewResult.StandardField email = field(result, "extracted.email_address");
    assertThat(email.status()).isEqualTo("pass");
    assertThat(email.normalizedValue()).isEqualTo("helper@example.com");
    assertThat(email.sources()).singleElement()
        .extracting(FdhReviewResult.FieldSource::documentName)
        .isEqualTo("ID 988A");
    assertThat(email.issue()).doesNotContain("未识别");
  }

  @Test
  void extractedMaterialFieldsOmitValuesAlreadyCoveredByStandardFields() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988aWithExtraFields(), id988bWithExtraFields(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    List<String> extractedKeys = result.fields().stream()
        .map(FdhReviewResult.StandardField::key)
        .filter(key -> key.startsWith("extracted."))
        .toList();

    // 已被标准化字段消费的识别值（姓名/合约号/工资/膳食津贴/申请类别/签名等）
    // 不再以 extracted.* 重复出现。
    assertThat(extractedKeys).doesNotContain(
        "extracted.surname_en",
        "extracted.given_names_en",
        "extracted.employment_contract_number",
        "extracted.contract_number",
        "extracted.monthly_wages",
        "extracted.food_allowance",
        "extracted.application_type",
        "extracted.employer_name",
        "extracted.name_of_helper",
        "extracted.name_of_employer",
        "extracted.signature_of_applicant",
        "extracted.signature_of_employer"
    );

    // 未被标准化字段覆盖的字段仍保留，并跨材料归一（mobile 跨 988A/988B 合并）。
    assertThat(extractedKeys)
        .containsExactlyInAnyOrder("extracted.mobile_phone_number", "extracted.email_address");

    FdhReviewResult.StandardField mobilePhone = field(result, "extracted.mobile_phone_number");
    assertThat(mobilePhone.sources()).extracting(FdhReviewResult.FieldSource::documentName)
        .containsExactlyInAnyOrder("ID 988A", "ID 988B");
  }

  @Test
  void flattenJsonDuplicateIsDroppedWhenStructuredFieldAlreadyCoversIt() throws Exception {
    // structuredFields 路：带真实截图、原始 label（Place of birth，大写 P）。
    StructuredFieldDetail placeOfBirth = new StructuredFieldDetail(
        1, "place_of_birth", "Place of birth", text("Kampong Cham"), "Kampong Cham", 95,
        List.of(10, 20, 100, 40), "data:image/jpeg;base64,SNAPSHOT", "", 0, "not_run", List.of()
    );
    OcrPage page = new OcrPage(
        1, "data:image/png;base64,AAA=", 100, 100, "", List.of(), List.of(), List.of(), List.of(placeOfBirth)
    );
    // structuredData JSON 里同字段同值：flattenJson 路本会再造一条无截图、小写 label 的副本。
    OcrDemoResponse response = new OcrDemoResponse(
        "ID988A.pdf", "test", 1, List.of(page), List.of(),
        new EngineStatus("test", List.of()),
        objectMapper.readTree("{\"page_1\":{\"place_of_birth\":\"Kampong Cham\"}}"),
        ""
    );
    FdhReviewDocument document = new FdhReviewDocument(
        "ID988A.pdf", "application/pdf", 1,
        new DocumentTemplate("id988a_2024_06", "ID 988A (06/2024)", 5, 98, "test", "id988a_2024_06"),
        response,
        "id988a"
    );

    FdhReviewResult result = assembler.assemble("entry_visa", List.of(document));

    FdhReviewResult.StandardField placeOfBirthField = result.fields().stream()
        .filter(field -> field.key().equals("extracted.place_of_birth"))
        .findFirst()
        .orElseThrow();
    // flattenJson 副本被去掉：只剩 structuredFields 路一条证据——带截图、原始 label。
    assertThat(placeOfBirthField.sources()).hasSize(1);
    assertThat(placeOfBirthField.sources().get(0).snapshotDataUrl()).isEqualTo("data:image/jpeg;base64,SNAPSHOT");
    assertThat(placeOfBirthField.sources().get(0).fieldName()).isEqualTo("Place of birth");
  }

  @Test
  void fdhReviewResultIncludesPerMaterialDocumentFieldsWithLocatorMetadata() throws Exception {
    StructuredFieldDetail travelDocumentNo = new StructuredFieldDetail(
        1, "page_1.personal_particulars.travel_document_no", "Travel document no.", text("C8923745"), "C8923745", 94,
        List.of(12, 24, 120, 38), "data:image/jpeg;base64,TRAVEL_DOC", "", 0, "not_run", List.of()
    );
    OcrPage page = new OcrPage(
        1, "data:image/png;base64,AAA=", 1000, 1400, "", List.of(), List.of(), List.of(), List.of(travelDocumentNo)
    );
    OcrDemoResponse response = new OcrDemoResponse(
        "ID988A.pdf", "test", 1, List.of(page), List.of(),
        new EngineStatus("test", List.of()),
        objectMapper.readTree("{\"page_1\":{\"personal_particulars\":{\"travel_document_no\":\"C8923745\"}}}"),
        ""
    );
    FdhReviewDocument document = new FdhReviewDocument(
        "ID988A.pdf", "application/pdf", 1,
        new DocumentTemplate("id988a_2024_06", "ID 988A (06/2024)", 5, 98, "test", "id988a_2024_06"),
        response,
        "id988a"
    );

    FdhReviewResult result = assembler.assemble("entry_visa", List.of(document));

    FdhReviewResult.DocumentFieldGroup group = result.documentFieldGroups().stream()
        .filter(item -> item.materialId().equals("id988a"))
        .findFirst()
        .orElseThrow();
    FdhReviewResult.DocumentField field = group.pages().stream()
        .filter(item -> item.pageNo() == 1)
        .flatMap(item -> item.fields().stream())
        .filter(item -> item.label().equals("Travel document no."))
        .findFirst()
        .orElseThrow();

    assertThat(field.value()).isEqualTo("C8923745");
    assertThat(field.pageNo()).isEqualTo(1);
    assertThat(field.imageWidth()).isEqualTo(1000);
    assertThat(field.imageHeight()).isEqualTo(1400);
    assertThat(field.bbox()).containsExactly(12, 24, 120, 38);
    assertThat(field.locatorConfidence()).isEqualTo(94);
  }

  @Test
  void documentFieldGroupsOnlyShowPagesParticipatingInRecognition() throws Exception {
    FdhReviewDocument id988a = withRecognizedPages(id988a(), List.of(1, 2, 3, 4));
    FdhReviewDocument id988b = withRecognizedPages(id988bWithExtraFields(), List.of(1, 2, 3));
    FdhReviewResult result = assembler.assemble("entry_visa", List.of(id988a, id988b));

    FdhReviewResult.DocumentFieldGroup id988aGroup = result.documentFieldGroups().stream()
        .filter(item -> item.materialId().equals("id988a"))
        .findFirst()
        .orElseThrow();
    FdhReviewResult.DocumentFieldGroup id988bGroup = result.documentFieldGroups().stream()
        .filter(item -> item.materialId().equals("id988b"))
        .findFirst()
        .orElseThrow();

    assertThat(id988aGroup.pages()).extracting(FdhReviewResult.DocumentFieldPage::pageNo)
        .containsExactly(1, 2, 3, 4);
    assertThat(id988bGroup.pages()).extracting(FdhReviewResult.DocumentFieldPage::pageNo)
        .containsExactly(1, 2, 3);
  }

  @Test
  void hkIdentityCardNoYesWithNumberPasses() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988aWithHkIdentity("Y432189(6)"), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField hkId = field(result, "applicant.hk_identity_card_no");
    assertThat(hkId.status()).isEqualTo("pass");
    assertThat(hkId.normalizedValue()).contains("Y432189");
    assertThat(hkId.blocking()).isTrue();
  }

  @Test
  void hkIdentityCardNoNoOutputsMeiYouAndPasses() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988aWithHkIdentity("没有"), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField hkId = field(result, "applicant.hk_identity_card_no");
    assertThat(hkId.status()).isEqualTo("pass");
    assertThat(hkId.normalizedValue()).isEqualTo("没有");
  }

  @Test
  void hkIdentityCardNoYesWithoutNumberFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988aWithHkIdentity("未填写"), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField hkId = field(result, "applicant.hk_identity_card_no");
    assertThat(hkId.status()).isEqualTo("fail");
    assertThat(hkId.blocking()).isTrue();
    assertThat(result.decision()).isEqualTo("FAIL");
  }

  @Test
  void contractNumberMismatchAcrossCoreFormsFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988bWithContractNo("FH-CON-IDN2026-9999"), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField contractNo = field(result, "contract.dh_contract_no");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(contractNo.status()).isEqualTo("fail");
    assertThat(contractNo.sources()).extracting(FdhReviewResult.FieldSource::value)
        .contains(CONTRACT_NO, "FH-CON-IDN2026-9999");
  }

  @Test
  void missingCoreFormContractNumberFailsCompleteness() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988bWithoutContractNo(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.StandardField contractNo = field(result, "contract.dh_contract_no");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(contractNo.status()).isEqualTo("fail");
    assertThat(contractNo.issue()).contains("ID 988B");
  }

  @Test
  void failsEntryVisaWhenId988aEntryVisaCheckboxBelongsToContractRenewalRow() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(
            id988aApplicationType(
                "contract_renewal_with_the_same_employer_or_change_of_employer",
                "entry visa"
            ),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(applicationType.status()).isEqualTo("fail");
    assertThat(applicationType.normalizedValue())
        .contains("Contract renewal with the same employer or change of employer")
        .contains("entry visa");
  }

  @Test
  void acceptsRenewalWhenId988aUsesContractRenewalRow() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "renewal",
        List.of(
            id988aApplicationType(
                "contract_renewal_with_the_same_employer_or_change_of_employer",
                "entry visa"
            ),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(applicationType.status()).isEqualTo("pass");
    assertThat(applicationType.normalizedValue())
        .contains("Contract renewal with the same employer or change of employer")
        .contains("entry visa");
  }

  @Test
  void reviewsWhenId988aSelectsMultipleApplicationTypeRows() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(
            id988aApplicationTypes(List.of(
                List.of(
                    "entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad",
                    "entry visa"
                ),
                List.of(
                    "contract_renewal_with_the_same_employer_or_change_of_employer",
                    "entry visa"
                )
            )),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(result.decision()).isEqualTo("REVIEW");
    assertThat(applicationType.status()).isEqualTo("review");
    assertThat(applicationType.issue()).contains("multiple").contains("review");
    assertThat(applicationType.normalizedValue())
        .contains("Entry to Hong Kong to take up employment as a domestic helper from abroad")
        .contains("Contract renewal with the same employer or change of employer");
  }

  @Test
  void marksDocumentFieldRowsAsReviewWhenId988aSelectsMultipleApplicationTypeRows() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(
            id988aApplicationTypes(List.of(
                List.of(
                    "entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad",
                    "entry visa"
                ),
                List.of(
                    "contract_renewal_with_the_same_employer_or_change_of_employer",
                    "entry visa"
                )
            )),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    List<FdhReviewResult.DocumentField> applicationTypeFields = result.documentFieldGroups().stream()
        .filter(group -> group.materialId().equals("id988a"))
        .flatMap(group -> group.pages().stream())
        .filter(page -> page.pageNo() == 1)
        .flatMap(page -> page.fields().stream())
        .filter(field -> field.label().contains("Entry to Hong Kong")
            || field.label().contains("Contract renewal with the same employer"))
        .toList();

    assertThat(applicationTypeFields)
        .extracting(FdhReviewResult.DocumentField::label)
        .containsExactlyInAnyOrder(
            "Entry to Hong Kong to take up employment as a domestic helper from abroad",
            "Contract renewal with the same employer or change of employer"
        );
    assertThat(applicationTypeFields)
        .extracting(FdhReviewResult.DocumentField::status)
        .containsOnly("review");
  }

  @Test
  void failsWhenHomepageApplicationTypeIsNotAmongMultipleId988aSelections() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "remaining_period",
        List.of(
            id988aApplicationTypes(List.of(
                List.of(
                    "entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad",
                    "entry visa"
                ),
                List.of(
                    "contract_renewal_with_the_same_employer_or_change_of_employer",
                    "entry visa"
                )
            )),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(applicationType.status()).isEqualTo("fail");
    assertThat(applicationType.issue()).contains("not among").contains("ID 988A");
  }

  private FdhReviewResult.StandardField field(FdhReviewResult result, String key) {
    return result.fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewResult.MaterialRow material(FdhReviewResult result, String id) {
    return result.materials().stream()
        .filter(row -> row.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewDocument id988a() throws Exception {
    return id988aWithHelperName("NURHALIZA", "SITI");
  }

  private FdhReviewDocument id988aWithHkIdentity(String hkIdentity) throws Exception {
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": "Entry visa - Domestic helper from abroad",
                "part_2_personal_particulars": {
                  "surname_en": "NURHALIZA",
                  "given_names_en": "SITI",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected",
                  "hk_identity_card_no": "%s"
                }
              },
              "page_4": {
                "employment_contract_no": "%s"
              }
            }
            """.formatted(hkIdentity, CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988aWithExtraFields() throws Exception {
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": "Entry visa - Domestic helper from abroad",
                "part_2_personal_particulars": {
                  "surname_en": "NURHALIZA",
                  "given_names_en": "SITI",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected",
                  "mobile_phone_no": "91234567",
                  "email_address": "helper@example.com"
                }
              },
              "page_4": {
                "employment_contract_no": "%s"
              }
            }
            """.formatted(CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988aWithHelperName(String surname, String givenNames) throws Exception {
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": "Entry visa - Domestic helper from abroad",
                "part_2_personal_particulars": {
                  "surname_en": "%s",
                  "given_names_en": "%s",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected"
                }
              },
              "page_4": {
                "employment_contract_no": "%s"
              }
            }
            """.formatted(surname, givenNames, CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988aApplicationType(String applicationTypeKey, String value) throws Exception {
    return id988aApplicationTypes(List.of(List.of(applicationTypeKey, value)));
  }

  private FdhReviewDocument id988aApplicationTypes(List<List<String>> applicationTypes) throws Exception {
    String entries = applicationTypes.stream()
        .map(entry -> "                  \"%s\": \"%s\"".formatted(entry.get(0), entry.get(1)))
        .reduce((left, right) -> left + ",\n" + right)
        .orElse("");
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": {
%s
                },
                "part_2_personal_particulars": {
                  "surname_en": "NURHALIZA",
                  "given_names_en": "SITI",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected"
                }
              },
              "page_4": {
                "employment_contract_no": "%s"
              }
            }
            """.formatted(entries, CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988b() throws Exception {
    return id988bWithContractNo(CONTRACT_NO);
  }

  private FdhReviewDocument id988bWithExtraFields() throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "employer_particulars": {
                  "employer_name": "CHAN TAI MAN",
                  "mobile_phone_number": "91234567"
                }
              },
              "page_3": {
                "employment_contract_no": "%s"
              },
              "page_4": {
                "declaration": {
                  "signature_of_employer": "signature detected"
                }
              }
            }
            """.formatted(CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988bWithPageCount(int pages) throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        pages,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "employer_particulars": {
                  "employer_name": "CHAN TAI MAN"
                }
              },
              "page_3": {
                "employment_contract_no": "%s"
              }
            }
            """.formatted(CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988bWithOfficialPages(List<Integer> officialPageNumbers) throws Exception {
    FdhReviewDocument document = id988bWithPageCount(officialPageNumbers.size());
    return new FdhReviewDocument(
        document.filename(),
        document.contentType(),
        document.pageCount(),
        document.template(),
        document.ocrResult(),
        document.materialId(),
        officialPageNumbers
    );
  }

  private FdhReviewDocument id988bWithContractNo(String contractNo) throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "employer_particulars": {
                  "employer_name": "CHAN TAI MAN"
                }
              },
              "page_3": {
                "employment_contract_no": "%s"
              },
              "page_4": {
                "declaration": {
                  "signature_of_employer": "signature detected"
                }
              }
            }
            """.formatted(contractNo)
    );
  }

  private FdhReviewDocument id988bWithoutContractNo() throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "employer_particulars": {
                  "employer_name": "CHAN TAI MAN"
                }
              },
              "page_4": {
                "declaration": {
                  "signature_of_employer": "signature detected"
                }
              }
            }
            """
    );
  }

  private FdhReviewDocument id988bSplitEmployerName(String surname, String givenNames) throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "surname_in_english": "%s",
                "given_names_in_english": "%s"
              },
              "page_3": {
                "employment_contract_no": "%s",
                "signature_of_employer": "signature detected"
              }
            }
            """.formatted(surname, givenNames, CONTRACT_NO)
    );
  }

  private FdhReviewDocument id988bEmployerNames(String chineseName, String surname, String givenNames) throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "name_in_chinese": "%s",
                "surname_in_english": "%s",
                "given_names_in_english": "%s"
              },
              "page_3": {
                "employment_contract_no": "%s",
                "signature_of_employer": "signature detected"
              }
            }
            """.formatted(chineseName, surname, givenNames, CONTRACT_NO)
    );
  }

  private FdhReviewDocument id407(String helperName, String wages, String foodAllowance) throws Exception {
    return id407(helperName, "CHAN TAI MAN", wages, foodAllowance);
  }

  private FdhReviewDocument id407WithEmployerName(String employerName) throws Exception {
    return id407("SITI NURHALIZA", employerName, "HK$5,100", "HK$1,236");
  }

  private FdhReviewDocument id407(String helperName, String employerName, String wages, String foodAllowance) throws Exception {
    return document(
        "ID407.pdf",
        "id407",
        4,
        "id407_2016_11",
        "ID 407 (11/2016)",
        """
            {
              "page_1": {
                "contract_no": "%s",
                "name_of_helper": "%s",
                "name_of_employer": "%s"
              },
              "page_2": {
                "monthly_wages": "%s",
                "food_allowance": "%s"
              },
              "page_4": {
                "signature_of_employer": "signature detected"
              }
            }
            """.formatted(CONTRACT_NO, helperName, employerName, wages, foodAllowance)
    );
  }

  private FdhReviewDocument passport(String passportNo, double confidence) throws Exception {
    StructuredFieldDetail passportNumber = new StructuredFieldDetail(
        1,
        "page_1.passport_no",
        "Passport No.",
        text(passportNo),
        passportNo,
        confidence,
        List.of(),
        "",
        passportNo,
        confidence,
        "available",
        List.of()
    );
    OcrPage page = new OcrPage(
        1,
        "data:image/png;base64,AAA=",
        100,
        100,
        "",
        List.of(),
        List.of(),
        List.of(),
        List.of(passportNumber)
    );
    OcrDemoResponse response = new OcrDemoResponse(
        "passport.jpg",
        "test",
        1,
        List.of(page),
        List.of(),
        new EngineStatus("test", List.of()),
        objectMapper.readTree("""
            {
              "page_1": {
                "passport_name": "SITI NURHALIZA",
                "date_of_birth": "27 NOV 1992",
                "nationality": "Indonesia"
              }
            }
            """),
        ""
    );
    return new FdhReviewDocument(
        "passport.jpg",
        "image/jpeg",
        1,
        new DocumentTemplate("unknown_1p_passport", "", 1, 70, "filename", "passport"),
        response,
        "helperTravelCopy"
    );
  }

  @Test
  void fieldJudgeStatusOverridesRecognitionConfidenceForExtractedFields() throws Exception {
    StructuredFieldDetail highConfidenceMismatch = verifiedDetail(
        "custom_high", "HIGH", 99, 0, "review", "mismatch"
    );
    StructuredFieldDetail lowConfidenceExact = verifiedDetail(
        "custom_low", "LOW", 40, 100, "pass", "exact"
    );
    OcrPage page = new OcrPage(
        1, "data:image/png;base64,AAA=", 200, 200, "", List.of(), List.of(), List.of(),
        List.of(highConfidenceMismatch, lowConfidenceExact)
    );
    OcrDemoResponse response = new OcrDemoResponse(
        "support.pdf", "test", 1, List.of(page), List.of(), new EngineStatus("test", List.of()),
        objectMapper.readTree("{\"page_1\":{\"custom_high\":\"HIGH\",\"custom_low\":\"LOW\"}}"), ""
    );
    FdhReviewDocument document = new FdhReviewDocument(
        "support.pdf", "application/pdf", 1,
        new DocumentTemplate("support", "", 1, 98, "test", "support"), response, "supporting"
    );

    FdhReviewResult result = assembler.assemble("entry_visa", List.of(document));

    FdhReviewResult.StandardField mismatch = field(result, "extracted.custom_high");
    FdhReviewResult.StandardField exact = field(result, "extracted.custom_low");
    assertThat(mismatch.status()).isEqualTo("review");
    assertThat(mismatch.sources().get(0).verificationScore()).isZero();
    assertThat(exact.status()).isEqualTo("pass");
    assertThat(exact.sources().get(0).verificationScore()).isEqualTo(100);
  }

  private StructuredFieldDetail verifiedDetail(
      String path,
      String value,
      double recognitionConfidence,
      int verificationScore,
      String verificationStatus,
      String matchType
  ) {
    List<Integer> bbox = List.of(10, 10, 180, 40);
    return new StructuredFieldDetail(
        1, path, path, text(value), value, recognitionConfidence, bbox,
        "data:image/jpeg;base64,SNAPSHOT", "", 0, "not_run", List.of(),
        recognitionConfidence, bbox, List.of(10, 10, 70, 40), List.of(80, 10, 180, 40), bbox,
        "located", "label_and_value", 96, "", "available", value, matchType,
        verificationScore, verificationStatus, matchType, "field_judge"
    );
  }

  private FdhReviewDocument document(
      String filename,
      String materialId,
      int pages,
      String templateId,
      String footerId,
      String json
  ) throws Exception {
    return new FdhReviewDocument(
        filename,
        "application/pdf",
        pages,
        new DocumentTemplate(templateId, footerId, pages, 98, "test", templateId),
        response(filename, pages, json),
        materialId
    );
  }

  private FdhReviewDocument withRecognizedPages(FdhReviewDocument document, List<Integer> pageNumbers) {
    OcrDemoResponse original = document.ocrResult();
    List<OcrPage> pages = pageNumbers.stream()
        .map(pageNo -> new OcrPage(
            pageNo,
            "data:image/png;base64,AAA=",
            100,
            100,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of()
        ))
        .toList();
    OcrDemoResponse response = new OcrDemoResponse(
        original.filename(),
        original.model(),
        original.pageCount(),
        pages,
        original.extractedFields(),
        original.engineStatus(),
        original.structuredData(),
        original.rawStructuredText()
    );
    return new FdhReviewDocument(
        document.filename(),
        document.contentType(),
        document.pageCount(),
        document.template(),
        response,
        document.materialId(),
        document.officialPageNumbers()
    );
  }

  private OcrDemoResponse response(String filename, int pages, String json) throws Exception {
    return new OcrDemoResponse(
        filename,
        "test",
        pages,
        List.of(new OcrPage(
            1,
            "data:image/png;base64,AAA=",
            100,
            100,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of()
        )),
        List.of(),
        new EngineStatus("test", List.of()),
        objectMapper.readTree(json),
        ""
    );
  }

  private JsonNode text(String value) {
    return objectMapper.getNodeFactory().textNode(value);
  }
}
