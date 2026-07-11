package com.aiform.id995a.ocr;

public interface FieldJudgeGateway {

  FieldJudgeObservation judge(
      String fieldKey,
      String fieldLabel,
      String expectedValue,
      String valueType,
      String snapshotDataUrl
  );
}
