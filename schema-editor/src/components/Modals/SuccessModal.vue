<template>
  <v-dialog
    v-model="opened"
    persistent
    width="500"
  >
    <v-card>
      <v-card-title class="text-h5 green lighten-1 white--text">
        Success
      </v-card-title>
      <v-card-text>
        <p class="pt-6" v-html="safeMessage">
        </p>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          text
          @click="$emit('close')"
        >
          Ok
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import { safeModalHtml } from '../../utils/safeModalHtml'

export default {
  data() {
    return {
      opened: true,
    }
  },
  props: {
    message: {
      type: String,
      default: 'Something went wrong'
    },
  },
  computed: {
    safeMessage() {
      return safeModalHtml(this.message)
    },
  },
  methods: {
    close() {
      this.opened = false
    },
  }
}
</script>
