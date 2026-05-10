import { createApp } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../store', () => ({ default: { install() {} } }))
vi.mock('./vuetify', () => ({ default: { install() {} } }))

import { closeAndUnmount, DESTROY_DELAY_MS, mountModal } from './modals'

const TestModal = {
  props: {
    message: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      opened: true,
    }
  },
  methods: {
    close() {
      this.opened = false
    },
  },
  template: '<div data-test-id="modal">{{ message }}</div>',
}

afterEach(() => {
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('modal lifecycle', () => {
  it('mounts modal props and removes its container after close delay', () => {
    vi.useFakeTimers()
    const rootApp = createApp({})
    const mounted = mountModal(rootApp, TestModal, { message: 'Confirm action' })

    expect(document.body.textContent).toContain('Confirm action')
    closeAndUnmount(mounted)
    expect(mounted.instance.opened).toBe(false)

    vi.advanceTimersByTime(DESTROY_DELAY_MS)

    expect(document.body.textContent).not.toContain('Confirm action')
  })
})
