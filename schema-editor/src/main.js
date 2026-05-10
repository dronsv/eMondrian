import { createApp } from 'vue'
import App from './App.vue'
import vuetify from './plugins/vuetify'
import titleMixin from './mixins/titleMixin'
import store from './store'
import Modals from './plugins/modals'
import { VListItemContent, VListItemIcon } from './plugins/vuetifyCompat'

const app = createApp(App)

app
  .component('VListItemContent', VListItemContent)
  .component('VListItemIcon', VListItemIcon)
  .mixin(titleMixin)
  .use(store)
  .use(vuetify)
  .use(Modals)
  .mount('#app')
