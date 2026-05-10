<template>
  <v-dialog
    :model-value="opened"
    persistent
    width="1000"
  >
    <v-card>
      <v-card-title class="text-h5 bg-primary text-white">
        Select source table
      </v-card-title>
      <v-card-text class="pt-5">
        <v-data-table
          :headers="headers"
          :items="rows"
          :items-per-page="10"
          :search="search"
          :row-props="getRowProps"
          :loading="rowsLoading"
          class="elevation-1"
          density="compact"
        >
          <template v-slot:top>
            <v-text-field
              v-model="search"
              label="Search"
              class="mx-4"
            ></v-text-field>
          </template>
          <template v-slot:item.actions="{ item }">
            <v-btn
              class="ma-2"
              @click="selectItem(item.raw || item)"
            >
              Select
            </v-btn>
          </template>
        </v-data-table>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          variant="text"
          @click="$emit('close')"
        >
          Close
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import { fetchTableList } from '../../services/XmlaApi'

export default {
  props: {
    opened: {
      type: Boolean,
      default: false,
    },
    selectedAttributeValue: {
      type: String,
      default: '',
    }
  },
  data() {
    return {
      rowsLoading: false,
      search: '',
      headers: [
        {
          title: 'Catalog',
          align: 'start',
          key: 'tableCatalog',
          width: '15%',
        },
        {
          title: 'Schema',
          key: 'tableSchema',
          width: '15%',
        },
        {
          title: 'Name',
          key: 'tableName',
          width: '40%',
        },
        {
          title: 'Type',
          key: 'tableType',
          width: '20%'
        },
        {
          title: 'Actions',
          key: 'actions',
          sortable: false,
          width: '20%',
          align: 'start',
        },
      ],
      rows: [],
    }
  },
  watch: {
    async opened(isOpen) {
      if (!isOpen) return

      this.rowsLoading = true
      try {
        const serverAddress = this.$store.getters['SchemaEditor/serverUrl'];
        const response = await fetchTableList(serverAddress)

        const errorResponse = response.querySelector('Fault')
        if (errorResponse) {
          const error = errorResponse.querySelector('detail > Error')
          const errorMessage = error.getAttribute('Description')
          const errorCode = error.getAttribute('ErrorCode')
          throw new Error(`<b class="text-h6">Server returned error response</b><br><b>Error code:</b> ${errorCode}<br><b>Error message:</b> ${errorMessage}`)
        }

        const rows = Array.from(response.querySelectorAll('row'))
        const parsedRows = rows.map((e) => {
          const textFrom = (tagName) => e.querySelector(tagName)?.textContent || ''
          return {
            tableCatalog: textFrom('TABLE_CATALOG'),
            tableSchema: textFrom('TABLE_SCHEMA'),
            tableName: textFrom('TABLE_NAME'),
            tableType: textFrom('TABLE_TYPE'),
          }
        })
        this.rows = parsedRows
      } catch (e) {
        this.$emit('close')
        if (e.message) {
          this.$errorModal.open(e.message)
        } else {
          this.$errorModal.open('<b class="text-h6">Unable to load table list from the provided server</b>')
        }
      } finally {
        this.rowsLoading = false
      }
    },
  },
  methods: {
    selectItem(item) {
      this.$emit('selectItem', item)
      this.$emit('close')
    },
    getRowProps({ item }) {
      if (item.tableName === this.selectedAttributeValue) {
        return { class: 'bg-green-lighten-4' }
      }
      return {}
    }
  }
}
</script>
