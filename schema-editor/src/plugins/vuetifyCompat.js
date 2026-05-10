import { h } from 'vue'

export const VListItemIcon = {
  name: 'VListItemIcon',
  setup(props, { slots }) {
    return () => h('div', { class: 'v-list-item__prepend' }, slots.default?.())
  },
}

export const VListItemContent = {
  name: 'VListItemContent',
  setup(props, { slots }) {
    return () => h('div', { class: 'v-list-item__content' }, slots.default?.())
  },
}
