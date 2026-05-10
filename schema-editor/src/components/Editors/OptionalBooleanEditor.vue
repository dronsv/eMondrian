<template>
  <div>
    <div class="text-subtitle-1">{{ label }}</div>
    <v-row>
      <v-col align-self="center" cols="11">
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
      </v-col>
      <v-col align-self="center" cols="1">
        <v-btn
          icon
          size="small"
          @click="reset"
        >
          <v-icon
            v-text="'mdi-close'"
          ></v-icon>
        </v-btn>
      </v-col>
    </v-row>
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
  },
  data() {
    return {
      model: this.modelValue,
      items: [ 'true', 'false' ],
    }
  },
  watch: {
    model(value) {
      this.$emit('update:modelValue', value)
    },
    modelValue(value) {
      this.model = value || 'none'
    },
  },
  methods: {
    reset() {
      this.model = 'none';
    }
  }
}
</script>
