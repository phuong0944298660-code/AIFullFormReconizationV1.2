export function verificationNotice(conclusion = {}) {
  const status = String(conclusion.status || '').trim()
  return status ? '' : ''
}
