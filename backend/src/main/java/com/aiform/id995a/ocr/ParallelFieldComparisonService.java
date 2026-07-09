package com.aiform.id995a.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ParallelFieldComparisonService {

  private static final Map<String, String> FIELD_ALIASES = Map.ofEntries(
      Map.entry("travel_doc_no", "travel_document_no"),
      Map.entry("travel_document_number", "travel_document_no"),
      Map.entry("passport_no", "travel_document_no"),
      Map.entry("passport_number", "travel_document_no"),
      Map.entry("given_names_en", "given_names_in_english"),
      Map.entry("given_name_in_english", "given_names_in_english"),
      Map.entry("given_name_en", "given_names_in_english"),
      Map.entry("surname_en", "surname_in_english"),
      Map.entry("english_surname", "surname_in_english"),
      Map.entry("chinese_name", "name_in_chinese"),
      Map.entry("date_of_birth_dd_mm_yyyy", "date_of_birth"),
      Map.entry("birth_date", "date_of_birth"),
      Map.entry("place_birth", "place_of_birth"),
      Map.entry("issue_place", "place_of_issue"),
      Map.entry("issue_date", "date_of_issue"),
      Map.entry("expiry_date", "date_of_expiry"),
      Map.entry("email", "email_address"),
      Map.entry("phone", "contact_telephone_no"),
      Map.entry("telephone_no", "contact_telephone_no"),
      Map.entry("contact_phone_no", "contact_telephone_no"),
      Map.entry("mainland_id_card_no", "mainland_identity_card_no"),
      Map.entry("mainland_id_no", "mainland_identity_card_no"),
      Map.entry("hk_id_card_no", "hk_identity_card_no"),
      Map.entry("hkid", "hk_identity_card_no"),
      Map.entry("marital_status", "marital_relationship_status"),
      Map.entry("relationship_status", "marital_relationship_status"),
      Map.entry("present_country_or_territory_of_domicile", "present_country_territory_of_domicile"),
      Map.entry("permanent_residence", "permanent_residence_acquired"),
      Map.entry("undergraduate_or_higher_qualification", "completed_undergraduate_or_higher_qualification"),
      Map.entry("current_stay_in_hong_kong", "currently_staying_in_hong_kong"),
      Map.entry("remain_until", "permitted_to_remain_until"),
      Map.entry("domicile_address_if_different_from_above", "domicile_address"),
      Map.entry("domicile_address_different_from_above", "domicile_address"),
      Map.entry("current_employer_address", "address_of_current_employer"),
      Map.entry("address_current_employer", "address_of_current_employer"),
      Map.entry("employer_address", "address_of_current_employer"),
      Map.entry("address_of_current_employer_if_applicable", "address_of_current_employer"),
      Map.entry("current_employer_name", "name_of_current_employer"),
      Map.entry("employer_name", "name_of_current_employer"),
      Map.entry("name_of_current_employer_if_applicable", "name_of_current_employer"),
      Map.entry("position", "position_occupation"),
      Map.entry("occupation", "position_occupation"),
      Map.entry("position_or_occupation", "position_occupation"),
      Map.entry("date_of_issue_of_graduation_certificate", "graduation_certificate_issue_date"),
      Map.entry("issue_date_of_graduation_certificate", "graduation_certificate_issue_date"),
      Map.entry("signature", "signature_of_applicant"),
      Map.entry("applicant_signature", "signature_of_applicant")
  );

  private final ObjectMapper objectMapper;

  public ParallelFieldComparisonService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode merge(JsonNode primary, JsonNode secondary) {
    ObjectNode merged = primary != null && primary.isObject()
        ? primary.deepCopy()
        : objectMapper.createObjectNode();
    if (secondary == null || secondary.isMissingNode() || secondary.isNull()) {
      return merged;
    }
    ObjectNode conflicts = merged.withObject("/_parallel_recognition");
    List<FieldValue> primaryValues = new ArrayList<>();
    List<FieldValue> secondaryValues = new ArrayList<>();
    collectPageValues(primary, primaryValues);
    collectPageValues(secondary, secondaryValues);
    Map<String, FieldValue> secondaryByExactKey = indexExact(secondaryValues);
    Map<String, FieldValue> secondaryByCanonicalKey = indexCanonical(secondaryValues);

    Set<String> primaryKeys = new HashSet<>();
    Set<String> primaryCanonicalKeys = new HashSet<>();
    Set<String> matchedSecondaryKeys = new HashSet<>();
    for (FieldValue primaryValue : primaryValues) {
      primaryKeys.add(fieldKey(primaryValue));
      primaryCanonicalKeys.add(canonicalFieldKey(primaryValue));
      FieldValue matchedSecondary = matchSecondaryField(
          primaryValue,
          secondaryByExactKey,
          secondaryByCanonicalKey
      ).orElse(null);
      String secondaryValue = matchedSecondary == null ? "" : matchedSecondary.value();
      if (matchedSecondary != null) {
        matchedSecondaryKeys.add(fieldKey(matchedSecondary));
      }
      if (secondaryValue.isBlank()) {
        addConflict(conflicts, primaryValue, primaryValue.value(), "", confidence(primary, primaryValue), 0);
        continue;
      }
      if (equivalent(primaryValue.value(), secondaryValue)) {
        continue;
      }
      addConflict(
          conflicts,
          primaryValue,
          primaryValue.value(),
          secondaryValue,
          confidence(primary, primaryValue),
          confidence(secondary, matchedSecondary)
      );
    }

    for (FieldValue secondaryValue : secondaryValues) {
      if (primaryKeys.contains(fieldKey(secondaryValue))
          || primaryCanonicalKeys.contains(canonicalFieldKey(secondaryValue))
          || matchedSecondaryKeys.contains(fieldKey(secondaryValue))) {
        continue;
      }
      putMergedValue(merged, secondaryValue);
      addConflict(conflicts, secondaryValue, "", secondaryValue.value(), 0, confidence(secondary, secondaryValue));
    }
    return merged;
  }

  private void addConflict(
      ObjectNode conflicts,
      FieldValue field,
      String primaryValue,
      String secondaryValue,
      double primaryConfidence,
      double secondaryConfidence
  ) {
    String suggestedValue = primaryValue.isBlank() ? secondaryValue : primaryValue;
    ObjectNode pageConflicts = conflicts.withObject("/" + field.pageKey());
    ObjectNode fieldConflict = pageConflicts.withObject("/" + pointerPath(field.path()));
    fieldConflict.put("model_agreement", "disagree");
    fieldConflict.put("conflict_type", "parallel_llm_disagreement");
    fieldConflict.put("suggested_value", suggestedValue);
    fieldConflict.put(
        "issue",
        "\u5e76\u884c\u8bc6\u522b\u7ed3\u679c\u4e0d\u4e00\u81f4\uff0c\u5efa\u8bae\u91c7\u7528\""
            + suggestedValue
            + "\"\uff0c\u8be5\u5b57\u6bb5\u9700\u4eba\u5de5\u590d\u6838\u786e\u8ba4\u3002"
    );
    ArrayNode outputs = fieldConflict.putArray("outputs");
    addOutput(outputs, "\u8bc6\u522b\u7ed3\u679c A", primaryValue, primaryConfidence);
    addOutput(outputs, "\u8bc6\u522b\u7ed3\u679c B", secondaryValue, secondaryConfidence);
  }

  private void putMergedValue(ObjectNode merged, FieldValue field) {
    if (field.path().isEmpty()) {
      return;
    }
    ObjectNode current = merged.withObject("/" + field.pageKey());
    for (int index = 0; index < field.path().size() - 1; index += 1) {
      String part = field.path().get(index);
      JsonNode child = current.get(part);
      if (child == null || !child.isObject()) {
        child = current.putObject(part);
      }
      current = (ObjectNode) child;
    }
    current.put(field.path().get(field.path().size() - 1), field.value());
  }

  private void collectPageValues(JsonNode root, List<FieldValue> values) {
    if (root == null || !root.isObject()) {
      return;
    }
    root.fields().forEachRemaining(entry -> {
      if (entry.getKey().matches("page_\\d+") && entry.getValue().isObject()) {
        collectValues(entry.getKey(), List.of(), entry.getValue(), values);
      }
    });
  }

  private void collectValues(String pageKey, List<String> path, JsonNode node, List<FieldValue> values) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    if (isLeafValue(node)) {
      String value = valueText(node);
      if (!value.isBlank()) {
        values.add(new FieldValue(pageKey, path, value));
      }
      return;
    }
    if (node.isObject()) {
      node.fields().forEachRemaining(entry -> {
        if (!entry.getKey().startsWith("_")) {
          collectValues(pageKey, append(path, entry.getKey()), entry.getValue(), values);
        }
      });
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index += 1) {
        collectValues(pageKey, append(path, String.valueOf(index)), node.get(index), values);
      }
    }
  }

  private boolean isLeafValue(JsonNode node) {
    return node.isTextual() || node.isBoolean() || node.isNumber();
  }

  private String valueText(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    return node.asText("").trim();
  }

  private double confidence(JsonNode root, FieldValue field) {
    JsonNode confidence = root == null
        ? MissingNode.getInstance()
        : root.at("/_confidence/" + field.pageKey() + "/" + pointerPath(field.path()));
    if (!confidence.isNumber()) {
      return 0;
    }
    double value = confidence.asDouble();
    return Math.max(0, Math.min(100, value <= 1 ? value * 100 : value));
  }

  private void addOutput(ArrayNode outputs, String label, String value, double confidence) {
    ObjectNode output = outputs.addObject();
    output.put("label", label);
    output.put("value", value);
    if (confidence > 0) {
      output.put("confidence", confidence);
    }
  }

  private List<String> append(List<String> path, String value) {
    List<String> copy = new ArrayList<>(path);
    copy.add(value);
    return copy;
  }

  private String pointerPath(List<String> path) {
    return String.join("/", path.stream()
        .map(part -> part.replace("~", "~0").replace("/", "~1"))
        .toList());
  }

  private Map<String, FieldValue> indexExact(List<FieldValue> values) {
    Map<String, FieldValue> index = new HashMap<>();
    for (FieldValue value : values) {
      index.putIfAbsent(fieldKey(value), value);
    }
    return index;
  }

  private Map<String, FieldValue> indexCanonical(List<FieldValue> values) {
    Map<String, FieldValue> index = new HashMap<>();
    for (FieldValue value : values) {
      index.putIfAbsent(canonicalFieldKey(value), value);
    }
    return index;
  }

  private Optional<FieldValue> matchSecondaryField(
      FieldValue primaryValue,
      Map<String, FieldValue> secondaryByExactKey,
      Map<String, FieldValue> secondaryByCanonicalKey
  ) {
    FieldValue exact = secondaryByExactKey.get(fieldKey(primaryValue));
    if (exact != null) {
      return Optional.of(exact);
    }
    return Optional.ofNullable(secondaryByCanonicalKey.get(canonicalFieldKey(primaryValue)));
  }

  private String canonicalFieldKey(FieldValue field) {
    return field.pageKey() + "\u0000" + canonicalPath(field.path());
  }

  private String canonicalPath(List<String> path) {
    Optional<String> indexedTablePath = canonicalIndexedTablePath(path);
    if (indexedTablePath.isPresent()) {
      return indexedTablePath.get();
    }
    return canonicalName(String.join("_", path));
  }

  private Optional<String> canonicalIndexedTablePath(List<String> path) {
    for (int index = 0; index < path.size() - 1; index += 1) {
      if (!isInteger(path.get(index))) {
        continue;
      }
      String context = indexedTableContext(path.subList(0, index));
      if (context.isBlank()) {
        continue;
      }
      int row = Integer.parseInt(path.get(index)) + 1;
      return Optional.of(context + "_" + row + "_" + canonicalTableLeaf(context, path.get(path.size() - 1)));
    }
    return Optional.empty();
  }

  private String indexedTableContext(List<String> contextPath) {
    String context = normalizedName(String.join("_", contextPath));
    if (context.contains("employment") || context.contains("employer") || context.contains("work")
        || context.contains("company")) {
      return "employment";
    }
    if (context.contains("education") || context.contains("academic") || context.contains("qualification")
        || context.contains("school") || context.contains("college") || context.contains("university")
        || context.contains("degree")) {
      return "education";
    }
    return "";
  }

  private String canonicalTableLeaf(String context, String value) {
    String leaf = canonicalName(value);
    if ("employment".equals(context)) {
      return switch (leaf) {
        case "name", "employer_name", "name_of_employer", "company", "company_name" -> "name";
        case "address", "employer_address", "company_address" -> "address";
        case "from", "period_from", "employment_period_from", "period_of_employment_from" -> "period_from";
        case "to", "period_to", "employment_period_to", "period_of_employment_to" -> "period_to";
        default -> leaf;
      };
    }
    if ("education".equals(context)) {
      return switch (leaf) {
        case "name", "school", "school_name", "college", "university", "institution",
            "education_institution", "name_of_education_institution",
            "name_of_education_institution_and_period_of_study",
            "name_of_school_college_university_other_educational_institution" -> "institution";
        case "subject", "major", "major_subject" -> "major_subject";
        case "degree", "qualification", "degree_qualification_obtained" -> "degree_qualification";
        case "subject_and_degree", "subject_and_degree_awarded" -> "subject_and_degree_awarded";
        case "from", "period_from", "period_of_study_from" -> "period_from";
        case "to", "period_to", "period_of_study_to" -> "period_to";
        default -> leaf;
      };
    }
    return leaf;
  }

  private String canonicalName(String value) {
    String normalized = normalizedName(value);
    Optional<String> employerField = canonicalFlatEmployerField(normalized);
    if (employerField.isPresent()) {
      return employerField.get();
    }
    return FIELD_ALIASES.getOrDefault(normalized, normalized);
  }

  private Optional<String> canonicalFlatEmployerField(String normalized) {
    String[] parts = normalized.split("_");
    if (parts.length < 3 || !"employer".equals(parts[0]) || !isInteger(parts[1])) {
      return Optional.empty();
    }
    String suffix = String.join("_", List.of(parts).subList(2, parts.length));
    String canonicalSuffix = switch (suffix) {
      case "from", "period_start", "employment_period_from" -> "period_from";
      case "to", "period_end", "employment_period_to" -> "period_to";
      default -> suffix;
    };
    return Optional.of("employment_" + parts[1] + "_" + canonicalSuffix);
  }

  private String normalizedName(String value) {
    return value == null ? "" : value
        .replaceAll("([a-z])([A-Z])", "$1_$2")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "");
  }

  private boolean isInteger(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    for (int index = 0; index < value.length(); index += 1) {
      if (!Character.isDigit(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private boolean equivalent(String left, String right) {
    if (normalized(left).equals(normalized(right))) {
      return true;
    }
    Optional<LocalDate> leftDate = dateValue(left);
    Optional<LocalDate> rightDate = dateValue(right);
    return leftDate.isPresent() && leftDate.equals(rightDate);
  }

  private String normalized(String value) {
    return value == null ? "" : value
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "")
        .trim();
  }

  private Optional<LocalDate> dateValue(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String normalizedValue = value.trim()
        .replaceAll("(?i)(\\d)(st|nd|rd|th)\\b", "$1")
        .replaceAll("[,，]", " ")
        .replaceAll("\\s+", " ");
    List<DateTimeFormatter> formatters = List.of(
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("uuuu-M-d"),
        englishDateFormatter("d MMM uuuu"),
        englishDateFormatter("d MMMM uuuu")
    );
    for (DateTimeFormatter formatter : formatters) {
      try {
        return Optional.of(LocalDate.parse(normalizedValue, formatter));
      } catch (DateTimeParseException ignored) {
      }
    }
    return Optional.empty();
  }

  private DateTimeFormatter englishDateFormatter(String pattern) {
    return new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .parseDefaulting(ChronoField.ERA, 1)
        .toFormatter(Locale.ENGLISH);
  }

  private String fieldKey(FieldValue field) {
    return field.pageKey() + "\u0000" + String.join("\u0000", field.path());
  }

  private record FieldValue(String pageKey, List<String> path, String value) {}
}
