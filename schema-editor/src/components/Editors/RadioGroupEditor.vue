<template>
  <div>
    <div class="text-subtitle-1">{{ label }}</div>
    <v-radio-group
      v-model="model"
      inline
    >
      <v-radio
        v-for="(item, i) in items"
        :key="i"
        :label="item"
        :value="item"
      ></v-radio>
    </v-radio-group>
  </div>
</template>

<script>
export default {
  emits: ['update:modelValue'],
  props: {
    modelValue: {
      type: String,
      default: 'none'
    },
    label: {
      type: String,
      required: true,
    },
    items: {
      type: Array,
    },
  },
  data() {
    return {
      model: this.modelValue
    }
  },
  watch: {
    model(value) {
      this.$emit('update:modelValue', value)
    },
    modelValue(value) {
      this.model = value || 'none'
    },
  }
}
</script>
