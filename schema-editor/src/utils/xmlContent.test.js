import { describe, expect, it } from 'vitest'
import { getElementTextValue, setElementCDataValue, xmlEscape } from './xmlContent'

describe('xmlEscape', () => {
  it('escapes values for XML text nodes', () => {
    expect(xmlEscape(`a&b<c>d"e'f`)).toBe('a&amp;b&lt;c&gt;d&quot;e&apos;f')
  })
})

describe('setElementCDataValue', () => {
  it('writes CDATA through XML DOM APIs and handles CDATA terminators', () => {
    const document = new DOMParser().parseFromString('<Root/>', 'text/xml')
    const element = document.documentElement

    setElementCDataValue(element, 'before]]>after')

    const serialized = new XMLSerializer().serializeToString(element)
    const reparsed = new DOMParser().parseFromString(serialized, 'text/xml')
    expect(reparsed.querySelector('parsererror')).toBeNull()
    expect(reparsed.documentElement.textContent).toBe('before]]>after')
  })
})

describe('getElementTextValue', () => {
  it('returns the current XML element text value', () => {
    const document = new DOMParser().parseFromString('<Root><![CDATA[ value ]]></Root>', 'text/xml')

    expect(getElementTextValue(document.documentElement)).toBe('value')
  })
})
