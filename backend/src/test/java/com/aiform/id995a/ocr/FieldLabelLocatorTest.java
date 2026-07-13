package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldLabelLocatorTest {

  private final FieldLabelLocator locator = new FieldLabelLocator();

  @Test
  void locatesPrintedLabelWithoutMatchingTheHandwrittenValue() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection(
            "Length of residence in country/territory of domicile",
            98,
            List.of(25, 1025, 430, 1072)
        ),
        new FieldLabelDetection("22", 61, List.of(445, 1030, 490, 1070)),
        new FieldLabelDetection(
            "Has the applicant completed any of the following programmes",
            99,
            List.of(25, 1092, 720, 1128)
        )
    );

    List<Integer> bbox = locator.locate(
        "Length of residence in country/territory of domicile",
        detections
    );

    assertThat(bbox).containsExactly(25, 1025, 430, 1072);
  }

  @Test
  void returnsNoHighlightWhenNoPrintedLabelMatchesReliably() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("22", 98, List.of(445, 1030, 490, 1070)),
        new FieldLabelDetection("undergraduate qualification", 98, List.of(25, 1092, 400, 1128))
    );

    assertThat(locator.locate(
        "Length of residence in country/territory of domicile",
        detections
    )).isEmpty();
  }

  @Test
  void matchesLabelsDespitePunctuationAndWhitespaceDifferences() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection(
            "E-mail address (if any)",
            97,
            List.of(25, 915, 225, 950)
        )
    );

    assertThat(locator.locate("E mail address if any", detections))
        .containsExactly(25, 915, 225, 950);
  }

  @Test
  void rejectsGenericSuffixOverlapWhenDistinctiveFieldWordIsMissing() {
    assertThat(locator.locate(
        "Status in Hong Kong",
        List.of(new FieldLabelDetection(
            "in Hong Kong (to be completed by the applicant)",
            99,
            List.of(28, 176, 503, 201)
        ))
    )).isEmpty();
  }

  @Test
  void rejectsAmbiguousDuplicatePrintedLabels() {
    assertThat(locator.locate(
        "(if any)",
        List.of(
            new FieldLabelDetection("(if any)", 99, List.of(20, 100, 80, 120)),
            new FieldLabelDetection("(if any)", 99, List.of(220, 100, 280, 120))
        )
    )).isEmpty();
  }

  @Test
  void resolvesAdjacentBilingualLabelsAsOnePrintedField() {
    FieldLabelLocation location = locator.locate(
        "出生日期",
        List.of(
            new FieldLabelDetection("出生日期", 99, List.of(371, 646, 454, 670)),
            new FieldLabelDetection("Date of birth", 99, List.of(373, 670, 463, 694))
        ),
        List.of()
    );

    assertThat(location.bbox()).isNotEmpty();
  }

  @Test
  void resolvesDuplicateLabelsUsingTheValueBboxGeometry() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("Name of employer(s)", 98, List.of(100, 300, 300, 340)),
        new FieldLabelDetection("Name of employer(s)", 98, List.of(100, 700, 300, 740))
    );

    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        detections,
        List.of(320, 720, 620, 780)
    );

    assertThat(location.bbox()).containsExactly(100, 700, 300, 740);
    assertThat(location.reason()).isEmpty();
  }

  @Test
  void mergesAdjacentOcrLinesBeforeMatchingTheLabel() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("Name of", 96, List.of(100, 300, 220, 330)),
        new FieldLabelDetection("employer(s)", 97, List.of(100, 334, 280, 366))
    );

    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        detections,
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 280, 366);
    assertThat(location.reason()).isEmpty();
  }

  @Test
  void reportsWhyAConfidentOcrCandidateDidNotMatch() {
    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        List.of(new FieldLabelDetection("Address", 98, List.of(100, 300, 220, 330))),
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).isEmpty();
    assertThat(location.reason()).isEqualTo("ocr_text_not_found");
  }

  @Test
  void toleratesParenthesizedPluralBeingRecognizedAsAPlainPlural() {
    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        List.of(new FieldLabelDetection("Name of employers", 96, List.of(100, 300, 280, 340))),
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 280, 340);
  }

  @Test
  void locatesId990aChineseLabelThroughItsPrintedEnglishAlias() {
    FieldLabelLocation location = locator.locate(
        "签发地点",
        List.of(
            new FieldLabelDetection("策發地點", 83, List.of(100, 300, 210, 334)),
            new FieldLabelDetection("Place of issue", 97, List.of(100, 336, 250, 370))
        ),
        List.of(280, 310, 520, 375)
    );

    assertThat(location.bbox()).containsExactly(100, 336, 250, 370);
  }

  @Test
  void locatesShortChineseId990aLabelThroughItsEnglishAlias() {
    FieldLabelLocation location = locator.locate(
        "性別",
        List.of(new FieldLabelDetection("Sex", 99, List.of(100, 300, 160, 334))),
        List.of(200, 290, 280, 350)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 160, 334);
  }

  @Test
  void locatesId407LabelWhenOcrContainsOneCharacterError() {
    FieldLabelLocation location = locator.locate(
        "Name of employer",
        List.of(new FieldLabelDetection(
            "Name of empIoyer",
            95,
            List.of(100, 300, 300, 340)
        )),
        List.of(320, 300, 620, 350)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 300, 340);
  }

  @Test
  void locatesId988aLabelSplitAcrossThreeOcrLines() {
    FieldLabelLocation location = locator.locate(
        "Name of current employer (if applicable)",
        List.of(
            new FieldLabelDetection("Name of", 97, List.of(100, 300, 220, 330)),
            new FieldLabelDetection("current employer", 96, List.of(100, 334, 290, 364)),
            new FieldLabelDetection("if applicable", 95, List.of(100, 368, 250, 398))
        ),
        List.of(300, 330, 620, 400)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 290, 398);
  }

  @Test
  void locatesId988bLabelDespiteMinorOcrWordError() {
    FieldLabelLocation location = locator.locate(
        "Signature of employer",
        List.of(new FieldLabelDetection(
            "Signature of empIoyer",
            94,
            List.of(100, 600, 320, 640)
        )),
        List.of(340, 590, 620, 650)
    );

    assertThat(location.bbox()).containsExactly(100, 600, 320, 640);
  }

  @Test
  void locatesChineseLabelWhenPrintedTextAddsApplicabilitySuffix() {
    FieldLabelLocation location = locator.locate(
        "姓名 (中文)",
        List.of(new FieldLabelDetection(
            "姓名(中文)(如適用)",
            96,
            List.of(100, 300, 300, 340)
        )),
        List.of(320, 300, 620, 350)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 300, 340);
  }

  @Test
  void locatesTraditionalChineseLabelDespiteSlashAndVariantCharacters() {
    FieldLabelLocation location = locator.locate(
        "婚姻/關係狀況",
        List.of(new FieldLabelDetection(
            "婚姻／關係狀况",
            96,
            List.of(100, 300, 300, 340)
        )),
        List.of(320, 300, 620, 350)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 300, 340);
  }

  @Test
  void locatesNationalityLabelBeforePrintedApplicabilityExplanation() {
    FieldLabelLocation location = locator.locate(
        "國籍/原居地",
        List.of(new FieldLabelDetection(
            "國籍原居地（適用於内地、澳門及台灣居民)",
            96,
            List.of(100, 300, 430, 350)
        )),
        List.of(450, 300, 620, 350)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 430, 350);
  }

  @Test
  void locatesPrintedTableHeaderForSyntheticRowLabel() {
    FieldLabelLocation location = locator.locate(
        "Row 2 major subject",
        List.of(new FieldLabelDetection(
            "Major subject",
            98,
            List.of(300, 200, 450, 240)
        )),
        List.of()
    );

    assertThat(location.bbox()).containsExactly(300, 200, 450, 240);
  }

  @Test
  void locatesId407SemanticEmployerFieldThroughContractAnchor() {
    FieldLabelLocation location = locator.locate(
        "Employer Name",
        List.of(new FieldLabelDetection(
            "本合約由",
            98,
            List.of(149, 231, 259, 262)
        )),
        List.of(241, 210, 386, 273)
    );

    assertThat(location.bbox()).containsExactly(149, 231, 259, 262);
  }

  @Test
  void locatesId407ShortLabelInsideACombinedOcrLineWhenValueBboxCanDisambiguate() {
    FieldLabelLocation location = locator.locate(
        "名成人",
        List.of(new FieldLabelDetection(
            "2名成人2名未成年子女（年龄介乎5至18岁)",
            98,
            List.of(251, 392, 770, 427)
        )),
        List.of(210, 390, 250, 430)
    );

    assertThat(location.bbox()).containsExactly(251, 392, 770, 427);
  }

  @Test
  void locatesId407ContractDateThroughThePrintedSentenceAnchor() {
    FieldLabelLocation location = locator.locate(
        "Contract Date",
        List.of(new FieldLabelDetection(
            "4月20日訂立。兼載有下列各項條件",
            92,
            List.of(381, 268, 885, 295)
        )),
        List.of(250, 250, 380, 300)
    );

    assertThat(location.bbox()).containsExactly(381, 268, 885, 295);
  }

  @Test
  void locatesId407WitnessSignatureDespiteStableOcrTypo() {
    FieldLabelLocation location = locator.locate(
        "見證人簽署",
        List.of(new FieldLabelDetection(
            "(見證人簧署)",
            93,
            List.of(868, 1280, 1023, 1304)
        )),
        List.of(788, 1215, 897, 1283)
    );

    assertThat(location.bbox()).containsExactly(868, 1280, 1023, 1304);
  }

  @Test
  void locatesRemainingId407ClauseLabelsFromActualPageOcr() {
    assertThat(locator.locate(
        "1. 就本合同而言，傭工的原居地是",
        List.of(new FieldLabelDetection(
            "1. 就本合約而言，庸工的原居地是Jakarta Indonesia",
            87,
            List.of(148, 299, 899, 334)
        )),
        List.of(573, 288, 1019, 360)
    ).bbox()).containsExactly(148, 299, 899, 334);

    assertThat(locator.locate(
        "5. (a) 僱主須每月向傭工支付港幣",
        List.of(new FieldLabelDetection(
            "5. (a) 催主须每月向庸工支付港帮6500",
            84,
            List.of(151, 769, 595, 794)
        )),
        List.of(535, 756, 637, 810)
    ).bbox()).containsExactly(151, 769, 595, 794);

    assertThat(locator.locate(
        "如不提供膳食，則應每月給予傭工港幣",
        List.of(new FieldLabelDetection(
            "如不提供膳食，则應每月給予庸工港弊300元的膳食津貼。",
            97,
            List.of(109, 904, 803, 929)
        )),
        List.of(535, 900, 637, 954)
    ).bbox()).containsExactly(109, 904, 803, 929);
  }

  @Test
  void locatesActualId407OcrVariantsForHelperCountAndBathroom() {
    assertThat(locator.locate(
        "現時僱主聘用以照料家庭的傭工數目是",
        List.of(new FieldLabelDetection(
            "(注：現時主聘用以照料家庭的庸工數目是1名）",
            89,
            List.of(233, 497, 794, 525)
        )),
        List.of(751, 504, 776, 540)
    ).bbox()).containsExactly(233, 497, 794, 525);

    assertThat(locator.locate(
        "廁所及沐浴設備",
        List.of(new FieldLabelDetection(
            "(b) 廊所及沐浴設備",
            98,
            List.of(271, 1167, 481, 1194)
        )),
        List.of(764, 1170, 789, 1206)
    ).bbox()).containsExactly(271, 1167, 481, 1194);
  }

  @Test
  void locatesId988aApplicantSignatureDespiteActualOcrCharacterError() {
    FieldLabelLocation location = locator.locate(
        "Signature of applicant",
        List.of(new FieldLabelDetection(
            "申请人鈴署",
            82,
            List.of(809, 1584, 902, 1605)
        )),
        List.of(955, 1584, 1210, 1656)
    );

    assertThat(location.bbox()).containsExactly(809, 1584, 902, 1605);
  }
}
