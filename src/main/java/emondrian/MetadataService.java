package emondrian;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.ServletContext;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Reads eMondrian metadata from the same datasources/schema files used by XMLA.
 */
public class MetadataService {
    private static final String DEFAULT_DATASOURCES_PATH =
        "/usr/local/tomcat/webapps/emondrian/WEB-INF/datasources.xml";
    private static final String DEFAULT_SCHEMA_DIR =
        "/usr/local/tomcat/webapps/emondrian/WEB-INF/schema";
    private static final long SNAPSHOT_TTL_MILLIS = 5000L;

    private final Object cacheLock = new Object();
    private volatile MetadataSnapshot cachedSnapshot;
    private volatile long cachedAtMillis;

    public MetadataSnapshot load(ServletContext servletContext) {
        long now = System.currentTimeMillis();
        MetadataSnapshot snapshot = cachedSnapshot;
        if (snapshot != null && (now - cachedAtMillis) < SNAPSHOT_TTL_MILLIS) {
            return snapshot;
        }

        synchronized (cacheLock) {
            now = System.currentTimeMillis();
            snapshot = cachedSnapshot;
            if (snapshot != null && (now - cachedAtMillis) < SNAPSHOT_TTL_MILLIS) {
                return snapshot;
            }
            MetadataSnapshot rebuilt = rebuildSnapshot(servletContext);
            cachedSnapshot = rebuilt;
            cachedAtMillis = now;
            return rebuilt;
        }
    }

