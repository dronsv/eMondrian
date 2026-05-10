<template>
  <v-list density="compact" nav v-if="schema">
    <div ref="listItems">
      <v-row class="align-center text-black py-1">
        <v-col cols=10 class="capitalize">
          Schema
        </v-col>
      </v-row>
      <v-divider />
      <element-list-item
        :element="schema"
        :timestamp="timestamp"
        @open-editor="openEditor"
      />
    </div>
  </v-list>
</template>

<script>
import ElementListItem from './ListItems/ElementListItem.vue'
export default {
  components: { ElementListItem },
  props: {
    xmlDoc: {
      type: XMLDocument,
      required: true
    },
    timestamp: {
      type: Number,
      required: true,
    }
  },
  computed: {
    schema() {
      this.timestamp
      return this.xmlDoc?.querySelector("Schema")
    },
    openedElement() {
      return this.$store.getters['SchemaEditor/openedElement']
    },
  },
  watch: {
    async openedElement(newVal) {
      await this.$nextTick()
      await this.$nextTick()
      this.updateEditorState(newVal)
    }
  },
  methods: {
    openEditor(payload) {
      this.$emit('open-editor', payload)
    },
    updateEditorState(currentItem) {
      if (currentItem === null) return
      this.$refs.listItems?.querySelector('.v-list-item')?.scrollIntoView({
        block: "nearest",
        inline: "nearest",
        behavior: "smooth"
      });
    }
  }
}
</script>
