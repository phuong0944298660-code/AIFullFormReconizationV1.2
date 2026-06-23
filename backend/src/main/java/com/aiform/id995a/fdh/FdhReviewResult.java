package com.aiform.id995a.fdh;

import java.util.List;

public record FdhReviewResult(
    String applicationTypeId,
    List<UploadedFile> uploadedFiles,
    List<MaterialRow> materials,
    List<StandardField> fields,
    List<DocumentFieldGroup> documentFieldGroups,
    String decision,
    String decisionText,
    FieldStats stats,
    String generatedAt
) {

  public FdhReviewResult {
    applicationTypeId = applicationTypeId == null || applicationTypeId.isBlank()
        ? "entry_visa"
        : applicationTypeId;
    uploadedFiles = uploadedFiles == null ? List.of() : List.copyOf(uploadedFiles);
    materials = materials == null ? List.of() : List.copyOf(materials);
    fields = fields == null ? List.of() : List.copyOf(fields);
    documentFieldGroups = documentFieldGroups == null ? List.of() : List.copyOf(documentFieldGroups);
    decision = decision == null || decision.isBlank() ? "REVIEW" : decision;
    decisionText = decisionText == null ? "" : decisionText;
    generatedAt = generatedAt == null ? "" : generatedAt;
  }

  public FdhReviewResult(
      String applicationTypeId,
      List<UploadedFile> uploadedFiles,
      List<MaterialRow> materials,
      List<StandardField> fields,
      String decision,
      String decisionText,
      FieldStats stats,
      String generatedAt
  ) {
    this(applicationTypeId, uploadedFiles, materials, fields, List.of(), decision, decisionText, stats, generatedAt);
  }

  public record UploadedFile(
      String materialId,
      String documentName,
      String filename,
      int pages,
      String footerId,
      String templateId,
      String matchSource
  ) {

    public UploadedFile {
      materialId = materialId == null || materialId.isBlank() ? "unknown" : materialId;
      documentName = documentName == null || documentName.isBlank() ? materialId : documentName;
      filename = filename == null || filename.isBlank() ? "uploaded-document" : filename;
      pages = Math.max(0, pages);
      footerId = footerId == null ? "" : footerId;
      templateId = templateId == null ? "" : templateId;
      matchSource = matchSource == null ? "" : matchSource;
    }
  }

  public record MaterialRow(
      String id,
      int no,
      String name,
      String shortName,
      String templateId,
      Object expectedPages,
      boolean applicable,
      boolean uploaded,
      boolean core,
      boolean blocking,
      boolean conditional,
      String status,
      String statusText,
      String scopeText,
      List<String> uploadedFilenames,
      String issue
  ) {

    public MaterialRow {
      id = id == null ? "" : id;
      name = name == null ? "" : name;
      shortName = shortName == null || shortName.isBlank() ? id : shortName;
      templateId = templateId == null ? "" : templateId;
      status = status == null || status.isBlank() ? "muted" : status;
      statusText = statusText == null ? "" : statusText;
      scopeText = scopeText == null ? "" : scopeText;
      uploadedFilenames = uploadedFilenames == null ? List.of() : List.copyOf(uploadedFilenames);
      issue = issue == null ? "" : issue;
    }
  }

  public record StandardField(
      String key,
      String category,
      String label,
      boolean required,
      String normalizedValue,
      String status,
      String issue,
      boolean blocking,
      List<FieldSource> sources,
      String rule
  ) {

    public StandardField {
      key = key == null ? "" : key;
      category = category == null ? "" : category;
      label = label == null || label.isBlank() ? key : label;
      normalizedValue = normalizedValue == null ? "" : normalizedValue;
      status = status == null || status.isBlank() ? "review" : status;
      issue = issue == null ? "" : issue;
      sources = sources == null ? List.of() : List.copyOf(sources);
      rule = rule == null ? "" : rule;
    }
  }

  public record FieldSource(
      String documentName,
      String filename,
      String section,
      String fieldName,
      String value,
      double confidence,
      String snapshotText,
      String snapshotDataUrl
  ) {

    public FieldSource {
      documentName = documentName == null ? "" : documentName;
      filename = filename == null ? "" : filename;
      section = section == null ? "" : section;
      fieldName = fieldName == null ? "" : fieldName;
      value = value == null ? "" : value;
      confidence = Math.max(0, Math.min(100, confidence));
      snapshotText = snapshotText == null || snapshotText.isBlank() ? value : snapshotText;
      snapshotDataUrl = snapshotDataUrl == null ? "" : snapshotDataUrl;
    }
  }

  public record FieldStats(
      int total,
      int pass,
      int fail,
      int review,
      int required
  ) {}

  public record DocumentFieldGroup(
      String materialId,
      String materialName,
      String templateId,
      String note,
      List<DocumentFieldPage> pages
  ) {

    public DocumentFieldGroup {
      materialId = materialId == null ? "" : materialId;
      materialName = materialName == null || materialName.isBlank() ? materialId : materialName;
      templateId = templateId == null ? "" : templateId;
      note = note == null ? "" : note;
      pages = pages == null ? List.of() : List.copyOf(pages);
    }
  }

  public record DocumentFieldPage(
      int pageNo,
      String title,
      List<DocumentField> fields
  ) {

    public DocumentFieldPage {
      pageNo = Math.max(1, pageNo);
      title = title == null || title.isBlank() ? "识别字段" : title;
      fields = fields == null ? List.of() : List.copyOf(fields);
    }
  }

  public record DocumentField(
      String label,
      String value,
      String status
  ) {

    public DocumentField {
      label = label == null || label.isBlank() ? "未命名字段" : label;
      value = value == null ? "" : value;
      status = status == null || status.isBlank() ? "pass" : status;
    }
  }
}
