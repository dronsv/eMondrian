<template>
  <div>
    <v-list-item
      @click.stop.prevent="openItem"
    >
      <template #prepend>
        <v-icon v-text="'mdi-database'"></v-icon>
      </template>
      <v-list-item-title v-text="name"></v-list-item-title>
      <template #append>
        <!-- <v-tooltip bottom>
          <template v-slot:activator="{ props }">
            <v-btn
              icon
              v-bind="props"
              @click.stop.prevent="copyItem"
            >
              <v-icon
                v-text="'mdi-content-copy'"
              ></v-icon>
            </v-btn>
          </template>
          <span>Copy</span>
        </v-tooltip> -->

        <!-- <v-tooltip bottom>
          <template v-slot:activator="{ props }">
            <v-btn
              icon
              v-bind="props"
              @click.stop.prevent="duplicateItem"
            >
              <v-icon
                v-text="'mdi-content-duplicate'"
              ></v-icon>
            </v-btn>
          </template>
          <span>Duplicate</span>
        </v-tooltip> -->

        <v-tooltip bottom>
          <template v-slot:activator="{ props }">
            <v-btn
              icon
              v-bind="props"
              @click.stop.prevent="deleteItem"
            >
              <v-icon
                class="text-red-lighten-2"
                v-text="'mdi-delete'"
              ></v-icon>
            </v-btn>
          </template>
          <span>Delete</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ props }">
            <v-btn
              icon
              v-bind="props"
              @click.stop.prevent="opened=!opened"
            >
              <v-icon
                v-text="'mdi-chevron-down'"
                :class="{
                  'openIcon': true,
                  'openIcon__opened': opened
                }"
              ></v-icon>
            </v-btn>
          </template>
          <span>{{ opened ? 'Collapse' : 'Expand' }}</span>
        </v-tooltip>
      </template>
    </v-list-item>
    <div v-if="opened" class="element_tree_item">
      <div style="margin-bottom: 2rem;">
        <v-row
          class="align-center py-1 text-black"
        >
          <v-col cols=8 class="capitalize">
            Catalogs
          </v-col>
          <v-spacer />
          <v-col cols=1>
            <v-tooltip
              bottom
            >
              <template v-slot:activator="{ props }">
                <v-btn
                  icon
                  v-bind="props"
                  @click="pasteItem"
                >
                  <v-icon>mdi-content-paste</v-icon>
                </v-btn>
              </template>
              <span>Paste</span>
            </v-tooltip>
          </v-col>
          <v-col cols=2>
            <v-tooltip
              bottom
            >
              <template v-slot:activator="{ props }">
                <v-btn
                  icon
                  v-bind="props"
                  @click="addNewItem"
                >
                  <v-icon>mdi-plus</v-icon>
                </v-btn>
              </template>
              <span>Add</span>
            </v-tooltip>
          </v-col>
        </v-row>
        <draggable
          v-model="catalogs"
          :item-key="getDraggableKey"
          @end="dragEnd"
        >
          <template #item="{ element: item, index: idx }">
            <catalog-list-item
              :element="item"
              :timestamp="timestamp"
              :key-prop="`Catalog_${idx}__${keyProp}`"
              @openItem="$emit('openItem', $event)"
              @removeItem="$emit('removeItem', $event)"
            />
          </template>
        </draggable>
      </div>
    </div>
  </div>
</template>

<script>
import CatalogListItem from './CatalogListItem.vue'
import draggable from 'vuedraggable'

export default {
  components: {
    CatalogListItem,
    draggable,
  },
  props: {
    element: {
      type: Element,
      required: true
    },
    timestamp: {
      type: Number,
      required: true,
    },
    keyProp: {
      type: String,
      required: true,
    }
  },
  data() {
    return {
      opened: false,
    }
  },
  computed: {
    name() {
      this.timestamp
      return this.element.querySelector('DataSourceName') ? this.element.querySelector('DataSourceName').innerHTML : ''
    },
    catalogs: {
      get() {
        this.timestamp
        return Array.from(this.element.querySelectorAll('Catalogs > Catalog'))
      },
      set() {
      }
    },
    // iconName() {
    //   this.timestamp
    //   if (this.element.tagName === 'Cube') {
    //     return 'mdi-cube';
    //   }
    //   return 'mdi-xml';
    // },
  },
  methods: {
    dragEnd({ oldIndex, newIndex }) {
      const items = Array.from(this.element.querySelectorAll('Catalogs > Catalog'))

      items[oldIndex].parentNode.removeChild(items[oldIndex]);
      if (oldIndex > newIndex) {
        items[newIndex].insertAdjacentElement('beforeBegin', items[oldIndex])
      } else {
        items[newIndex].insertAdjacentElement('afterEnd', items[oldIndex])
      }

      this.$emit('updateModel');
      this.$emit('openItem',  { element: null, key: null })
    },
    addNewItem() {
      const newEl = document.createElementNS(null, "Catalog");
      const catalogArray = this.element.querySelector('Catalogs');
      catalogArray.appendChild(newEl);

      this.$emit('updateModel');
    },
    openItem() {
      this.$emit('openItem',  { element: this.element, key: this.keyProp })
    },
    getDraggableKey(element) {
      const index = Array.from(element.parentNode?.children || []).indexOf(element)
      return `${element.tagName}-${element.getAttribute('name') || 'unnamed'}-${index}`
    },
    deleteItem() {
      this.$emit('removeItem', this.element)
    },
    async pasteItem() {
      const { status, xml } = await this.$pasteModal.open()
      if (status !== 'success') return;
      const parser = new DOMParser()
      const item = parser.parseFromString(xml, "text/xml")
      const elementToPaste = item.documentElement
      const possibleToPaste = elementToPaste.tagName === 'Catalog'
      if (!possibleToPaste) {
        this.$errorModal.open(`<b class="text-h6">Item from your clipboard can't be pasted here</b>`)
        return
      }

      if (this.catalogs.length) {
        this.catalogs[this.catalogs.length - 1].insertAdjacentElement('afterend', elementToPaste)

        const itemIndex = Array.from(elementToPaste.parentNode.children).indexOf(elementToPaste)
        const prevItem = elementToPaste.parentNode.children[itemIndex - 1]
        const prevItemNodeIndex = Array.from(elementToPaste.parentNode.childNodes).indexOf(prevItem)
        const prevItemSeparator = elementToPaste.parentNode.childNodes[prevItemNodeIndex - 1]
        let newtext = '\n'
        if (prevItemSeparator.nodeType === 3) {
          newtext = prevItemSeparator.textContent
        }

        elementToPaste.insertAdjacentHTML('beforebegin', newtext)
      } 

      const keyProp = `Catalog_${this.catalogs.length - 1}__${this.keyProp}`
      
      this.$emit('updateModel');
      this.$emit('openItem',  { element: elementToPaste, key: keyProp })
    },
  }
}
</script>

<style scoped>
.openIcon {
  transition: transform 0.5 ease-in-out;
}

.openIcon__opened {
  transform: rotate(-180deg);
}

.capitalize {
  text-transform: capitalize;
}

.empty-object {
  color: #BDBDBD;
}

.element_tree_item {
  padding-left: 1.5rem;
}

.element_has_errors {
  color: red !important;
}


.element_has_errors_in_child {
  border-left: 3px solid red;
  border-radius: 0 !important;
}
</style>

<style>
.object_has_errors .empty-object {
  color: rgba(255, 0, 0, 0.4) !important;
}

.object_has_errors {
  border-left: 3px solid red;
  border-radius: 0 !important;
}
</style>
