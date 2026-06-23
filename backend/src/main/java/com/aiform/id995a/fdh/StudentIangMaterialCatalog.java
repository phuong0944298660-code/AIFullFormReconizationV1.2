package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class StudentIangMaterialCatalog {

  static final String APPLICATION_TYPE_ID = "iang_recent_in_hk";

  private static final List<StudentIangMaterialDefinition> MATERIALS = List.of(
      new StudentIangMaterialDefinition(
          "id990a",
          1,
          "IANG / 专业人士来港就业申请表",
          "ID 990A",
          "ID 990A",
          "前 5 页",
          true,
          true,
          false,
          "Demo审批；按页尾页码识别 ID 990A 前 5 页"
      ),
      new StudentIangMaterialDefinition(
          "educationProof",
          2,
          "学历 / 毕业资格证明",
          "毕业证明",
          "Certifying letter / Transcript / Graduation certificate",
          "按证明",
          true,
          true,
          false,
          "Demo审批；判断学历层级及 6 个月应届窗口"
      ),
      new StudentIangMaterialDefinition(
          "identityDocs",
          3,
          "港澳通行证 / 护照 / 香港身份证",
          "身份及旅行证件",
          "EEP / Passport / HKID",
          "资料页及 HKID",
          true,
          true,
          false,
          "Demo审批；与申请表、毕业证明、付款记录交叉核验"
      ),
      new StudentIangMaterialDefinition(
          "paymentStatus",
          4,
          "付款状态 / 申请费付款截图",
          "付款状态",
          "Online payment page",
          "1 页",
          true,
          true,
          false,
          "Demo审批；付款未完成时保留 REVIEW"
      ),
      new StudentIangMaterialDefinition(
          "photo",
          5,
          "申请人近照",
          "申请人近照",
          "Photo",
          "1 张",
          true,
          false,
          false,
          "当前 demo 不纳入最终阻断"
      ),
      new StudentIangMaterialDefinition(
          "currentStayEvidence",
          6,
          "最近入境记录 / 小白条 / e-Visa",
          "当前逗留记录",
          "Landing slip / e-Visa",
          "按记录",
          true,
          false,
          false,
          "确认当前在港及逗留期限；当前 demo 不纳入最终阻断"
      ),
      new StudentIangMaterialDefinition(
          "mainlandConsent",
          7,
          "《内地的中国居民赴港工作同意书》",
          "赴港工作同意书",
          "ID(C) 991 附件二",
          "1 页",
          true,
          false,
          true,
          "内地居民适用；当前 demo 展示为条件应交，不纳入最终阻断"
      ),
      new StudentIangMaterialDefinition(
          "visaIssueFee",
          8,
          "获批后的签证签发费付款证明 / e-Visa 下载件",
          "获批后结果材料",
          "Visa issue fee / e-Visa",
          "获批后产生",
          true,
          false,
          false,
          "获批后用于结果归档；不属于当前首轮材料审核阻断范围"
      )
  );

  private StudentIangMaterialCatalog() {}

  static boolean supports(String applicationTypeId) {
    return APPLICATION_TYPE_ID.equals(applicationTypeId);
  }

  static List<StudentIangMaterialDefinition> materials() {
    return MATERIALS;
  }

  static Optional<StudentIangMaterialDefinition> find(String materialId) {
    return MATERIALS.stream().filter(material -> material.id().equals(materialId)).findFirst();
  }

  static String displayName(String materialId) {
    return find(materialId).map(StudentIangMaterialDefinition::shortName).orElse("未识别材料");
  }

  static String classify(DocumentTemplate template, String filename) {
    String templateId = template == null ? "" : template.templateId().toLowerCase(Locale.ROOT);
    String footerId = template == null ? "" : template.footerId().toLowerCase(Locale.ROOT);
    if (containsAny(templateId, "990a") || containsAny(footerId, "990a")) {
      return "id990a";
    }
    return classifyByFilename(filename);
  }

  private static String classifyByFilename(String filename) {
    String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (containsAny(lower, "990a", "id 990a", "id990a", "iang")) {
      return "id990a";
    }
    if (containsAny(lower, "毕业", "學歷", "学历", "graduation", "graduate", "transcript", "degree", "completion", "education", "certifying")) {
      return "educationProof";
    }
    if (containsAny(lower, "港澳", "通行证", "通行證", "护照", "護照", "hkid", "hk id", "passport", "permit", "identity")) {
      return "identityDocs";
    }
    if (containsAny(lower, "付款", "缴费", "繳費", "payment", "receipt", "application fee")) {
      return "paymentStatus";
    }
    if (containsAny(lower, "同意书", "同意書", "consent", "id(c) 991", "idc 991")) {
      return "mainlandConsent";
    }
    if (containsAny(lower, "小白条", "入境记录", "landing", "current stay", "e-visa", "evisa")) {
      return "currentStayEvidence";
    }
    if (containsAny(lower, "photo", "照片", "近照")) {
      return "photo";
    }
    if (containsAny(lower, "issue fee", "获批", "獲批", "visa issue")) {
      return "visaIssueFee";
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

record StudentIangMaterialDefinition(
    String id,
    int no,
    String name,
    String shortName,
    String templateId,
    Object expectedPages,
    boolean applicable,
    boolean core,
    boolean conditional,
    String scopeText
) {}
