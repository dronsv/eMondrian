import { h } from 'vue'

// TODO(migration): replace legacy VListItemContent/VListItemIcon call sites with
// Vuetify 3 list-item prepend/default slots and remove these compatibility shims.
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
