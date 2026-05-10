<template>
  <v-dialog
    v-model="opened"
    persistent
    fullscreen
  >
    <v-card>
      <v-toolbar color="primary">
        <v-toolbar-title>Diagram</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-toolbar-items>
          <v-btn
            icon
            @click="$emit('close')"
          >
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-toolbar-items>
      </v-toolbar>
      <div class="diagram-view">
        <svg class="diagram-links" :viewBox="viewBox">
          <line
            v-for="link in linkLines"
            :key="link.id"
            :x1="link.x1"
            :y1="link.y1"
            :x2="link.x2"
            :y2="link.y2"
          />
        </svg>
        <article
          v-for="node in nodes"
          :key="node.id"
          class="diagram-node"
          :style="getNodeStyle(node)"
        >
          <header>{{ node.title }}</header>
          <section
            v-for="hier in getHierarchyList(node.data)"
            :key="`${node.id}-${hier.getAttribute('name') || 'hierarchy'}`"
            class="diagram-hierarchy"
          >
            <strong>Hierarchy {{ hier.getAttribute('name') || 'with no name' }}</strong>
            <span>{{ hier.getAttribute('primaryKey') }}</span>
          </section>
        </article>
        <aside class="diagram-legend">
          <div
            v-for="color in colorsDescriptions"
            :key="color[0]"
            class="diagram-legend-item"
          >
            <span :style="{ backgroundColor: color[1] }"></span>
            <span>{{ color[0] }}</span>
          </div>
        </aside>
      </div>
    </v-card>
  </v-dialog>
</template>

<script>
import xmlDescriptionMixin from '../../mixins/xmlDescriptionMixin'

const colors = {
  Dimension: '#657ED4',
  VirtualCubeDimension: '#1E555C',
  DimensionUsage: '#550C18',
  Cube: '#0D0106',
  VirtualCube: '#3A2E39',
  Hierarchy: '#2BA84A',
  'Fact Table': '#95190C',
}

