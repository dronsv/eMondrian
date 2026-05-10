<template>
  <v-dialog
    v-model="opened"
    persistent
  >
    <v-card>
      <v-card-title class="text-h5 grey lighten-2">
        Open schema
      </v-card-title>
      <v-card-text class="pa-4">
        <v-tabs v-model="tab">
          <v-tab value="server">Open from server</v-tab>
          <v-tab value="local">Open from local file</v-tab>
        </v-tabs>
        <v-row>
          <v-col>
            <v-window v-model="tab">
              <v-window-item value="server">
                <v-card-title>
                  Load schema from server
                </v-card-title>
                <v-text-field
                  class="mx-4"
                  v-model="serverAddress"
                  label="XMLA server address"
                ></v-text-field>
                <v-divider></v-divider>
              </v-window-item>
              <v-window-item value="local">
                <v-card-title>
                  Upload a schema file
                </v-card-title>
                <v-file-input
                  v-model="schemaFile"
                  class="mx-4"
                  accept=".xml"
                  label="Select schema"
                ></v-file-input>
                <v-divider></v-divider>
              </v-window-item>
            </v-window>
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
          text
          @click="$emit('close')"
        >
          Cancel
        </v-btn>
        <v-btn
          text
          color="primary"
          :disabled="!canContinue"
          @click="openSchema"
        >
          Continue
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  data() {
    return {
      serverAddress: import.meta.env.PROD ? "../xmla" : 'https://ssemenkoff.dev/emondrian/xmla',
      schemaFile: null,
      tab: 'server',
      opened: true,
    }
  },
  computed: {
    canContinue() {
      return (this.tab === 'server' && !!this.serverAddress) || (this.tab === 'local' && !!this.schemaFile)
    }
  },
  methods: {
    close() {
      this.opened = false
    },
    openSchema() {
      if (this.tab === 'server') {
        this.$emit('openFromServer', this.serverAddress)
      } else if (this.tab === 'local') {
        this.$emit('openFromLocal', this.schemaFile)
      }
    }
  }
}
</script>
