import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Monaco is lazy-loaded but still emitted as one large chunk. Keep this just
// above the current Monaco chunk size so future non-Monaco bloat remains visible.
const MONACO_CHUNK_WARNING_LIMIT_KB = 2300

function manualChunks(id) {
  if (!id.includes('/node_modules/')) return

  if (id.includes('/node_modules/monaco-editor/')) {
    return 'monaco-editor'
  }

  if (id.includes('/node_modules/vuetify/')) {
    return 'vuetify'
  }

  if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/')) return 'vue-runtime'
  if (id.includes('/node_modules/lodash/')) return 'lodash'
  if (id.includes('/node_modules/vuedraggable/')) return 'vuedraggable'
}

export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? '/emondrian/schema_editor/' : '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    chunkSizeWarningLimit: MONACO_CHUNK_WARNING_LIMIT_KB,
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
}))
