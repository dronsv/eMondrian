<template>
  <v-dialog
    v-model="opened"
    persistent
    fullscreen
    scrollable
  >
    <v-card class="d-flex flex-column" style="z-index: 100;">
      <v-card-title class="pa-0">
        <v-toolbar
          dark
          color="primary"
        >
          <v-toolbar-title>XML Editor</v-toolbar-title>
          <v-spacer></v-spacer>
          <v-btn
            class="mr-2"
            @click="save"
          >
            Save
          </v-btn>
          <v-btn
            icon
            dark
            @click="callCloseAction"
          >
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-toolbar>
      </v-card-title>
      <v-card-text class="pa-0" ref="cardContent">
        <div ref="editor"></div>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script>
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import 'monaco-editor/esm/vs/basic-languages/xml/xml.contribution'

export default {
  async mounted() {
    const serializer = new XMLSerializer
    const xmlContent = serializer.serializeToString(this.element)
    this.xmlContent = xmlContent
    this.lastSavedElement = xmlContent
    document.documentElement.style.overflow = 'hidden'

    await this.$nextTick()

    const cardContent = this.$refs.cardContent?.$el || this.$refs.cardContent
    const el = this.$refs.editor

    if (!cardContent || !el) return

    el.style.width = `${cardContent.clientWidth || window.innerWidth}px`
    el.style.height = `${cardContent.clientHeight || window.innerHeight}px`
    el.style.overflow = 'hidden'
    cardContent.style.overflow = 'hidden'

    this.editor = monaco.editor.create(el, {
      value: this.xmlContent,
      language: 'xml',
    })
  },
  unmounted() {
    this.editor?.dispose()
    document.documentElement.style.overflow = ''
  },
  data() {
    return {
      opened: true,
      xmlContent: '',
      lastSavedElement: '',
      editor: null,
    }
  },
  props: {
    element: {
      type: Element,
      required: true,
    }
  },
  methods: {
    close() {
      this.opened = false
    },
    async save() {
      if (!this.editor) return
      this.xmlContent = this.editor.getModel().getValue();

      this.lastSavedElement = this.xmlContent
      this.$emit('saveElement', this.xmlContent);
    },
    async callCloseAction() {
      const { confirmed } = await this.checkChanges()
      if (confirmed) {
        this.$emit('close')
      }
    },
    async checkChanges() {
      if (!this.editor) return { confirmed: true }
      this.xmlContent = this.editor.getModel().getValue();

      return this.lastSavedElement !== this.xmlContent
        ? this.$confirmationModal.open()
        : { confirmed: true }
    },
  }
}
</script>
<style scoped>
.fixed-toolbar {
  position: fixed;
  top: 0;
  width: 100%;
}
</style>
