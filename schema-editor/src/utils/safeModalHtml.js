function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export function safeModalHtml(message) {
  return escapeHtml(message)
    .replaceAll('&lt;b class=&quot;text-h6&quot;&gt;', '<b class="text-h6">')
    .replaceAll('&lt;b&gt;', '<b>')
    .replaceAll('&lt;/b&gt;', '</b>')
    .replaceAll('&lt;br&gt;', '<br>')
    .replaceAll('&lt;br/&gt;', '<br>')
    .replaceAll('&lt;br /&gt;', '<br>')
    .replaceAll('&lt;p&gt;', '<p>')
    .replaceAll('&lt;/p&gt;', '</p>')
}
