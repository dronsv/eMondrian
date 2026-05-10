import { afterEach, describe, expect, it } from 'vitest'
import { fetchSchemaForCatalog } from './XmlaApi'

const nativeXmlHttpRequest = globalThis.XMLHttpRequest

class FakeXmlHttpRequest {
  static instances = []

  constructor() {
    this.headers = {}
    this.responseXML = new DOMParser().parseFromString('<Response/>', 'text/xml')
    FakeXmlHttpRequest.instances.push(this)
  }

  open(method, url, async) {
    this.method = method
    this.url = url
    this.async = async
  }

  setRequestHeader(name, value) {
    this.headers[name] = value
  }

  send(data) {
    this.data = data
    this.onload()
  }
}

afterEach(() => {
  globalThis.XMLHttpRequest = nativeXmlHttpRequest
  FakeXmlHttpRequest.instances = []
})

describe('XmlaApi', () => {
  it('posts XML requests with escaped catalog ids', async () => {
    globalThis.XMLHttpRequest = FakeXmlHttpRequest

    await fetchSchemaForCatalog('/xmla', 'catalog&</DatabaseID><Injected>')

    const request = FakeXmlHttpRequest.instances[0]
    expect(request.method).toBe('POST')
    expect(request.url).toBe('/xmla')
    expect(request.headers['Content-type']).toBe('text/xml')
    expect(request.data).toContain('catalog&amp;&lt;/DatabaseID&gt;&lt;Injected&gt;')
  })
})
