const DROP_CONTENT_TAGS = new Set(['script', 'style', 'template', 'iframe', 'object', 'embed'])
const SCHEMA_DOC_TAGS = new Set(['a', 'b', 'br', 'code', 'dfn', 'em', 'i', 'li', 'ol', 'p', 'pre', 'strong', 'ul'])
const MODAL_TAGS = new Set(['b', 'br', 'code', 'p', 'pre', 'strong'])
const HEADING_TAGS = new Set(['b', 'strong'])

function isSafeHref(href) {
  const normalized = String(href ?? '').replace(/[\u0000-\u001F\u007F\s]+/g, '').toLowerCase()
  return !!normalized
    && !normalized.startsWith('javascript:')
    && !normalized.startsWith('data:')
    && !normalized.startsWith('vbscript:')
}

function copySafeAttributes(source, target, options) {
  const tagName = source.tagName.toLowerCase()

  if (options.allowLinks && tagName === 'a') {
    const href = source.getAttribute('href')
    if (isSafeHref(href)) {
      target.setAttribute('href', href)
    }
  }

  if (options.allowTextH6Class && HEADING_TAGS.has(tagName) && source.classList.contains('text-h6')) {
    target.className = 'text-h6'
  }
}

function sanitizeNode(node, targetDocument, options) {
  if (node.nodeType === Node.TEXT_NODE) {
    return targetDocument.createTextNode(node.textContent ?? '')
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return targetDocument.createDocumentFragment()
  }

  const tagName = node.tagName.toLowerCase()
  if (DROP_CONTENT_TAGS.has(tagName)) {
    return targetDocument.createDocumentFragment()
  }

  if (!options.allowedTags.has(tagName)) {
    const fragment = targetDocument.createDocumentFragment()
    node.childNodes.forEach((child) => {
      fragment.appendChild(sanitizeNode(child, targetDocument, options))
    })
    return fragment
  }

  const element = targetDocument.createElement(tagName)
  copySafeAttributes(node, element, options)
  node.childNodes.forEach((child) => {
    element.appendChild(sanitizeNode(child, targetDocument, options))
  })
  return element
}

export function sanitizeHtml(value, options = {}) {
  const sourceDocument = new DOMParser().parseFromString(String(value ?? ''), 'text/html')
  const targetDocument = document.implementation.createHTMLDocument('')
  const container = targetDocument.createElement('div')
  const sanitizerOptions = {
    allowedTags: options.allowedTags ?? new Set(),
    allowLinks: !!options.allowLinks,
    allowTextH6Class: !!options.allowTextH6Class,
  }

  sourceDocument.body.childNodes.forEach((node) => {
    container.appendChild(sanitizeNode(node, targetDocument, sanitizerOptions))
  })

  return container.innerHTML
}

export function sanitizeSchemaDocHtml(value) {
  return sanitizeHtml(value, {
    allowedTags: SCHEMA_DOC_TAGS,
    allowLinks: true,
  })
}

export function sanitizeModalHtml(value) {
  return sanitizeHtml(value, {
    allowedTags: MODAL_TAGS,
    allowTextH6Class: true,
  })
}
