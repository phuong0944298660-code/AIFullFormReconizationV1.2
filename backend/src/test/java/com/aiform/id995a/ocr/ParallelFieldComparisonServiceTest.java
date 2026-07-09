package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ParallelFieldComparisonServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ParallelFieldComparisonService service = new ParallelFieldComparisonService(objectMapper);

  @Test
  void marksFieldForReviewWhenParallelRecognitionValuesDisagree() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {
          "page_2": {
            "travel_document_no": "CA3273201"
          },
          "_confidence": {
            "page_2": {
              "travel_document_no": 91
            }
          },
          "_field_evidence": {
            "page_2": {
              "travel_document_no": {
                "label": "旅行证件号码",
                "value_bbox": [10, 20, 130, 50]
              }
            }
          }
        }
        """);
    JsonNode secondary = objectMapper.readTree("""
        {
          "page_2": {
            "travel_document_no": "CA3273207"
          },
          "_confidence": {
            "page_2": {
              "travel_document_no": 86
            }
          }
        }
        """);

    JsonNode merged = service.merge(primary, secondary);

    JsonNode conflict = merged.at("/_parallel_recognition/page_2/travel_document_no");
    assertThat(conflict.path("model_agreement").asText()).isEqualTo("disagree");
    assertThat(conflict.path("conflict_type").asText()).isEqualTo("parallel_llm_disagreement");
    assertThat(conflict.path("suggested_value").asText()).isEqualTo("CA3273201");
    assertThat(conflict.path("issue").asText()).contains("并行识别结果不一致");
    assertThat(conflict.path("outputs").get(0).path("label").asText()).isEqualTo("识别结果 A");
    assertThat(conflict.path("outputs").get(0).path("value").asText()).isEqualTo("CA3273201");
    assertThat(conflict.path("outputs").get(0).path("confidence").asDouble()).isEqualTo(91);
    assertThat(conflict.path("outputs").get(1).path("label").asText()).isEqualTo("识别结果 B");
    assertThat(conflict.path("outputs").get(1).path("value").asText()).isEqualTo("CA3273207");
    assertThat(conflict.path("outputs").get(1).path("confidence").asDouble()).isEqualTo(86);
    assertThat(conflict.toString()).doesNotContain("Qwen");
    assertThat(merged.at("/page_2/travel_document_no").asText()).isEqualTo("CA3273201");
  }

  @Test
  void doesNotMarkAgreementWhenNormalizedValuesMatch() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_1":{"hkid":"F539325(2)"}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_1":{"hkid":"F 539325 (2)"}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.path("_parallel_recognition").path("page_1").path("hkid").isMissingNode()).isTrue();
  }

  @Test
  void treatsEquivalentDateFormatsAsAgreement() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_1":{"declaration_date":"20/04/2026"}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_1":{"declaration_date":"20 April 2026"}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.path("_parallel_recognition").path("page_1").path("declaration_date").isMissingNode()).isTrue();
  }

  @Test
  void marksSimilarTravelDocumentNumbersWithDifferentLettersAsDisagreement() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_1":{"travel_document_no":"PA9923471"}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_1":{"travel_document_no":"PU9923471"}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    JsonNode conflict = merged.at("/_parallel_recognition/page_1/travel_document_no");
    assertThat(conflict.path("model_agreement").asText()).isEqualTo("disagree");
    assertThat(conflict.path("outputs").get(0).path("value").asText()).isEqualTo("PA9923471");
    assertThat(conflict.path("outputs").get(1).path("value").asText()).isEqualTo("PU9923471");
  }

  @Test
  void matchesSecondaryAliasFieldBeforeDeclaringItMissing() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {
          "page_1": {
            "travel_document_no": "PA9923471"
          },
          "_confidence": {
            "page_1": {
              "travel_document_no": 92
            }
          }
        }
        """);
    JsonNode secondary = objectMapper.readTree("""
        {
          "page_1": {
            "travel_doc_no": "PU9923471"
          },
          "_confidence": {
            "page_1": {
              "travel_doc_no": 88
            }
          }
        }
        """);

    JsonNode merged = service.merge(primary, secondary);

    JsonNode conflict = merged.at("/_parallel_recognition/page_1/travel_document_no");
    assertThat(conflict.path("model_agreement").asText()).isEqualTo("disagree");
    assertThat(conflict.path("outputs").get(0).path("value").asText()).isEqualTo("PA9923471");
    assertThat(conflict.path("outputs").get(1).path("value").asText()).isEqualTo("PU9923471");
    assertThat(conflict.path("outputs").get(1).path("confidence").asDouble()).isEqualTo(88);
    assertThat(merged.at("/_parallel_recognition/page_1/travel_doc_no").isMissingNode()).isTrue();
    assertThat(merged.at("/page_1/travel_doc_no").isMissingNode()).isTrue();
  }

  @Test
  void matchesSecondaryNestedEmploymentHistoryFieldToCanonicalPrimaryField() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {
          "page_2": {
            "employer_1_address": "Flat 12A, 28/F, Belcher's Tower 1, Hong Kong"
          }
        }
        """);
    JsonNode secondary = objectMapper.readTree("""
        {
          "page_2": {
            "employment_history": [
              {
                "address": "Flat 12A, 28/F, Belcher's Tower 1, Hong Kong"
              }
            ]
          }
        }
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.at("/_parallel_recognition/page_2/employer_1_address").isMissingNode()).isTrue();
    assertThat(merged.at("/page_2/employment_history/0/address").isMissingNode()).isTrue();
  }

  @Test
  void matchesSecondaryFlatEmployerPeriodAliases() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {
          "page_2": {
            "employer_1_period_from": "01/17"
          }
        }
        """);
    JsonNode secondary = objectMapper.readTree("""
        {
          "page_2": {
            "employer_1_from": "01/17"
          }
        }
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.at("/_parallel_recognition/page_2/employer_1_period_from").isMissingNode()).isTrue();
    assertThat(merged.at("/page_2/employer_1_from").isMissingNode()).isTrue();
  }

  @Test
  void matchesEducationTableFieldAcrossDifferentSectionNames() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_3":{"higher_education_qualifications_obtained":[{"subject_and_degree_awarded":"Computer Science, Master of Science"}]}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_3":{"higher_education_qualifications":[{"subject_and_degree_awarded":"Computer Science, Master of Science"}]}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.at("/_parallel_recognition/page_3/higher_education_qualifications_obtained/0/subject_and_degree_awarded").isMissingNode()).isTrue();
    assertThat(merged.at("/page_3/higher_education_qualifications/0/subject_and_degree_awarded").isMissingNode()).isTrue();
  }

  @Test
  void matchesEmploymentTableFieldAcrossDifferentSectionNames() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_3":{"working_experience":[{"position_occupation":"AI Application Developer"}]}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_3":{"employment_history":[{"position":"AI Application Developer"}]}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.at("/_parallel_recognition/page_3/working_experience/0/position_occupation").isMissingNode()).isTrue();
    assertThat(merged.at("/page_3/employment_history/0/position").isMissingNode()).isTrue();
  }

  @Test
  void marksMissingSecondaryFieldAsDisagreement() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_1":{"maiden_surname":"ROYES"}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_1":{}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    JsonNode conflict = merged.at("/_parallel_recognition/page_1/maiden_surname");
    assertThat(conflict.path("model_agreement").asText()).isEqualTo("disagree");
    assertThat(conflict.path("outputs").get(0).path("value").asText()).isEqualTo("ROYES");
    assertThat(conflict.path("outputs").get(1).path("value").asText()).isEmpty();
  }

  @Test
  void addsSecondaryOnlyFieldToMergedResultAndMarksItForReview() throws Exception {
    JsonNode primary = objectMapper.readTree("""
        {"page_1":{}}
        """);
    JsonNode secondary = objectMapper.readTree("""
        {"page_1":{"maiden_surname_if_applicable":"LOYES"}}
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(merged.at("/page_1/maiden_surname_if_applicable").asText()).isEqualTo("LOYES");
    JsonNode conflict = merged.at("/_parallel_recognition/page_1/maiden_surname_if_applicable");
    assertThat(conflict.path("model_agreement").asText()).isEqualTo("disagree");
    assertThat(conflict.path("suggested_value").asText()).isEqualTo("LOYES");
  }
}
