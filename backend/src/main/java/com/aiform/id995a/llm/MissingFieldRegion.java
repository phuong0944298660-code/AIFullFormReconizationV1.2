package com.aiform.id995a.llm;

/** 一个已识别到值、但缺少 value_bbox（无法裁剪截图）的字段描述。 */
public record MissingFieldRegion(int page, String path, String label, String value) {}
