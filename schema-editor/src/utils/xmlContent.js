export function xmlEscape(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;')
}

export function getElementTextValue(element) {
  return element.textContent?.trim() ?? ''
}

export function setElementCDataValue(element, value) {
  while (element.firstChild) {
    element.removeChild(element.firstChild)
  }

  const document = element.ownerDocument
  const parts = String(value ?? '').split(']]>')
  parts.forEach((part, index) => {
    const content = `${index > 0 ? '>' : ''}${part}${index < parts.length - 1 ? ']]' : ''}`
    element.appendChild(document.createCDATASection(content))
  })
}
