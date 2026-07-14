export const LOCAL_MODEL_LABEL = '主模型'
const MODEL_LABELS_BY_ID = new Map([
  ['local-qwen3.6-35b-a3b', LOCAL_MODEL_LABEL]
])

const LEGACY_LABELS = new Map([
  ['Qwen3.6-35B-A3B 视觉结构化', LOCAL_MODEL_LABEL],
  ['Qwen3.6-35B-A3B multimodal structured extraction', LOCAL_MODEL_LABEL],
  ['Qwen3.6-35B-A3B', LOCAL_MODEL_LABEL]
])

const SUPPORTED_MODEL_IDS = new Set(['local-qwen3.6-35b-a3b'])

function normalizedModelKey(value) {
  return String(value || '').trim().toLowerCase()
}

export function isHiddenModelOption(model) {
  const id = typeof model === 'string' ? model : model?.id
  return !SUPPORTED_MODEL_IDS.has(normalizedModelKey(id))
}

export function modelDisplayLabel(model, fallback = LOCAL_MODEL_LABEL) {
  if (!model) return fallback
  if (typeof model === 'string') {
    return LEGACY_LABELS.get(model) || model || fallback
  }
  if (MODEL_LABELS_BY_ID.has(model.id)) {
    return MODEL_LABELS_BY_ID.get(model.id)
  }
  const label = String(model.label || '').trim()
  return LEGACY_LABELS.get(label) || label || fallback
}

export function normalizeModelOptions(models = []) {
  return models
    .filter((model) => !isHiddenModelOption(model))
    .map((model) => ({
      ...model,
      label: modelDisplayLabel(model, model?.label || '')
    }))
}

export function extractionModeDisplayLabel(response) {
  const rawLabel = response?.engineStatus?.extractionMode || response?.model || ''
  return modelDisplayLabel(rawLabel, rawLabel || LOCAL_MODEL_LABEL)
}
