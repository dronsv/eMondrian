import DiscoverCatalogs from './XmlRequests/DiscoverCatalogs.xml?raw'
import DiscoverMetadata from './XmlRequests/DiscoverSchema.xml?raw'
import SaveMetadata from './XmlRequests/SaveSchema.xml?raw'
import DiscoverDatabases from './XmlRequests/DiscoverDatabases.xml?raw'
import SaveDatabase from './XmlRequests/SaveDatabase.xml?raw'
import DiscoverSourceTables from './XmlRequests/DiscoverSourceTables.xml?raw'
import { xmlEscape } from '../utils/xmlContent'

function postXml(url, data) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url, true)
    xhr.setRequestHeader('Content-type', 'text/xml')
    xhr.onload = function () {
      resolve(xhr.responseXML)
    }
    xhr.onerror = function (event) {
      reject({ progressEvent: event, request: xhr })
    }
    xhr.send(data)
  })
}

export function fetchCatalogList(url) {
  return postXml(url, DiscoverCatalogs)
}

export function fetchSchemaForCatalog(url, catalog) {
  const data = DiscoverMetadata.replace(/{{ DatabaseID }}/gm, xmlEscape(catalog))
  return postXml(url, data)
}

export function saveSchemaToCatalog(url, catalog, schema) {
  const data = SaveMetadata.replace(/{{ DatabaseID }}/gm, xmlEscape(catalog)).replace(/{{ Schema }}/gm, schema)
  return postXml(url, data)
}

export function fetchTableList(url) {
  return postXml(url, DiscoverSourceTables)
}

export function fetchDatabasesList(url) {
  return postXml(url, DiscoverDatabases)
}

export function saveDatabase(url, database) {
  const data = SaveDatabase.replace(/{{ Database }}/gm, database)
  return postXml(url, data)
}
