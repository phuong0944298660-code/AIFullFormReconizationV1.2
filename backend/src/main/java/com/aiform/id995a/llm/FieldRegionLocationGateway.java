package com.aiform.id995a.llm;

import com.aiform.id995a.ocr.RenderedOcrPage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 用多模态 LLM 对「已识别到值、但缺 value_bbox」的字段做视觉定位，返回归一化 bbox，
 * 供后端裁剪真实字段截图。不使用固定坐标/模板/ROI。
 */
public interface FieldRegionLocationGateway {

  /**
   * @param missingFields 缺 bbox 的字段（按页归集）
   * @return key = {@code page|path}，value = 归一化 bbox（仅含 LLM 能视觉定位到的字段）
   */
  Map<String, NormalizedBbox> locateMissingFieldBboxes(
      String filename,
      List<RenderedOcrPage> pages,
      List<MissingFieldRegion> missingFields,
      LlmModelProfile modelProfile
  ) throws IOException;
}
