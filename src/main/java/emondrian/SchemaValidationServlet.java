package emondrian;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SchemaValidationServlet extends HttpServlet {
    private static final String DEFAULT_SCHEMA_DIR =
        "/usr/local/tomcat/webapps/emondrian/WEB-INF/schema";
    private static final int MAX_REQUEST_BODY_CHARS = 8 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 32;

    private final SchemaValidationService validationService = new SchemaValidationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        if (!"/schema/validate/current".equals(servletPath)) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "not_found",
                "Use GET /schema/validate/current or POST /schema/validate.");
            return;
        }

        boolean failOnWarn = parseBool(req.getParameter("fail_on_warn"), false)
            || parseBool(req.getParameter("failOnWarn"), false);
        File schemaDir = resolveSchemaDir();
        SchemaValidationService.ValidationResult result =
            validationService.validateDirectory(schemaDir, failOnWarn);
        writeResult(resp, result);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        if (!"/schema/validate".equals(servletPath)) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "not_found",
                "Use POST /schema/validate.");
            return;
        }

        boolean failOnWarn = parseBool(req.getParameter("fail_on_warn"), false)
            || parseBool(req.getParameter("failOnWarn"), false);

        String contentType = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        String body;
        try {
            body = readBody(req, MAX_REQUEST_BODY_CHARS);
        } catch (RequestBodyTooLargeException ex) {
            writeError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "payload_too_large",
                ex.getMessage());
            return;
        }
        String schemaXml;
        if (contentType.contains("application/json")) {
            JsonSchemaPayload payload = extractSchemaXmlFromJson(body);
            if (!payload.isValid()) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_schema_payload",
                    payload.errorMessage);
                return;
            }
            schemaXml = payload.schemaXml;
        } else {
            schemaXml = body;
        }

        if (schemaXml == null || schemaXml.trim().isEmpty() || schemaXml.indexOf("<Schema") < 0) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_schema_payload",
                "Provide Mondrian schema XML via request body or JSON {\"schema_xml\":\"...\"}.");
            return;
        }

        SchemaValidationService.ValidationResult result =
            validationService.validateSchemaXml(schemaXml, "inline-schema.xml", failOnWarn);
        writeResult(resp, result);
    }

    private File resolveSchemaDir() {
        String envDir = System.getenv("EMONDRIAN_SCHEMA_DIR");
        if (envDir != null && !envDir.trim().isEmpty()) {
            return new File(envDir.trim());
        }

        String realPath = getServletContext().getRealPath("/WEB-INF/schema");
        if (realPath != null && !realPath.trim().isEmpty()) {
            return new File(realPath.trim());
        }
        return new File(DEFAULT_SCHEMA_DIR);
    }

    private static boolean parseBool(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized)
            || "true".equals(normalized)
            || "yes".equals(normalized)
            || "on".equals(normalized);
    }

    private static String readBody(HttpServletRequest req, int maxChars) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (out.length() + read > maxChars) {
                throw new RequestBodyTooLargeException(
                    "Request body exceeds " + maxChars + " characters.");
            }
            out.append(buffer, 0, read);
        }
        return out.toString();
    }

    private static JsonSchemaPayload extractSchemaXmlFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return JsonSchemaPayload.error(
                "JSON payload must contain string field schema_xml.");
        }

        JsonCursor cursor = new JsonCursor(stripBom(json));
        try {
            cursor.skipWhitespace();
            if (!cursor.consume('{')) {
                return JsonSchemaPayload.error(
                    "JSON payload must be an object with string field schema_xml.");
            }
            cursor.skipWhitespace();
            if (cursor.consume('}')) {
                return JsonSchemaPayload.error(
                    "JSON payload must contain string field schema_xml.");
            }

            String schemaXml = null;
            boolean schemaFieldSeen = false;
            while (true) {
                String key = cursor.parseString();
                cursor.skipWhitespace();
                cursor.expect(':');
                cursor.skipWhitespace();

                if ("schema_xml".equals(key)) {
                    schemaFieldSeen = true;
                    if (cursor.isAtEnd() || cursor.peek() != '"') {
                        return JsonSchemaPayload.error(
                            "Field schema_xml must be a JSON string.");
                    }
                    schemaXml = cursor.parseString();
                } else {
                    cursor.skipValue(0);
                }

                cursor.skipWhitespace();
                if (cursor.consume('}')) {
                    break;
                }
                cursor.expect(',');
                cursor.skipWhitespace();
            }

            cursor.skipWhitespace();
            if (!cursor.isAtEnd()) {
                return JsonSchemaPayload.error(
                    "Invalid JSON payload: trailing characters after root object.");
            }
            if (!schemaFieldSeen) {
                return JsonSchemaPayload.error(
                    "JSON payload must contain string field schema_xml.");
            }
            return JsonSchemaPayload.ok(schemaXml);
        } catch (JsonParseException ex) {
            return JsonSchemaPayload.error(
                "Invalid JSON payload: " + ex.getMessage());
        }
    }

    private static String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private static final class RequestBodyTooLargeException extends IOException {
        RequestBodyTooLargeException(String message) {
            super(message);
        }
    }

    private static final class JsonSchemaPayload {
        final String schemaXml;
        final String errorMessage;

        private JsonSchemaPayload(String schemaXml, String errorMessage) {
            this.schemaXml = schemaXml;
            this.errorMessage = errorMessage;
        }

        static JsonSchemaPayload ok(String schemaXml) {
            return new JsonSchemaPayload(schemaXml, null);
        }

        static JsonSchemaPayload error(String message) {
            return new JsonSchemaPayload(null, message);
        }

        boolean isValid() {
            return errorMessage == null;
        }
    }

    private static final class JsonParseException extends Exception {
        JsonParseException(String message) {
            super(message);
        }
    }

    private static final class JsonCursor {
        private final String text;
        private int index;

        JsonCursor(String text) {
            this.text = text == null ? "" : text;
            this.index = 0;
        }

        boolean isAtEnd() {
            return index >= text.length();
        }

        char peek() {
            return text.charAt(index);
        }

        boolean consume(char expected) {
            if (!isAtEnd() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        void expect(char expected) throws JsonParseException {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        void skipWhitespace() {
            while (!isAtEnd()) {
                char c = text.charAt(index);
                if (!Character.isWhitespace(c)) {
                    break;
                }
                index++;
            }
        }

        String parseString() throws JsonParseException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!isAtEnd()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw error("Invalid escape at end of string");
                    }
                    char esc = text.charAt(index++);
                    switch (esc) {
                        case '"':
                            out.append('"');
                            break;
                        case '\\':
                            out.append('\\');
                            break;
                        case '/':
                            out.append('/');
                            break;
                        case 'b':
                            out.append('\b');
                            break;
                        case 'f':
                            out.append('\f');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            out.append(parseUnicodeEscape());
                            break;
                        default:
                            throw error("Unsupported escape sequence: \\" + esc);
                    }
                    continue;
                }
                if (c < 0x20) {
                    throw error("Control character in JSON string");
                }
                out.append(c);
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicodeEscape() throws JsonParseException {
            if (index + 4 > text.length()) {
                throw error("Incomplete unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char ch = text.charAt(index++);
                int hex = Character.digit(ch, 16);
                if (hex < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) + hex;
            }
            return (char) value;
        }

        void skipValue(int depth) throws JsonParseException {
            if (depth > MAX_JSON_DEPTH) {
                throw error("JSON payload is too deeply nested");
            }
            if (isAtEnd()) {
                throw error("Unexpected end of JSON payload");
            }
            char c = peek();
            if (c == '"') {
                parseString();
                return;
            }
            if (c == '{') {
                skipObject(depth + 1);
                return;
            }
            if (c == '[') {
                skipArray(depth + 1);
                return;
            }
            if (c == 't') {
                expectKeyword("true");
                return;
            }
            if (c == 'f') {
                expectKeyword("false");
                return;
            }
            if (c == 'n') {
                expectKeyword("null");
                return;
            }
            if (c == '-' || Character.isDigit(c)) {
                skipNumber();
                return;
            }
            throw error("Unexpected token '" + c + "'");
        }

        private void skipObject(int depth) throws JsonParseException {
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                return;
            }
            while (true) {
                parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                skipValue(depth + 1);
                skipWhitespace();
                if (consume('}')) {
                    return;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private void skipArray(int depth) throws JsonParseException {
            expect('[');
            skipWhitespace();
            if (consume(']')) {
                return;
            }
            while (true) {
                skipValue(depth + 1);
                skipWhitespace();
                if (consume(']')) {
                    return;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private void expectKeyword(String keyword) throws JsonParseException {
            for (int i = 0; i < keyword.length(); i++) {
                if (isAtEnd() || text.charAt(index++) != keyword.charAt(i)) {
                    throw error("Invalid token, expected " + keyword);
                }
            }
        }

        private void skipNumber() throws JsonParseException {
            if (consume('-')) {
                // optional sign
            }
            if (consume('0')) {
                // zero literal
            } else {
                if (!consumeDigit()) {
                    throw error("Invalid number");
                }
                while (consumeDigit()) {
                    // integer part
                }
            }
            if (consume('.')) {
                if (!consumeDigit()) {
                    throw error("Invalid fractional part");
                }
                while (consumeDigit()) {
                    // fractional part
                }
            }
            if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
                index++;
                if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                    index++;
                }
                if (!consumeDigit()) {
                    throw error("Invalid exponent");
                }
                while (consumeDigit()) {
                    // exponent digits
                }
            }
        }

        private boolean consumeDigit() {
            if (!isAtEnd() && Character.isDigit(peek())) {
                index++;
                return true;
            }
            return false;
        }

        private JsonParseException error(String message) {
            return new JsonParseException(message + " at position " + index);
        }
    }

    private static void writeResult(
        HttpServletResponse resp,
        SchemaValidationService.ValidationResult result) throws IOException
    {
        int status = result.isOk() ? HttpServletResponse.SC_OK : 422;
        String payload = toJson(result);
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        resp.setStatus(status);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    private static void writeError(
        HttpServletResponse resp,
        int status,
        String code,
        String message) throws IOException
    {
        String payload = "{\"error\":\"" + escapeJson(code) + "\",\"message\":\""
            + escapeJson(message) + "\"}";
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        resp.setStatus(status);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    private static String toJson(SchemaValidationService.ValidationResult result) {
        StringBuilder out = new StringBuilder(256 + (result.getMessages().size() * 256));
        out.append('{');
        out.append("\"ok\":").append(result.isOk());
        out.append(",\"fail_on_warn\":").append(result.isFailOnWarn());
        out.append(",\"counts\":{");
        out.append("\"fatal\":").append(result.getFatalCount());
        out.append(",\"warn\":").append(result.getWarnCount());
        out.append(",\"info\":").append(result.getInfoCount());
        out.append('}');
        out.append(",\"messages\":[");

        boolean first = true;
        for (SchemaValidationService.ValidationMessage message : result.getMessages()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('{');
            out.append("\"severity\":\"").append(escapeJson(message.severity)).append('"');
            out.append(",\"code\":\"").append(escapeJson(message.code)).append('"');
            out.append(",\"message\":\"").append(escapeJson(message.message)).append('"');
            if (message.schema != null) {
                out.append(",\"schema\":\"").append(escapeJson(message.schema)).append('"');
            }
            if (message.level != null) {
                out.append(",\"level\":\"").append(escapeJson(message.level)).append('"');
            }
            if (message.recommendation != null) {
                out.append(",\"recommendation\":\"")
                    .append(escapeJson(message.recommendation)).append('"');
            }
            out.append('}');
        }
        out.append("]}");
        return out.toString();
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
