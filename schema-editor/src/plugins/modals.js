import { createApp } from 'vue'
import store from '../store'
import vuetify from './vuetify'
import ConfirmationModal from '../components/Modals/ConfirmationModal.vue'
import ErrorModal from '../components/Modals/ErrorModal.vue'
import SuccessModal from '../components/Modals/SuccessModal.vue'
import CatalogSelectionModal from '../components/Modals/CatalogSelectionModal.vue'
import OpenSchemaModal from '../components/Modals/OpenSchemaModal.vue'
import DeleteConfirmationModal from '../components/Modals/DeleteConfirmationModal.vue'
import ServerSelectionModal from '../components/Modals/ServerSelectionModal.vue'
import SchemaValidationModal from '../components/Modals/SchemaValidationModal.vue'
import DiagramModal from '../components/Modals/DiagramModal.vue'
import XmlViewerModal from '../components/Modals/XmlViewerModal/XmlViewerModal.vue'
import PasteModal from '../components/Modals/PasteModal/PasteModal.vue'
import LoadingModal from '../components/Modals/LoadingModal.vue'

const DESTROY_DELAY_MS = 500

function mountModal(rootApp, component, props = {}, listeners = {}) {
  const container = document.createElement('div')
  document.body.appendChild(container)

  const modalApp = createApp(component, {
    ...props,
    ...listeners,
  })

  modalApp.use(store)
  modalApp.use(vuetify)
  Object.assign(modalApp.config.globalProperties, rootApp.config.globalProperties)

  const instance = modalApp.mount(container)
  const unmount = () => {
    setTimeout(() => {
      modalApp.unmount()
      container.remove()
    }, DESTROY_DELAY_MS)
  }

  return { instance, unmount }
}

function closeAndUnmount(mounted) {
  mounted.instance.close()
  mounted.unmount()
}

function register(rootApp, name, api) {
  rootApp.config.globalProperties[name] = api
}

const Modals = {
  install(app) {
    register(app, '$confirmationModal', {
      open(text) {
        return new Promise((resolve) => {
          const mounted = mountModal(app, ConfirmationModal, { text }, {
            onCancel: () => {
              resolve({ confirmed: false })
              closeAndUnmount(mounted)
            },
            onContinue: () => {
              resolve({ confirmed: true })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$successModal', {
      open(message) {
        const mounted = mountModal(app, SuccessModal, { message }, {
          onClose: () => closeAndUnmount(mounted),
        })
      },
    })

    register(app, '$errorModal', {
      open(message) {
        const mounted = mountModal(app, ErrorModal, { message }, {
          onClose: () => closeAndUnmount(mounted),
        })
      },
    })

    register(app, '$catalogSelectionModal', {
      open(serverAddress) {
        return new Promise((resolve) => {
          const mounted = mountModal(app, CatalogSelectionModal, { serverAddress }, {
            onCancel: () => {
              resolve({ status: 'cancelled', catalog: null })
              closeAndUnmount(mounted)
            },
            onSelectCatalog: (catalog) => {
              resolve({ status: 'success', catalog })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$openSchemaModal', {
      open() {
        return new Promise((resolve) => {
          const mounted = mountModal(app, OpenSchemaModal, {}, {
            onClose: () => {
              resolve({ status: 'cancelled', mode: null })
              closeAndUnmount(mounted)
            },
            onOpenFromServer: (serverAddress) => {
              resolve({ status: 'success', mode: 'server', serverAddress })
              closeAndUnmount(mounted)
            },
            onOpenFromLocal: (schemaFile) => {
              resolve({ status: 'success', mode: 'local', schemaFile })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$deleteConfirmationModal', {
      open() {
        return new Promise((resolve) => {
          const mounted = mountModal(app, DeleteConfirmationModal, {}, {
            onClose: () => {
              resolve({ confirmed: false })
              closeAndUnmount(mounted)
            },
            onConfirm: () => {
              resolve({ confirmed: true })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$serverSelectionModal', {
      open() {
        return new Promise((resolve) => {
          const mounted = mountModal(app, ServerSelectionModal, {}, {
            onClose: () => {
              resolve({ status: 'cancelled' })
              closeAndUnmount(mounted)
            },
            onSaveToServer: (serverUrl) => {
              resolve({ status: 'success', serverUrl })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$schemaValidationModal', {
      open(xmlDoc, errorList) {
        return new Promise((resolve) => {
          const mounted = mountModal(app, SchemaValidationModal, {
            xmlDoc,
            errorListProvided: errorList,
          }, {
            onClose: (state) => {
              resolve(state)
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })

    register(app, '$diagramModal', {
      open(cube) {
        const mounted = mountModal(app, DiagramModal, { cube }, {
          onClose: () => closeAndUnmount(mounted),
        })
      },
    })

    register(app, '$loadingModal', {
      open() {
        const mounted = mountModal(app, LoadingModal)
        const close = mounted.instance.close
        mounted.instance.close = () => {
          close.call(mounted.instance)
          mounted.unmount()
        }
        return mounted.instance
      },
    })

    register(app, '$xmlViewerModal', {
      open(element, onSave) {
        const mounted = mountModal(app, XmlViewerModal, { element }, {
          onClose: () => closeAndUnmount(mounted),
          onSaveElement: (xml) => onSave(xml),
        })
      },
    })

    register(app, '$pasteModal', {
      open() {
        return new Promise((resolve) => {
          const mounted = mountModal(app, PasteModal, {}, {
            onClose: () => {
              resolve({ status: 'cancelled', xml: null })
              closeAndUnmount(mounted)
            },
            onPaste: (xml) => {
              resolve({ status: 'success', xml })
              closeAndUnmount(mounted)
            },
          })
        })
      },
    })
  },
}

export default Modals