export default {
  props: {
    cube: {
      type: Element,
      required: true,
    },
  },
  mixins: [
    xmlDescriptionMixin
  ],
  data: () => ({
    opened: true,
    nodes: [],
    links: {},
    colors,
    colorsDescriptions: Object.entries(colors),
  }),
  computed: {
    viewBox() {
      return `0 0 ${this.canvasSize.width} ${this.canvasSize.height}`
    },
    canvasSize() {
      const width = Math.max(...this.nodes.map(node => node.coordinates.x + node.size.width + 80), 800)
      const height = Math.max(...this.nodes.map(node => node.coordinates.y + node.size.height + 80), 600)
      return { width, height }
    },
    linkLines() {
      return Object.values(this.links).map((link) => {
        const start = this.nodes.find(node => node.id === link.start_id)
        const end = this.nodes.find(node => node.id === link.end_id)
        const fromLeft = start.coordinates.x < end.coordinates.x

        return {
          id: link.id,
          x1: start.coordinates.x + (fromLeft ? start.size.width : 0),
          y1: start.coordinates.y + (start.size.height / 2),
          x2: end.coordinates.x + (fromLeft ? 0 : end.size.width),
          y2: end.coordinates.y + (end.size.height / 2),
        }
      })
    },
  },
  mounted() {
    this.parseSchema()
  },
  methods: {
    parseSchema() {
      const possibleElements = this.getElementsOfType("CubeDimension").filter(e => !e.abstract)
      const items = Array.from(this.cube.querySelectorAll(`:scope > ${possibleElements.join(', :scope >')}`))

      const foreignKeys = items.reduce((acc, e) => {
        const key = e.getAttribute('foreignKey') || 'Fact Table'
        const storedVal = acc.find(e => e.key === key);
        if (storedVal) {
          storedVal.count = storedVal.count + 1;
        } else {
          acc.push({
            key,
            count: 1
          })
        }
        return acc
      }, []).sort((a, b) => b.count - a.count)

      this.nodes = []
      this.links = {}
      this.nodes.push({
        id: 'cube',
        title: `${this.cube.tagName}: ${ this.cube.getAttribute('name') }`,
        size: {
          width: 240,
          height: 60 + 16 * foreignKeys.length
        },
        coordinates: {
          x: 280,
          y: 160
        },
        data: this.cube,
        portsOut: {},
        portsIn: {}
      })

      let leftSide = 0;
      let rightSide = 0;
      let leftSideHeirarchies = 0;
      let rightSideHeirarchies = 0;
      let onLeftSide = true;
      for (let i = 0; i < foreignKeys.length; i++) {
        const el = foreignKeys[i];
        if (onLeftSide) {
          this.nodes[0].portsIn[`${el.key}`] = el.key
          leftSide += el.count
          leftSideHeirarchies++;

          if (leftSide > rightSide) onLeftSide = !onLeftSide;
        } else {
          this.nodes[0].portsOut[`${el.key}`] = el.key
          rightSide += el.count
          rightSideHeirarchies++;

          if (rightSide > leftSide) onLeftSide = !onLeftSide;
        }
      }

      this.nodes[0].size.height = 60 + 18 * Math.max(rightSideHeirarchies, leftSideHeirarchies);

      items.sort((a, b) => {
        const aFK = foreignKeys.find((e) => e.key === (a.getAttribute('foreignKey') || 'Fact Table'))
        const bFK = foreignKeys.find((e) => e.key === (b.getAttribute('foreignKey') || 'Fact Table'))

        const aIndex = foreignKeys.indexOf(aFK)
        const bIndex = foreignKeys.indexOf(bFK)

        return aIndex - bIndex;
      })

      let leftSideHeight = 0;
      let rightSideHeight = 0;
      items.forEach((e, i) => {
        const elForeignKey = e.getAttribute('foreignKey') || 'Fact Table'
        const hierarchies = this.getHierarchyList(e);
        let height = 54 + 48 * hierarchies.length;

        if (this.nodes[0].portsIn[elForeignKey]) {
          this.nodes.push({
            id: `dimension-${i}`,
            title: `${e.tagName}: ${ e.getAttribute('name') }`,
            size: {
              width: 210,
              height: height,
            },
            coordinates: {
              x: 24,
              y: 24 + leftSideHeight
            },
            portsOut: {
              [elForeignKey]: ''
            },
            data: e
          })

          this.links[`link-${i}`] = {
            id: `link-${i}`,
            start_id: `dimension-${i}`,
            start_port: elForeignKey,
            end_id: 'cube',
            end_port: elForeignKey
          }

          leftSideHeight += height + 24;
        } else if (this.nodes[0].portsOut[elForeignKey]) {
          this.nodes.push({
            id: `dimension-${i}`,
            title: `${e.tagName}: ${ e.getAttribute('name') }`,
            size: {
              width: 210,
              height: height,
            },
            coordinates: {
              x: 570,
              y: 24 + rightSideHeight
            },
            portsIn: {
              [elForeignKey]: ''
            },
            data: e
          })

          this.links[`link-${i}`] = {
            id: `link-${i}`,
            start_id: `cube`,
            start_port: elForeignKey,
            end_id: `dimension-${i}`,
            end_port: elForeignKey
          }

          rightSideHeight += height + 24;
        }
      });
    },
    getNodeStyle(node) {
      return {
        backgroundColor: this.nodeColor(node),
        left: `${node.coordinates.x}px`,
        top: `${node.coordinates.y}px`,
        width: `${node.size.width}px`,
        minHeight: `${node.size.height}px`,
      }
    },
    nodeColor(node) {
      const nodeType = node.data.tagName
      switch (nodeType) {
        case 'Dimension':
          return colors['Dimension'];
        case 'VirtualCubeDimension':
          return colors['VirtualCubeDimension'];
        case 'DimensionUsage':
          return colors['DimensionUsage'];
        case 'Cube':
          return colors['Cube'];
        case 'VirtualCube':
          return colors['VirtualCube'];
        default:
          return colors['Cube'];
      }
    },
    getHierarchyList(e) {
      const possibleElements = this.getElementsOfType("Hierarchy").filter(e => !e.abstract)
      const hierarchies = Array.from(e.querySelectorAll(`:scope > ${possibleElements.join(', :scope >')}`))
      return hierarchies
    },
    close() {
      this.opened = false
    }
  }
}
</script>

<style scoped>
.diagram-view {
  min-height: calc(100vh - 64px);
  overflow: auto;
  position: relative;
}

.diagram-links {
  height: 100%;
  min-height: calc(100vh - 64px);
  min-width: 900px;
  position: absolute;
  width: 100%;
}

.diagram-links line {
  stroke: rgba(13, 1, 6, 0.34);
  stroke-width: 2;
}

.diagram-node {
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.18);
  color: #fff;
  overflow: hidden;
  padding: 12px;
  position: absolute;
}

.diagram-node header {
  font-weight: 700;
  margin-bottom: 10px;
}

.diagram-hierarchy {
  background: rgba(43, 168, 74, 0.92);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  font-size: 12px;
  margin-top: 8px;
  padding: 8px;
}

.diagram-hierarchy span {
  font-weight: 700;
}

.diagram-legend {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.18);
  border-radius: 10px;
  bottom: 20px;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.12);
  padding: 16px;
  position: fixed;
  right: 20px;
}

.diagram-legend-item {
  align-items: center;
  display: flex;
  gap: 10px;
  padding: 4px;
}

.diagram-legend-item span:first-child {
  display: inline-block;
  height: 15px;
  width: 15px;
}
</style>