    private MetadataSnapshot rebuildSnapshot(ServletContext servletContext) {
        List<String> warnings = new ArrayList<String>();
        LinkedHashMap<String, CatalogSnapshot> catalogs = new LinkedHashMap<String, CatalogSnapshot>();
        CRC32 crc32 = new CRC32();

        File datasourcesFile = resolveDatasourcesFile(servletContext);
        updateFingerprint(crc32, datasourcesFile);
        if (datasourcesFile == null || !datasourcesFile.exists() || !datasourcesFile.isFile()) {
            warnings.add("datasources.xml not found: "
                + (datasourcesFile == null ? "<null>" : datasourcesFile.getAbsolutePath()));
            return new MetadataSnapshot(toVersion(crc32), catalogs, warnings);
        }

        Document datasourcesDocument;
        try {
            InputStream in = new FileInputStream(datasourcesFile);
            try {
                datasourcesDocument = parseXml(in);
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            warnings.add("failed to parse datasources.xml: " + ex.getMessage());
            return new MetadataSnapshot(toVersion(crc32), catalogs, warnings);
        }

        NodeList dataSourceNodes = datasourcesDocument.getElementsByTagName("DataSource");
        for (int ds = 0; ds < dataSourceNodes.getLength(); ds++) {
            Node dsNode = dataSourceNodes.item(ds);
            if (!(dsNode instanceof Element)) {
                continue;
            }
            Element dataSourceElement = (Element) dsNode;
            String dataSourceName = trimToNull(textOfTag(dataSourceElement, "DataSourceName"));
            if (dataSourceName == null) {
                dataSourceName = "DataSource#" + (ds + 1);
            }

            NodeList catalogNodes = dataSourceElement.getElementsByTagName("Catalog");
            for (int c = 0; c < catalogNodes.getLength(); c++) {
                Node catalogNode = catalogNodes.item(c);
                if (!(catalogNode instanceof Element)) {
                    continue;
                }
                Element catalogElement = (Element) catalogNode;
                String catalogName = trimToNull(catalogElement.getAttribute("name"));
                if (catalogName == null) {
                    catalogName = trimToNull(textOfTag(catalogElement, "CatalogName"));
                }
                if (catalogName == null) {
                    warnings.add("catalog without name in data source " + dataSourceName);
                    continue;
                }

                if (catalogs.containsKey(catalogName)) {
                    warnings.add("duplicate catalog name '" + catalogName
                        + "' encountered; keeping first definition");
                    continue;
                }

                String definition = trimToNull(textOfTag(catalogElement, "Definition"));
                File schemaFile = resolveSchemaFile(definition, datasourcesFile, servletContext);
                updateFingerprint(crc32, schemaFile);
                SchemaSnapshot schemaSnapshot = parseSchema(schemaFile, catalogName, warnings);

                catalogs.put(catalogName, new CatalogSnapshot(
                    dataSourceName,
                    catalogName,
                    schemaFile == null ? null : schemaFile.getAbsolutePath(),
                    schemaSnapshot.cubes,
                    schemaSnapshot.cubeDimensions
                ));
            }
        }

        return new MetadataSnapshot(toVersion(crc32), catalogs, warnings);
    }

    private static SchemaSnapshot parseSchema(
        File schemaFile,
        String catalogName,
        List<String> warnings)
    {
        LinkedHashMap<String, List<String>> cubeDimensions =
            new LinkedHashMap<String, List<String>>();
        List<String> cubes = new ArrayList<String>();

        if (schemaFile == null) {
            warnings.add("catalog '" + catalogName + "' has no schema definition");
            return new SchemaSnapshot(cubes, cubeDimensions);
        }
        if (!schemaFile.exists() || !schemaFile.isFile()) {
            warnings.add("schema file not found for catalog '" + catalogName + "': "
                + schemaFile.getAbsolutePath());
            return new SchemaSnapshot(cubes, cubeDimensions);
        }

        Document schemaDocument;
        try {
            InputStream in = new FileInputStream(schemaFile);
            try {
                schemaDocument = parseXml(in);
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            warnings.add("failed to parse schema for catalog '" + catalogName + "': "
                + ex.getMessage());
            return new SchemaSnapshot(cubes, cubeDimensions);
        }

        NodeList cubeNodes = schemaDocument.getElementsByTagName("Cube");
        for (int i = 0; i < cubeNodes.getLength(); i++) {
            Node cubeNode = cubeNodes.item(i);
            if (!(cubeNode instanceof Element)) {
                continue;
            }
            Element cubeElement = (Element) cubeNode;
            String cubeName = trimToNull(cubeElement.getAttribute("name"));
            if (cubeName == null) {
                continue;
            }

            LinkedHashSet<String> dimensions = new LinkedHashSet<String>();
            NodeList childNodes = cubeElement.getChildNodes();
            for (int c = 0; c < childNodes.getLength(); c++) {
                Node childNode = childNodes.item(c);
                if (!(childNode instanceof Element)) {
                    continue;
                }
                Element child = (Element) childNode;
                String tagName = child.getTagName();
                if ("DimensionUsage".equals(tagName)) {
                    String source = trimToNull(child.getAttribute("source"));
                    String name = trimToNull(child.getAttribute("name"));
                    if (source != null) {
                        dimensions.add(source);
                    } else if (name != null) {
                        dimensions.add(name);
                    }
                } else if ("Dimension".equals(tagName)) {
                    String name = trimToNull(child.getAttribute("name"));
                    if (name != null) {
                        dimensions.add(name);
                    }
                }
            }

            cubes.add(cubeName);
            cubeDimensions.put(cubeName, new ArrayList<String>(dimensions));
        }

        NodeList virtualCubeNodes = schemaDocument.getElementsByTagName("VirtualCube");
        for (int i = 0; i < virtualCubeNodes.getLength(); i++) {
            Node virtualCubeNode = virtualCubeNodes.item(i);
            if (!(virtualCubeNode instanceof Element)) {
                continue;
            }
            Element virtualCubeElement = (Element) virtualCubeNode;
            String cubeName = trimToNull(virtualCubeElement.getAttribute("name"));
            if (cubeName == null) {
                continue;
            }
            LinkedHashSet<String> dimensions = new LinkedHashSet<String>();
            NodeList childNodes = virtualCubeElement.getChildNodes();
            for (int c = 0; c < childNodes.getLength(); c++) {
                Node childNode = childNodes.item(c);
                if (!(childNode instanceof Element)) {
                    continue;
                }
                Element child = (Element) childNode;
                if (!"VirtualCubeDimension".equals(child.getTagName())) {
                    continue;
                }
                String name = trimToNull(child.getAttribute("name"));
                if (name != null) {
                    dimensions.add(name);
                }
            }
            cubes.add(cubeName);
            cubeDimensions.put(cubeName, new ArrayList<String>(dimensions));
        }

        return new SchemaSnapshot(cubes, cubeDimensions);
    }

    private static Document parseXml(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(inputStream);
    }

    private static void updateFingerprint(CRC32 crc32, File file) {
        if (file == null) {
            crc32.update(0);
            return;
        }
        updateText(crc32, file.getAbsolutePath());
        updateNumber(crc32, file.exists() ? 1L : 0L);
        if (file.exists()) {
            updateNumber(crc32, file.lastModified());
            updateNumber(crc32, file.length());
        }
    }

    private static void updateText(CRC32 crc32, String text) {
        if (text == null) {
            crc32.update(0);
            return;
        }
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
    }

    private static void updateNumber(CRC32 crc32, long value) {
        crc32.update((int) (value & 0xFF));
        crc32.update((int) ((value >> 8) & 0xFF));
        crc32.update((int) ((value >> 16) & 0xFF));
        crc32.update((int) ((value >> 24) & 0xFF));
        crc32.update((int) ((value >> 32) & 0xFF));
        crc32.update((int) ((value >> 40) & 0xFF));
        crc32.update((int) ((value >> 48) & 0xFF));
        crc32.update((int) ((value >> 56) & 0xFF));
    }

    private static String toVersion(CRC32 crc32) {
        long value = crc32 == null ? 0L : crc32.getValue();
        return Long.toHexString(value).toLowerCase(Locale.ROOT);
    }

    private static File resolveDatasourcesFile(ServletContext servletContext) {
        String explicitPath = trimToNull(System.getenv("EMONDRIAN_DATASOURCES_XML"));
        if (explicitPath != null) {
            return new File(explicitPath);
        }

        String configDir = trimToNull(System.getenv("EMONDRIAN_CONFIG_DIR"));
        if (configDir != null) {
            return new File(configDir, "datasources.xml");
        }

        if (servletContext != null) {
            String webInfDataSources = servletContext.getRealPath("/WEB-INF/datasources.xml");
            if (!isBlank(webInfDataSources)) {
                return new File(webInfDataSources);
            }
        }

        return new File(DEFAULT_DATASOURCES_PATH);
    }

    private static File resolveSchemaFile(
        String definition,
        File datasourcesFile,
        ServletContext servletContext)
    {
        if (isBlank(definition)) {
            return null;
        }
        String trimmed = definition.trim();
        if (trimmed.startsWith("file:")) {
            try {
                return new File(new URI(trimmed));
            } catch (Exception ignored) {
                // fall through to other resolution strategies
            }
        }

        File webInfMapped = mapWebInfDefinition(trimmed, servletContext);
        if (webInfMapped != null && webInfMapped.exists()) {
            return webInfMapped;
        }

        File asPath = new File(trimmed);
        if (asPath.isAbsolute() && asPath.exists()) {
            return asPath;
        }

        File datasourcesRelative = null;
        if (datasourcesFile != null && datasourcesFile.getParentFile() != null) {
            datasourcesRelative = new File(datasourcesFile.getParentFile(), trimmed);
            if (datasourcesRelative.exists()) {
                return datasourcesRelative;
            }
        }

        String schemaDir = trimToNull(System.getenv("EMONDRIAN_SCHEMA_DIR"));
        if (schemaDir != null) {
            File schemaDirRelative = new File(schemaDir, trimmed);
            if (schemaDirRelative.exists()) {
                return schemaDirRelative;
            }
        }

        if (servletContext != null) {
            String webInfSchemaDir = servletContext.getRealPath("/WEB-INF/schema");
            if (!isBlank(webInfSchemaDir)) {
                File webInfRelative = new File(webInfSchemaDir, new File(trimmed).getName());
                if (webInfRelative.exists()) {
                    return webInfRelative;
                }
            }
        }

        if (webInfMapped != null) {
            return webInfMapped;
        }

        if (datasourcesRelative != null) {
            return datasourcesRelative;
        }

        File defaultSchemaDir = new File(DEFAULT_SCHEMA_DIR, new File(trimmed).getName());
        if (defaultSchemaDir.exists()) {
            return defaultSchemaDir;
        }

        return asPath;
    }

    private static File mapWebInfDefinition(String definition, ServletContext servletContext) {
        if (isBlank(definition)) {
            return null;
        }

        String normalized = definition.trim().replace('\\', '/');
        if (!normalized.startsWith("/WEB-INF/")) {
            return null;
        }

        String webInfRelative = normalized.substring("/WEB-INF/".length());
        if (isBlank(webInfRelative)) {
            return null;
        }

        if (servletContext != null) {
            String webInfDir = servletContext.getRealPath("/WEB-INF");
            if (!isBlank(webInfDir)) {
                File fromWebInf = new File(webInfDir, webInfRelative);
                if (fromWebInf.exists()) {
                    return fromWebInf;
                }
            }
        }

        String configDir = trimToNull(System.getenv("EMONDRIAN_CONFIG_DIR"));
        if (configDir != null) {
            File fromConfig = new File(configDir, webInfRelative);
            if (fromConfig.exists()) {
                return fromConfig;
            }
        }

        String schemaDir = trimToNull(System.getenv("EMONDRIAN_SCHEMA_DIR"));
        if (schemaDir != null && webInfRelative.startsWith("schema/")) {
            String schemaRelative = webInfRelative.substring("schema/".length());
            File fromSchemaDir = new File(schemaDir, schemaRelative);
            if (fromSchemaDir.exists()) {
                return fromSchemaDir;
            }
            return fromSchemaDir;
        }

        return null;
    }

    private static String textOfTag(Element parent, String tagName) {
        if (parent == null || isBlank(tagName)) {
            return null;
        }
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node == null ? null : node.getTextContent();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class SchemaSnapshot {
        final List<String> cubes;
        final Map<String, List<String>> cubeDimensions;

        SchemaSnapshot(List<String> cubes, Map<String, List<String>> cubeDimensions) {
            this.cubes = cubes == null ? Collections.<String>emptyList() : cubes;
            this.cubeDimensions = cubeDimensions == null
                ? Collections.<String, List<String>>emptyMap()
                : cubeDimensions;
        }
    }

    public static final class MetadataSnapshot {
        public final String version;
        public final Map<String, CatalogSnapshot> catalogs;
        public final List<String> warnings;

        MetadataSnapshot(
            String version,
            Map<String, CatalogSnapshot> catalogs,
            List<String> warnings)
        {
            this.version = version == null ? "0" : version;
            this.catalogs = catalogs == null
                ? Collections.<String, CatalogSnapshot>emptyMap()
                : catalogs;
            this.warnings = warnings == null ? Collections.<String>emptyList() : warnings;
        }

        public List<String> catalogNames() {
            return new ArrayList<String>(catalogs.keySet());
        }

        public CatalogSnapshot findCatalogIgnoreCase(String catalog) {
            if (catalog == null) {
                return null;
            }
            for (Map.Entry<String, CatalogSnapshot> entry : catalogs.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(catalog)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    public static final class CatalogSnapshot {
        public final String dataSourceName;
        public final String catalogName;
        public final String schemaPath;
        public final List<String> cubes;
        public final Map<String, List<String>> cubeDimensions;

        CatalogSnapshot(
            String dataSourceName,
            String catalogName,
            String schemaPath,
            List<String> cubes,
            Map<String, List<String>> cubeDimensions)
        {
            this.dataSourceName = dataSourceName;
            this.catalogName = catalogName;
            this.schemaPath = schemaPath;
            this.cubes = cubes == null ? Collections.<String>emptyList() : cubes;
            this.cubeDimensions = cubeDimensions == null
                ? Collections.<String, List<String>>emptyMap()
                : cubeDimensions;
        }

        public List<String> findDimensionsIgnoreCase(String cube) {
            if (cube == null) {
                return null;
            }
            for (Map.Entry<String, List<String>> entry : cubeDimensions.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cube)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
