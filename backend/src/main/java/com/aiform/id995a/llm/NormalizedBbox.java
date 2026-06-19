package com.aiform.id995a.llm;

/** 归一化到 [0,1] 的边界框：{x, y} 为左上角，{width, height} 为尺寸（相对页面图像）。 */
public record NormalizedBbox(double x, double y, double width, double height) {}
