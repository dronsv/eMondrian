package emondrian;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lightweight metadata API backed by the same datasources/schema config as XMLA.
 */
public class MetadataServlet extends HttpServlet {
    private final MetadataService metadataService = new MetadataService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        MetadataService.MetadataSnapshot snapshot = metadataService.load(getServletContext());
        String path = req.getPathInfo();

        if (path == null || path.isEmpty() || "/".equals(path)) {
            writeSummary(resp, snapshot);
            return;
        }
        if ("/version".equals(path)) {
            writeVersion(resp, snapshot);
            return;
        }
        if ("/catalogs".equals(path)) {
            writeCatalogs(resp, snapshot);
            return;
        }
        if ("/cubes".equals(path)) {
            writeCubes(req, resp, snapshot);
            return;
        }
        if ("/dimensions".equals(path)) {
            writeDimensions(req, resp, snapshot);
            return;
        }

        writeError(resp, HttpServletResponse.SC_NOT_FOUND, "not_found",
            "Unknown endpoint: " + path);
    }

    private static void writeSummary(
        HttpServletResponse resp,
        MetadataService.MetadataSnapshot snapshot) throws IOException
    {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        json.append("\"ok\":true");
        json.append(",\"version\":\"").append(escapeJson(snapshot.version)).append('"');
        appendStringArray(json, ",\"catalogs\":", snapshot.catalogNames());
        appendStringArray(json, ",\"warnings\":", snapshot.warnings);
        json.append('}');
        writeJson(resp, HttpServletResponse.SC_OK, json.toString());
    }

    private static void writeVersion(
        HttpServletResponse resp,
        MetadataService.MetadataSnapshot snapshot) throws IOException
    {
        StringBuilder json = new StringBuilder(96);
        json.append('{');
        json.append("\"version\":\"").append(escapeJson(snapshot.version)).append('"');
        appendStringArray(json, ",\"warnings\":", snapshot.warnings);
        json.append('}');
        writeJson(resp, HttpServletResponse.SC_OK, json.toString());
    }

    private static void writeCatalogs(
        HttpServletResponse resp,
        MetadataService.MetadataSnapshot snapshot) throws IOException
    {
        StringBuilder json = new StringBuilder(128);
        json.append('{');
        json.append("\"version\":\"").append(escapeJson(snapshot.version)).append('"');
        appendStringArray(json, ",\"catalogs\":", snapshot.catalogNames());
        appendStringArray(json, ",\"warnings\":", snapshot.warnings);
        json.append('}');
        writeJson(resp, HttpServletResponse.SC_OK, json.toString());
    }

    private static void writeCubes(
        HttpServletRequest req,
        HttpServletResponse resp,
        MetadataService.MetadataSnapshot snapshot) throws IOException
    {
        String requestedCatalog = trimToNull(req.getParameter("catalog"));
        if (requestedCatalog == null) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                "missing_catalog", "Query parameter 'catalog' is required");
            return;
        }

        MetadataService.CatalogSnapshot catalog = snapshot.findCatalogIgnoreCase(requestedCatalog);
        if (catalog == null) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND,
                "catalog_not_found", "Catalog not found: " + requestedCatalog);
            return;
        }

        StringBuilder json = new StringBuilder(192);
        json.append('{');
        json.append("\"version\":\"").append(escapeJson(snapshot.version)).append('"');
        json.append(",\"data_source\":\"").append(escapeJson(catalog.dataSourceName)).append('"');
        json.append(",\"catalog\":\"").append(escapeJson(catalog.catalogName)).append('"');
        appendStringArray(json, ",\"cubes\":", catalog.cubes);
        appendStringArray(json, ",\"warnings\":", snapshot.warnings);
        json.append('}');
        writeJson(resp, HttpServletResponse.SC_OK, json.toString());
    }

    private static void writeDimensions(
        HttpServletRequest req,
        HttpServletResponse resp,
        MetadataService.MetadataSnapshot snapshot) throws IOException
    {
        String requestedCatalog = trimToNull(req.getParameter("catalog"));
        String requestedCube = trimToNull(req.getParameter("cube"));
        if (requestedCatalog == null || requestedCube == null) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                "missing_parameters",
                "Query parameters 'catalog' and 'cube' are required");
            return;
        }

        MetadataService.CatalogSnapshot catalog = snapshot.findCatalogIgnoreCase(requestedCatalog);
        if (catalog == null) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND,
                "catalog_not_found", "Catalog not found: " + requestedCatalog);
            return;
        }
        List<String> dimensions = catalog.findDimensionsIgnoreCase(requestedCube);
        if (dimensions == null) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND,
                "cube_not_found", "Cube not found in catalog '" + catalog.catalogName + "': "
                    + requestedCube);
            return;
        }

        String cubeName = requestedCube;
        for (String existingCubeName : catalog.cubeDimensions.keySet()) {
            if (existingCubeName.equalsIgnoreCase(requestedCube)) {
                cubeName = existingCubeName;
                break;
            }
        }

        StringBuilder json = new StringBuilder(224);
        json.append('{');
        json.append("\"version\":\"").append(escapeJson(snapshot.version)).append('"');
        json.append(",\"data_source\":\"").append(escapeJson(catalog.dataSourceName)).append('"');
        json.append(",\"catalog\":\"").append(escapeJson(catalog.catalogName)).append('"');
        json.append(",\"cube\":\"").append(escapeJson(cubeName)).append('"');
        appendStringArray(json, ",\"dimensions\":", dimensions);
        appendStringArray(json, ",\"warnings\":", snapshot.warnings);
        json.append('}');
        writeJson(resp, HttpServletResponse.SC_OK, json.toString());
    }

    private static void writeError(
        HttpServletResponse resp,
        int status,
        String code,
        String message) throws IOException
    {
        String payload = "{\"error\":\"" + escapeJson(code) + "\",\"message\":\""
            + escapeJson(message) + "\"}";
        writeJson(resp, status, payload);
    }

    private static void writeJson(HttpServletResponse resp, int status, String payload)
        throws IOException
    {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        resp.setStatus(status);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    private static void appendStringArray(StringBuilder out, String fieldName, List<String> values) {
        out.append(fieldName).append('[');
        if (values != null) {
            boolean first = true;
            for (String value : values) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append('"').append(escapeJson(value)).append('"');
            }
        }
        out.append(']');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }
}

