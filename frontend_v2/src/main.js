import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router/index.js'
import './style.css'
import './styles/unslop.css'

createApp(App)
  .use(router)
  .use(ElementPlus)
  .mount('#app')
