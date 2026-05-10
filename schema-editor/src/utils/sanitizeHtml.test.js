import { describe, expect, it } from 'vitest'
import { sanitizeModalHtml, sanitizeSchemaDocHtml } from './sanitizeHtml'

describe('sanitizeSchemaDocHtml', () => {
  it('keeps schema documentation markup but strips executable content', () => {
    const html = '<p onclick="alert(1)">See <code>Cube</code><script>alert(1)</script></p>'

    expect(sanitizeSchemaDocHtml(html)).toBe('<p>See <code>Cube</code></p>')
  })

  it('keeps safe links and drops unsafe href values', () => {
    const html = '<a href="api/mondrian/olap/Role.html">Role</a><a href="java&#x0A;script:alert(1)">bad</a>'

    expect(sanitizeSchemaDocHtml(html)).toBe('<a href="api/mondrian/olap/Role.html">Role</a><a>bad</a>')
  })
})

describe('sanitizeModalHtml', () => {
  it('keeps supported modal formatting without unsafe attributes', () => {
    const html = '<b class="text-h6 danger" onmouseover="alert(1)">Title</b><br><i>detail</i>'

    expect(sanitizeModalHtml(html)).toBe('<b class="text-h6">Title</b><br>detail')
  })
})
