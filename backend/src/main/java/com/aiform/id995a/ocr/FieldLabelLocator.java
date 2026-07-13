package com.aiform.id995a.ocr;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FieldLabelLocator {

  private static final double MIN_CONFIDENCE = 70;
  private static final double MIN_SCORE = 0.72;
  private static final List<List<String>> LABEL_ALIAS_GROUPS = List.of(
      List.of("签发地点", "簽發地點", "Place of issue"),
      List.of("签发日期", "簽發日期", "Date of issue"),
      List.of("届满日期", "屆滿日期", "Date of expiry"),
      List.of("旅行证件类别", "旅行證件類別", "Travel document type"),
      List.of("旅行证件号码", "旅行證件號碼", "Travel document no", "Travel document number"),
      List.of("电邮地址", "電郵地址", "E-mail address", "Email address"),
      List.of("联络电话号码", "聯絡電話號碼", "Contact telephone no", "Contact telephone number"),
      List.of("出生日期", "Date of birth"),
      List.of("出生地点", "出生地點", "Place of birth"),
      List.of("英文姓氏", "Surname in English"),
      List.of("英文名字", "Given names in English"),
      List.of("姓名中文", "Name in Chinese"),
      List.of("性别", "性別", "Sex"),
      List.of("婚姻状况", "婚姻狀況", "Marital status", "Marital/Relationship status"),
      List.of("国籍或原居地", "國籍或原居地", "国籍原居地", "國籍原居地", "Nationality/Place of domicile"),
      List.of("香港身份证号码", "香港身份證號碼", "Hong Kong identity card no", "HK identity card no"),
      List.of("内地身份证号码", "內地身份證號碼", "Mainland identity card no"),
      List.of("现时地址", "現時地址", "Present address"),
      List.of("申请人是否现正在香港", "申請人是否現正在香港", "Is the applicant currently staying in Hong Kong"),
      List.of("雇主姓名", "Employer Name", "Name of employer", "本合约由"),
      List.of("佣工姓名", "佣工", "Servant Name", "Helper Name", "本合约由"),
      List.of("合约日期", "合同订立日期", "Contract Date", "订立"),
      List.of("佣工原居地", "就本合同而言佣工的原居地是", "就本合约而言佣工的原居地是", "佣工的原居地是", "庸工的原居地是", "Servant Previous Residence", "Place of origin", "原居地"),
      List.of("合约号码", "佣工合约号码", "Contract Number", "Contract No", "D H Contract No", "家庭佣工合约号码", "家庭庸工合约号码", "家庭佣工合约"),
      List.of("合约开始日期", "Contract Start Date", "Start Date", "Employment start date"),
      List.of("佣工地址", "佣工须于雇主的住址工作及居住住址为", "Servant Address", "Residential address", "雇主住址"),
      List.of("每月工资", "5. (a) 僱主須每月向傭工支付港幣", "雇主须每月向佣工支付港币", "5. (a) 催主须每月向庸工支付港帮", "催主须每月向庸工支付港帮", "Monthly Salary", "Monthly wages", "工资"),
      List.of("膳食津贴", "如不提供膳食则应每月给予佣工港币", "如不提供膳食则应每月给予庸工港弊", "Meal Allowance", "Food allowance"),
      List.of("由雇主签署", "由雇主署", "Signature of employer", "Employer signature", "僱主簽署 Signature of employer"),
      List.of("见证人姓名", "Witness name", "见证人 姓名"),
      List.of("见证人签署", "见证人簧署", "Witness signature"),
      List.of("由佣工签署", "由体工署", "Signature of worker", "Employee signature", "Worker signature"),
      List.of("住所的面积约为", "住所的面精约为", "Residential area sq ft"),
      List.of("名成人", "Adults to care for"),
      List.of("名未成年子女", "Minors to care for"),
      List.of("名小孩", "Children under 5"),
      List.of("名将出生的婴儿", "Unborn babies"),
      List.of("家庭成员需要经常照料或留意", "Family members needing care"),
      List.of("雇主聘用以照料家庭的佣工数目", "现时雇主聘用以照料家庭的佣工数目是", "现时主聘用以照料家庭的佣工数目是", "(注：現時主聘用以照料家庭的庸工數目是）", "主聘用以照料家庭的佣工数目", "Current helper count"),
      List.of("工人房", "Separate room provided"),
      List.of("工人房的大小估计为", "Room size sq ft"),
      List.of("水电供应", "Water electricity supply"),
      List.of("厕所及沐浴设备", "廊所及沐浴设备", "Toilet bath facilities"),
      List.of("床铺", "Bedding"),
      List.of("毡或被", "蚝或被", "Quilt or blanket"),
      List.of("枕头", "Pillow"),
      List.of("衣柜", "衣槽", "Wardrobe"),
      List.of("雪柜", "雪糙", "Refrigerator"),
      List.of("桌子", "Table"),
      List.of("雇主姓名及签署", "主姓名及署", "Employer name signature"),
      List.of("佣工姓名及签署", "佣工姓名及署", "Employee name signature"),
      List.of("申请人签署", "申请人鈴署", "Signature of applicant", "Applicant signature", "Signature"),
      List.of("雇佣合约号码", "Employment contract no", "D H Contract No"),
      List.of("平均每月家庭收入", "Average monthly household income", "Average monthly household income no less than HK 15 000 declaration Yes No"),
      List.of("日期", "Date")
  );

  public List<Integer> locate(String label, List<FieldLabelDetection> detections) {
    return locate(label, detections, List.of()).bbox();
  }

  public FieldLabelLocation locate(
      String label,
      List<FieldLabelDetection> detections,
      List<Integer> valueBbox
  ) {
    String normalizedLabel = normalize(label);
    List<String> normalizedLabels = labelAliases(normalizedLabel);
    if (normalizedLabels.stream().mapToInt(String::length).max().orElse(0) < 3) {
      return FieldLabelLocation.notFound("label_too_short");
    }
    if (detections == null || detections.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_unavailable");
    }

    List<FieldLabelDetection> reliable = detections.stream()
        .filter(item -> item != null && item.bbox().size() >= 4 && item.confidence() >= MIN_CONFIDENCE)
        .toList();
    if (reliable.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_below_confidence");
    }

    List<Candidate> candidates = candidates(reliable).stream()
        .map(item -> candidate(normalizedLabels, item, valueBbox))
        .filter(item -> item.textScore() >= MIN_SCORE)
        .sorted(Comparator.comparingDouble(Candidate::combinedScore).reversed())
        .toList();
    if (candidates.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_text_not_found");
    }

    Candidate best = candidates.get(0);
    if (candidates.size() > 1
        && Math.abs(best.combinedScore() - candidates.get(1).combinedScore()) <= 0.0001
        && !best.detection().bbox().equals(candidates.get(1).detection().bbox())
        && !areAdjacent(best.detection().bbox(), candidates.get(1).detection().bbox())) {
      return FieldLabelLocation.notFound("ambiguous_candidates");
    }
    return new FieldLabelLocation(best.detection().bbox(), best.combinedScore() * 100, "");
  }

  private Candidate candidate(
      List<String> normalizedLabels,
      FieldLabelDetection detection,
      List<Integer> valueBbox
  ) {
    String normalizedDetection = normalize(detection.text());
    double textScore = normalizedLabels.stream()
        .mapToDouble(item -> matchScore(item, normalizedDetection))
        .max()
        .orElse(0);
    if (valueBbox != null && valueBbox.size() >= 4) {
      textScore = Math.max(textScore, normalizedLabels.stream()
          .mapToDouble(item -> containedLabelScore(item, normalizedDetection))
          .max()
          .orElse(0));
    }
    double geometryScore = geometryScore(detection.bbox(), valueBbox);
    double combinedScore = valueBbox != null && valueBbox.size() >= 4
        ? textScore * 0.8 + geometryScore * 0.2
        : textScore;
    return new Candidate(detection, textScore, combinedScore);
  }

  private List<FieldLabelDetection> candidates(List<FieldLabelDetection> detections) {
    ArrayList<FieldLabelDetection> values = new ArrayList<>(detections);
    for (int left = 0; left < detections.size(); left += 1) {
      for (int right = left + 1; right < detections.size(); right += 1) {
        FieldLabelDetection first = detections.get(left);
        FieldLabelDetection second = detections.get(right);
        if (areAdjacent(first.bbox(), second.bbox())) {
          values.add(merge(first, second));
        }
      }
    }
    for (int first = 0; first + 2 < detections.size(); first += 1) {
      FieldLabelDetection second = detections.get(first + 1);
      if (!areAdjacent(detections.get(first).bbox(), second.bbox())) {
        continue;
      }
      FieldLabelDetection pair = merge(detections.get(first), second);
      FieldLabelDetection third = detections.get(first + 2);
      if (areAdjacent(pair.bbox(), third.bbox())) {
        values.add(merge(pair, third));
      }
    }
    return values;
  }

  private boolean areAdjacent(List<Integer> first, List<Integer> second) {
    int firstHeight = first.get(3) - first.get(1);
    int secondHeight = second.get(3) - second.get(1);
    int maxHeight = Math.max(firstHeight, secondHeight);
    int verticalGap = Math.max(0, Math.max(first.get(1), second.get(1)) - Math.min(first.get(3), second.get(3)));
    int horizontalOverlap = overlap(first.get(0), first.get(2), second.get(0), second.get(2));
    int minWidth = Math.min(first.get(2) - first.get(0), second.get(2) - second.get(0));
    if (verticalGap <= Math.round(maxHeight * 1.5) && horizontalOverlap >= minWidth * 0.35) {
      return true;
    }

    int horizontalGap = Math.max(0, Math.max(first.get(0), second.get(0)) - Math.min(first.get(2), second.get(2)));
    int verticalOverlap = overlap(first.get(1), first.get(3), second.get(1), second.get(3));
    return horizontalGap <= Math.round(maxHeight * 2.5)
        && verticalOverlap >= Math.min(firstHeight, secondHeight) * 0.45;
  }

  private FieldLabelDetection merge(FieldLabelDetection first, FieldLabelDetection second) {
    boolean firstComesFirst = first.bbox().get(1) < second.bbox().get(1)
        || (first.bbox().get(1).equals(second.bbox().get(1)) && first.bbox().get(0) <= second.bbox().get(0));
    FieldLabelDetection leading = firstComesFirst ? first : second;
    FieldLabelDetection trailing = firstComesFirst ? second : first;
    List<Integer> bbox = List.of(
        Math.min(first.bbox().get(0), second.bbox().get(0)),
        Math.min(first.bbox().get(1), second.bbox().get(1)),
        Math.max(first.bbox().get(2), second.bbox().get(2)),
        Math.max(first.bbox().get(3), second.bbox().get(3))
    );
    return new FieldLabelDetection(
        leading.text() + " " + trailing.text(),
        Math.min(first.confidence(), second.confidence()),
        bbox
    );
  }

  private double geometryScore(List<Integer> labelBbox, List<Integer> valueBbox) {
    if (valueBbox == null || valueBbox.size() < 4) {
      return 0;
    }
    double labelCenterX = (labelBbox.get(0) + labelBbox.get(2)) / 2.0;
    double labelCenterY = (labelBbox.get(1) + labelBbox.get(3)) / 2.0;
    double valueCenterX = (valueBbox.get(0) + valueBbox.get(2)) / 2.0;
    double valueCenterY = (valueBbox.get(1) + valueBbox.get(3)) / 2.0;
    double width = Math.max(1, valueBbox.get(2) - valueBbox.get(0));
    double height = Math.max(1, valueBbox.get(3) - valueBbox.get(1));
    double dx = (labelCenterX - valueCenterX) / width;
    double dy = (labelCenterY - valueCenterY) / height;
    double distanceScore = 1.0 / (1.0 + Math.sqrt(dx * dx + dy * dy));
    boolean leftOfValue = labelBbox.get(2) <= valueBbox.get(2)
        && overlap(labelBbox.get(1), labelBbox.get(3), valueBbox.get(1), valueBbox.get(3)) > 0;
    boolean aboveValue = labelBbox.get(3) <= valueBbox.get(3)
        && overlap(labelBbox.get(0), labelBbox.get(2), valueBbox.get(0), valueBbox.get(2)) > 0;
    return Math.min(1, distanceScore + (leftOfValue || aboveValue ? 0.2 : 0));
  }

  private int overlap(int firstStart, int firstEnd, int secondStart, int secondEnd) {
    return Math.max(0, Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
  }

  private double matchScore(String label, String candidate) {
    if (candidate.isBlank()) {
      return 0;
    }
    if (label.equals(candidate)) {
      return 1;
    }
    if (label.startsWith(candidate) || candidate.startsWith(label)) {
      return (double) Math.min(label.length(), candidate.length()) / Math.max(label.length(), candidate.length());
    }
    String compactLabel = label.replace(" ", "");
    String compactCandidate = candidate.replace(" ", "");
    if (compactLabel.equals(compactCandidate)) {
      return 1;
    }
    if (Math.min(compactLabel.length(), compactCandidate.length()) >= 8) {
      double similarity = editSimilarity(compactLabel, compactCandidate);
      if (similarity >= 0.86) {
        return similarity;
      }
    }
    Set<String> labelTokens = tokens(label);
    Set<String> candidateTokens = tokens(candidate);
    if (labelTokens.isEmpty() || candidateTokens.isEmpty()) {
      return 0;
    }
    String distinctiveToken = labelTokens.iterator().next();
    if (!candidateTokens.contains(distinctiveToken)) {
      return 0;
    }
    long matched = labelTokens.stream().filter(candidateTokens::contains).count();
    return (double) matched / Math.max(labelTokens.size(), candidateTokens.size());
  }

  private List<String> labelAliases(String normalizedLabel) {
    return LABEL_ALIAS_GROUPS.stream()
        .map(group -> group.stream().map(this::normalize).toList())
        .filter(group -> group.stream().anyMatch(alias -> sameCompactText(alias, normalizedLabel)))
        .findFirst()
        .orElse(List.of(normalizedLabel));
  }

  private boolean sameCompactText(String left, String right) {
    return left.replace(" ", "").equals(right.replace(" ", ""));
  }

  private double editSimilarity(String left, String right) {
    int[] previous = new int[right.length() + 1];
    for (int column = 0; column <= right.length(); column += 1) {
      previous[column] = column;
    }
    for (int row = 1; row <= left.length(); row += 1) {
      int[] current = new int[right.length() + 1];
      current[0] = row;
      for (int column = 1; column <= right.length(); column += 1) {
        int substitution = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
        current[column] = Math.min(
            Math.min(current[column - 1] + 1, previous[column] + 1),
            previous[column - 1] + substitution
        );
      }
      previous = current;
    }
    return 1.0 - (double) previous[right.length()] / Math.max(left.length(), right.length());
  }

  private double containedLabelScore(String label, String candidate) {
    String compactLabel = label.replace(" ", "");
    String compactCandidate = candidate.replace(" ", "");
    if (compactLabel.equals("有") || compactLabel.equals("没有") || compactLabel.equals("yes")
        || compactLabel.equals("no")) {
      return 0;
    }
    boolean sufficientlyDistinctive = compactLabel.codePoints().anyMatch(this::isHan)
        ? compactLabel.codePointCount(0, compactLabel.length()) >= 2
        : compactLabel.length() >= 5;
    return sufficientlyDistinctive && compactCandidate.contains(compactLabel) ? 0.88 : 0;
  }

  private boolean isHan(int codePoint) {
    return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
  }

  private Set<String> tokens(String value) {
    Set<String> tokens = new LinkedHashSet<>();
    for (String token : value.split("[^a-z0-9\\p{IsHan}]+")) {
      if (token.length() >= 2) {
        tokens.add(canonicalToken(token));
      }
    }
    return tokens;
  }

  private String canonicalToken(String token) {
    if (token.length() > 4 && token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us")) {
      return token.substring(0, token.length() - 1);
    }
    return token;
  }

  private String normalize(String value) {
    String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\p{IsHan}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
      normalized = canonicalizeChinese(normalized)
        .replaceFirst("^(?:注|note)\\s*", "")
        .replaceFirst("^\\d+\\s*", "")
        .replaceFirst("^row\\s+\\d+\\s+", "")
        .replaceFirst("^第\\s*\\d+\\s*(?:行|列)\\s*", "")
        .replaceAll("\\s+(?:if applicable|if any|where applicable)$", "")
        .replaceAll("\\s*(?:如适用|如有)$", "")
        .replaceFirst("\\s*适用于.*$", "")
        .trim()
        .replaceAll("\\s+", " ");
    return normalized;
  }

  private String canonicalizeChinese(String value) {
    StringBuilder canonical = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index += 1) {
      canonical.append(switch (value.charAt(index)) {
        case '國' -> '国';
        case '關' -> '关';
        case '係' -> '系';
        case '狀' -> '状';
        case '況' -> '况';
        case '別' -> '别';
        case '適' -> '适';
        case '於' -> '于';
        case '現' -> '现';
        case '時' -> '时';
        case '請' -> '请';
        case '發' -> '发';
        case '點' -> '点';
        case '證' -> '证';
        case '號' -> '号';
        case '碼' -> '码';
        case '電' -> '电';
        case '郵' -> '邮';
        case '聯' -> '联';
        case '絡' -> '络';
        case '類' -> '类';
        case '僱', '倔', '偃' -> '雇';
        case '傭', '庸' -> '佣';
        case '約' -> '约';
        case '訂' -> '订';
        case '簽' -> '签';
        case '見' -> '见';
        case '積' -> '积';
        case '為' -> '为';
        case '將' -> '将';
        case '嬰' -> '婴';
        case '兒' -> '儿';
        case '員' -> '员';
        case '數' -> '数';
        case '則' -> '则';
        case '應' -> '应';
        case '給' -> '给';
        case '幣' -> '币';
        case '須' -> '须';
        case '設' -> '设';
        case '備' -> '备';
        case '廁' -> '厕';
        case '鋪' -> '铺';
        case '櫃' -> '柜';
        case '頭' -> '头';
        case '氈' -> '毡';
        case '貼' -> '贴';
        default -> value.charAt(index);
      });
    }
    return canonical.toString();
  }

  private record Candidate(
      FieldLabelDetection detection,
      double textScore,
      double combinedScore
  ) {}
}
