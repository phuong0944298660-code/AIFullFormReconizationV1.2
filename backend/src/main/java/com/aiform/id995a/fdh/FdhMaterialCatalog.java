package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class FdhMaterialCatalog {

  private static final List<FdhMaterialDefinition> MATERIALS = List.of(
      new FdhMaterialDefinition(
          "id988a",
          1,
          "从外国受聘来港家庭傭工签证 / 延长逗留期限申请表",
          "ID 988A",
          "ID 988A (06/2024)",
          5,
          List.of("entry_visa", "renewal", "remaining_period", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "id988b",
          2,
          "从外国聘用家庭傭工申请表",
          "ID 988B",
          "ID 988B (06/2024)",
          4,
          List.of("entry_visa", "renewal", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "id407",
          3,
          "新标准雇佣合约正本一份",
          "ID 407",
          "ID 407 (11/2016)",
          4,
          List.of("entry_visa", "renewal", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "helperTravelOriginal",
          4,
          "傭工的旅行证件正本",
          "旅行证件正本",
          "非固定模板",
          "按证件",
          List.of("renewal", "remaining_period", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "helperTravelCopy",
          5,
          "傭工的旅行证件副本",
          "旅行证件副本",
          "非固定模板",
          "资料页",
          List.of("entry_visa", "renewal", "remaining_period", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "helperHkid",
          6,
          "傭工的香港身份证副本（如适用）",
          "傭工 HKID",
          "HKID",
          "1-2 页",
          List.of("entry_visa", "change_employer"),
          true
      ),
      new FdhMaterialDefinition(
          "employerId",
          7,
          "雇主的香港永久性居民身份证 / 香港身份证 / 护照副本",
          "雇主身份证明",
          "HKID / Passport",
          "1-2 页",
          List.of("entry_visa", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "financialProof",
          8,
          "雇主的经济状况证明（副本）",
          "经济状况证明",
          "税单 / 银行 / 薪金",
          "按证明",
          List.of("entry_visa", "renewal", "remaining_period", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "addressProof",
          9,
          "雇主的住址证明（副本）",
          "住址证明",
          "差饷 / 水电等",
          "按证明",
          List.of("entry_visa", "renewal", "remaining_period", "change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "testimonial",
          10,
          "傭工的荐书",
          "荐书",
          "信件",
          "按证明",
          List.of("entry_visa"),
          false
      ),
      new FdhMaterialDefinition(
          "releaseLetter",
          11,
          "现时雇主发出的离职信（列明合约的届满 / 终止日期）",
          "离职信",
          "信件",
          "按证明",
          List.of("change_employer"),
          false
      ),
      new FdhMaterialDefinition(
          "continuousLetter",
          12,
          "雇主继续聘用的确认信",
          "继续聘用确认信",
          "信件",
          "按证明",
          List.of("remaining_period"),
          false
      )
  );

  private FdhMaterialCatalog() {}

  static List<FdhMaterialDefinition> materials() {
    return MATERIALS;
  }

  static Optional<FdhMaterialDefinition> find(String materialId) {
    return MATERIALS.stream().filter(material -> material.id().equals(materialId)).findFirst();
  }

  static String displayName(String materialId) {
    return find(materialId).map(FdhMaterialDefinition::shortName).orElse("未识别材料");
  }

  static String classify(DocumentTemplate template, String filename) {
    String templateId = template == null ? "" : template.templateId().toLowerCase(Locale.ROOT);
    if (templateId.startsWith("id988a_")) {
      return "id988a";
    }
    if (templateId.startsWith("id988b_")) {
      return "id988b";
    }
    if (templateId.startsWith("id407_")) {
      return "id407";
    }
    return classifyByFilename(filename);
  }

  private static String classifyByFilename(String filename) {
    String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (containsAny(lower, "988a", "id 988a")) {
      return "id988a";
    }
    if (containsAny(lower, "988b", "id 988b")) {
      return "id988b";
    }
    if (containsAny(lower, "407", "contract")) {
      return "id407";
    }
    if (containsAny(lower, "passport", "travel", "biodata", "bio-data")) {
      return "helperTravelCopy";
    }
    if (containsAny(lower, "helper_hkid", "helper-hkid")) {
      return "helperHkid";
    }
    if (containsAny(lower, "employer_hkid", "employer-id", "employerid")) {
      return "employerId";
    }
    if (containsAny(lower, "bank", "salary", "income", "financial", "tax")) {
      return "financialProof";
    }
    if (containsAny(lower, "address", "utility", "water", "electricity", "rent")) {
      return "addressProof";
    }
    if (containsAny(lower, "testimonial", "reference")) {
      return "testimonial";
    }
    if (containsAny(lower, "release", "termination", "resignation")) {
      return "releaseLetter";
    }
    if (containsAny(lower, "continuous", "continue", "renewal_letter")) {
      return "continuousLetter";
    }
    return "unknown";
  }

  private static boolean containsAny(String value, String... needles) {
    for (String needle : needles) {
      if (value.contains(needle)) {
        return true;
      }
    }
    return false;
  }
}

record FdhMaterialDefinition(
    String id,
    int no,
    String name,
    String shortName,
    String templateId,
    Object expectedPages,
    List<String> applicability,
    boolean conditional
) {

  boolean applicableTo(String applicationTypeId) {
    return applicability.contains(applicationTypeId);
  }

  boolean coreFor(String applicationTypeId) {
    return no <= 3 && applicableTo(applicationTypeId);
  }

  Optional<Integer> expectedPageCount() {
    return expectedPages instanceof Integer value ? Optional.of(value) : Optional.empty();
  }
}
