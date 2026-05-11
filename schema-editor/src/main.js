import { createApp } from 'vue'
import App from './App.vue'
import vuetify from './plugins/vuetify'
import titleMixin from './mixins/titleMixin'
import store from './store'
import Modals from './plugins/modals'

const app = createApp(App)

app
  .mixin(titleMixin)
  .use(store)
  .use(vuetify)
  .use(Modals)
  .mount('#app')
