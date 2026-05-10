<template>
  <v-row class="flex-wrap ma-4">
    <v-col 
      cols=12
      md=10
      lg=8
      offset-md=1
      offset-lg=2
      offset=0
    >
      <h2>Edit DataSource</h2>
    </v-col>
    <v-col 
      cols=12
      md=10
      lg=8
      offset-md=1
      offset-lg=2
      offset=0
    >
      <v-card width="100%">
        <v-card-text>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="dataSourceName"
              label="DataSource name"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setDataSourceName"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="dataSourceDescription"
              label="DataSource description"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setDataSourceDescription"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="url"
              label="Url"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setUrl"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="dataSourceInfo"
              label="DataSource info"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setDataSourceInfo"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="providerName"
              label="Provider name"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setProviderName"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="providerType"
              label="Provider type"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setProviderType"
            />
          </v-col>
          <v-col
            cols="12"
          >
            <v-text-field
              :model-value="authenticationMode"
              label="Authentication mode"
              :rules="rules.required"
              density="compact"
              clearable
              @update:model-value="setAuthenticationMode"
            />
          </v-col>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script>
export default {
  props: {
    element: {
      type: Element,
      required: true
    },
  },
  data() {
    const textFrom = (tagName) => this.element.querySelector(tagName)?.textContent || '';
    const dataSourceName = textFrom('DataSourceName');
    const dataSourceDescription = textFrom('DataSourceDescription');
    const url = textFrom('URL');
    const dataSourceInfo = textFrom('DataSourceInfo');
    const providerName = textFrom('ProviderName');
    const providerType = textFrom('ProviderType');
    const authenticationMode = textFrom('AuthenticationMode');

    return {
      dataSourceName,
      dataSourceDescription,
      url,
      dataSourceInfo,
      providerName,
      providerType,
      authenticationMode,
      rules: {
        required: [
          value => !!value || 'Required.',
        ],
      },
    }
  },
  methods: {
    setDataSourceName(value) {
      this.dataSourceName = value
      if (value) {
        let dataSourceName = this.element.querySelector('DataSourceName');
        if (!dataSourceName) {
          dataSourceName = document.createElementNS(null, 'DataSourceName')
          this.element.appendChild(dataSourceName)
        }
        dataSourceName.textContent = value;
      } else {
        let dataSourceName = this.element.querySelector('DataSourceName');
        if (dataSourceName) this.element.removeChild(dataSourceName)
      }
      this.$emit('updateModel');
    },
    setDataSourceDescription(value) {
      this.dataSourceDescription = value
      if (value) {
        let dataSourceDescription = this.element.querySelector('DataSourceDescription');
        if (!dataSourceDescription) {
          dataSourceDescription = document.createElementNS(null, 'DataSourceDescription')
          this.element.appendChild(dataSourceDescription)
        }
        dataSourceDescription.textContent = value;
      } else {
        let dataSourceDescription = this.element.querySelector('DataSourceDescription');
        if (dataSourceDescription) this.element.removeChild(dataSourceDescription)
      }
      this.$emit('updateModel');
    },
    setUrl(value) {
      this.url = value
      if (value) {
        let url = this.element.querySelector('URL');
        if (!url) {
          url = document.createElementNS(null, 'URL')
          this.element.appendChild(url)
        }
        url.textContent = value;
      } else {
        let url = this.element.querySelector('URL');
        if (url) this.element.removeChild(url)
      }
      this.$emit('updateModel');
    },
    setDataSourceInfo(value) {
      this.dataSourceInfo = value
      let dataSourceInfo = this.element.querySelector('DataSourceInfo');
      if (!dataSourceInfo) {
        dataSourceInfo = document.createElementNS(null, 'DataSourceInfo')
        this.element.appendChild(dataSourceInfo)
      }
      dataSourceInfo.textContent = value || '';
      this.$emit('updateModel');
    },
    setProviderName(value) {
      this.providerName = value
      if (value) {
        let providerName = this.element.querySelector('ProviderName');
        if (!providerName) {
          providerName = document.createElementNS(null, 'ProviderName')
          this.element.appendChild(providerName)
        }
        providerName.textContent = value;
      } else {
        let providerName = this.element.querySelector('ProviderName');
        if (providerName) this.element.removeChild(providerName)
      }
      this.$emit('updateModel');
    },
    setProviderType(value) {
      this.providerType = value
      if (value) {
        let providerType = this.element.querySelector('ProviderType');
        if (!providerType) {
          providerType = document.createElementNS(null, 'ProviderType')
          this.element.appendChild(providerType)
        }
        providerType.textContent = value;
      } else {
        let providerType = this.element.querySelector('ProviderType');
        if (providerType) this.element.removeChild(providerType)
      }
      this.$emit('updateModel');
    },
    setAuthenticationMode(value) {
      this.authenticationMode = value
      if (value) {
        let authenticationMode = this.element.querySelector('AuthenticationMode');
        if (!authenticationMode) {
          authenticationMode = document.createElementNS(null, 'AuthenticationMode')
          this.element.appendChild(authenticationMode)
        }
        authenticationMode.textContent = value;
      } else {
        let authenticationMode = this.element.querySelector('AuthenticationMode');
        if (authenticationMode) this.element.removeChild(authenticationMode)
      }
      this.$emit('updateModel');
    },
  }
}
</script>
