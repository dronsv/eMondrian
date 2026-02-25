package emondrian;

import mondrian.rolap.sql.dependency.DependencyRegistry;
import mondrian.rolap.sql.dependency.SchemaDependencyValidationReport;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SchemaValidationService {
    private static final Pattern IDENTIFIER_PATTERN =
        Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern DEPENDS_ON_REF_PATTERN =
        Pattern.compile("property:([A-Za-z_][A-Za-z0-9_]*)");
    private static final String DEPENDS_ON_ANNOTATION_NAME = "drilldown.dependsOn";
    private final MondrianSchemaDependencyValidationAdapter dependencyValidationAdapter =
        new MondrianSchemaDependencyValidationAdapter(this);

    public ValidationResult validateDirectory(File schemaDir, boolean failOnWarn) {
        ValidationResult result = new ValidationResult(failOnWarn);
        if (schemaDir == null || !schemaDir.exists() || !schemaDir.isDirectory()) {
            result.addWarn(
                "SCHEMA_DIR_NOT_FOUND",
                "Schema directory not found: " + (schemaDir == null ? "<null>" : schemaDir.getAbsolutePath()),
                null,
                null,
                "Set EMONDRIAN_SCHEMA_DIR to a valid directory."
            );
            return result;
        }

        File[] files = schemaDir.listFiles();
        if (files == null) {
            result.addWarn(
                "SCHEMA_DIR_READ_ERROR",
                "Schema directory cannot be read: " + schemaDir.getAbsolutePath(),
                null,
                null,
                "Check file permissions."
            );
            return result;
        }

        int xmlCount = 0;
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".xml")) {
                continue;
            }
            xmlCount++;
            try {
                ValidationResult fileResult = validateSchemaFile(file, failOnWarn);
                result.merge(fileResult);
            } catch (Exception ex) {
                result.addFatal(
                    "SCHEMA_PARSE_ERROR",
                    "Failed to parse schema file: " + file.getAbsolutePath() + " (" + ex.getMessage() + ")",
                    file.getName(),
                    null,
                    "Fix XML syntax and Mondrian structure in this schema file."
                );
            }
        }

        if (xmlCount == 0) {
            result.addWarn(
                "SCHEMA_FILES_NOT_FOUND",
                "No .xml schema files found in: " + schemaDir.getAbsolutePath(),
                null,
                null,
                "Mount at least one schema file into WEB-INF/schema."
            );
        }

        return result;
    }

    public ValidationResult validateSchemaXml(String schemaXml, String schemaName, boolean failOnWarn) {
        ValidationResult result = new ValidationResult(failOnWarn);
        if (isBlank(schemaXml)) {
            result.addFatal(
                "EMPTY_SCHEMA_PAYLOAD",
                "Schema payload is empty.",
                schemaName,
                null,
                "Send a Mondrian schema XML document in request body."
            );
            return result;
        }

        try {
            Document document = parseXml(
                new ByteArrayInputStream(schemaXml.getBytes(StandardCharsets.UTF_8)));
            return validateDocument(document, safeSchemaName(schemaName), failOnWarn);
        } catch (Exception ex) {
            result.addFatal(
                "SCHEMA_PARSE_ERROR",
                "Failed to parse schema payload: " + ex.getMessage(),
                safeSchemaName(schemaName),
                null,
                "Ensure request body contains valid Mondrian schema XML."
            );
            return result;
        }
    }

    public SchemaDependencyValidationReport validateDirectoryAsDependencyReport(
        File schemaDir,
        boolean failOnWarn)
    {
        return dependencyValidationAdapter.validateDirectory(schemaDir, failOnWarn);
    }

    public SchemaDependencyValidationReport validateSchemaXmlAsDependencyReport(
        String schemaXml,
        String schemaName,
        boolean failOnWarn)
    {
        return dependencyValidationAdapter.validateSchemaXml(
            schemaXml, schemaName, failOnWarn);
    }

    private ValidationResult validateSchemaFile(File schemaFile, boolean failOnWarn) throws Exception {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(schemaFile);
            Document document = parseXml(inputStream);
            return validateDocument(document, schemaFile.getName(), failOnWarn);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private ValidationResult validateDocument(Document document, String schemaName, boolean failOnWarn) {
        ValidationResult result = new ValidationResult(failOnWarn);
        final boolean hasTimeDimension = hasTimeDimension(document);

        NodeList levelNodes = document.getElementsByTagName("Level");
        final Map<String, Integer> schemaLevelNameCounts =
            collectSchemaLevelNameCounts(levelNodes);
        Map<String, Integer> levelColumnCounts = new LinkedHashMap<String, Integer>();
        boolean hasAnyNameExpression = false;

        for (int i = 0; i < levelNodes.getLength(); i++) {
            Node node = levelNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element level = (Element) node;
            String levelName = safeLevelName(level.getAttribute("name"));
            String levelColumn = trimToNull(level.getAttribute("column"));
            if (levelColumn != null) {
                Integer count = levelColumnCounts.get(levelColumn);
                levelColumnCounts.put(levelColumn, count == null ? 1 : count + 1);
            }

            boolean hasNameColumn = !isBlank(level.getAttribute("nameColumn"));
            NodeList nameExpressionNodes = level.getElementsByTagName("NameExpression");
            boolean hasNameExpression = nameExpressionNodes.getLength() > 0;
            if (hasNameExpression) {
                hasAnyNameExpression = true;
            }

            if (hasNameExpression && hasNameColumn) {
                result.addWarn(
                    "LEVEL_HAS_NAMECOLUMN_AND_NAMEEXPRESSION",
                    "Level has both nameColumn and NameExpression; prefer one caption source.",
                    schemaName,
                    levelName,
                    "Use nameColumn for direct columns or fully-qualified SQL in NameExpression."
                );
            }

            for (int ne = 0; ne < nameExpressionNodes.getLength(); ne++) {
                Node nameExpressionNode = nameExpressionNodes.item(ne);
                if (!(nameExpressionNode instanceof Element)) {
                    continue;
                }
                Element nameExpression = (Element) nameExpressionNode;
                NodeList sqlNodes = nameExpression.getElementsByTagName("SQL");
                for (int s = 0; s < sqlNodes.getLength(); s++) {
                    Node sqlNode = sqlNodes.item(s);
                    if (!(sqlNode instanceof Element)) {
                        continue;
                    }
                    Element sql = (Element) sqlNode;
                    String dialect = trimToNull(sql.getAttribute("dialect"));
                    if (dialect == null || !"generic".equalsIgnoreCase(dialect)) {
                        continue;
                    }

                    String expression = normalized(sql.getTextContent());
                    if (isBlank(expression)) {
                        continue;
                    }

                    if (IDENTIFIER_PATTERN.matcher(expression).matches()) {
                        result.addFatal(
                            "UNQUALIFIED_GENERIC_NAME_EXPRESSION",
                            "Generic NameExpression uses bare identifier '" + expression + "'.",
                            schemaName,
                            levelName,
                            "Use level@nameColumn or fully-qualified SQL expression."
                        );
                    } else if (expression.indexOf('.') < 0 && expression.indexOf('(') < 0) {
                        result.addWarn(
                            "WEAKLY_QUALIFIED_GENERIC_NAME_EXPRESSION",
                            "Generic NameExpression may be weakly qualified: " + expression,
                            schemaName,
                            levelName,
                            "Prefer fully-qualified columns to avoid ambiguous SQL."
                        );
                    }
                }
            }

            Map<String, Boolean> propertyDepends = new LinkedHashMap<String, Boolean>();
            int dependsOnLevelValueCount = 0;
            NodeList propertyNodes = level.getElementsByTagName("Property");
            for (int p = 0; p < propertyNodes.getLength(); p++) {
                Node propertyNode = propertyNodes.item(p);
                if (!(propertyNode instanceof Element)) {
                    continue;
                }
                Element property = (Element) propertyNode;
                String propertyName = trimToNull(property.getAttribute("name"));
                if (propertyName == null) {
                    continue;
                }
                boolean dependsOnLevelValue = isTrue(property.getAttribute("dependsOnLevelValue"));
                propertyDepends.put(propertyName, dependsOnLevelValue);
                if (dependsOnLevelValue) {
                    dependsOnLevelValueCount++;
                }
            }

            String dependsOnText = collectDependsOnText(level);
            boolean hasDependsOn = dependsOnText != null;
            if (hasDependsOn) {
                List<ParsedDependsOnRule> parsedRules = parseDependsOnRules(dependsOnText);
                validateDependsOnRuleSyntaxAndSafety(
                    parsedRules,
                    hasTimeDimension,
                    schemaLevelNameCounts,
                    result,
                    schemaName,
                    levelName);
                LinkedHashSet<String> referencedProperties = extractDependsOnReferences(dependsOnText);
                if (referencedProperties.isEmpty()) {
                    result.addWarn(
                        "DEPENDS_ON_WITHOUT_PROPERTY_REFS",
                        "drilldown.dependsOn has no property:... references.",
                        schemaName,
                        levelName,
                        "Use values like property:SomeProperty in drilldown.dependsOn."
                    );
                }
                for (String propertyName : referencedProperties) {
                    Boolean dependsOnLevelValue = propertyDepends.get(propertyName);
                    if (dependsOnLevelValue == null) {
                        result.addFatal(
                            "DEPENDS_ON_MISSING_PROPERTY",
                            "drilldown.dependsOn references missing Property '" + propertyName + "'.",
                            schemaName,
                            levelName,
                            "Declare Property name=\"" + propertyName
                                + "\" dependsOnLevelValue=\"true\" on the same level."
                        );
                    } else if (!dependsOnLevelValue.booleanValue()) {
                        result.addFatal(
                            "DEPENDS_ON_PROPERTY_FLAG_MISSING",
                            "Property '" + propertyName + "' must declare dependsOnLevelValue=\"true\".",
                            schemaName,
                            levelName,
                            "Set dependsOnLevelValue=\"true\" for this Property."
                        );
                    }
                }
            } else if (dependsOnLevelValueCount > 0) {
                result.addWarn(
                    "PROPERTY_FLAG_WITHOUT_DEPENDS_ON",
                    "Level has Property dependsOnLevelValue=\"true\" but no drilldown.dependsOn annotation.",
                    schemaName,
                    levelName,
                    "Add drilldown.dependsOn annotation or remove the dependsOnLevelValue flag."
                );
            }
        }

        if (hasAnyNameExpression) {
            for (Map.Entry<String, Integer> entry : levelColumnCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    result.addWarn(
                        "DUPLICATE_LEVEL_COLUMNS_WITH_NAME_EXPRESSION",
                        "Schema has repeated level column '" + entry.getKey() + "' (" + entry.getValue()
                            + " occurrences) while NameExpression is used.",
                        schemaName,
                        null,
                        "Prefer nameColumn and avoid ambiguous caption expressions on joined dimensions."
                    );
                }
            }
        }

        return result;
    }

    private static Document parseXml(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();
        return document;
    }

    private static String collectDependsOnText(Element level) {
        NodeList annotationNodes = level.getElementsByTagName("Annotation");
        StringBuilder out = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < annotationNodes.getLength(); i++) {
            Node annotationNode = annotationNodes.item(i);
            if (!(annotationNode instanceof Element)) {
                continue;
            }
            Element annotation = (Element) annotationNode;
            String annotationName = trimToNull(annotation.getAttribute("name"));
            if (!DEPENDS_ON_ANNOTATION_NAME.equals(annotationName)) {
                continue;
            }
            found = true;
            out.append(' ').append(normalized(annotation.getTextContent()));
        }
        return found ? out.toString() : null;
    }

    private static LinkedHashSet<String> extractDependsOnReferences(String text) {
        LinkedHashSet<String> refs = new LinkedHashSet<String>();
        Matcher matcher = DEPENDS_ON_REF_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    private static List<ParsedDependsOnRule> parseDependsOnRules(String text) {
        if (isBlank(text)) {
            return Collections.emptyList();
        }
        String[] rawTokens = text.split("[;,]");
        List<ParsedDependsOnRule> result = new ArrayList<ParsedDependsOnRule>(rawTokens.length);
        for (String rawToken : rawTokens) {
            ParsedDependsOnRule rule = parseDependsOnRule(rawToken);
            if (rule != null) {
                result.add(rule);
            }
        }
        return result;
    }

    private static ParsedDependsOnRule parseDependsOnRule(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String trimmedToken = rawToken.trim();
        if (trimmedToken.isEmpty()) {
            return null;
        }

        String[] segments = trimmedToken.split("\\|");
        if (segments.length == 0) {
            return null;
        }
        String determinantRef = segments[0].trim();
        if (determinantRef.isEmpty()) {
            return ParsedDependsOnRule.error(
                "Dependency rule is missing determinant level reference: " + trimmedToken);
        }

        String mappingType = "ancestor";
        String mappingProperty = null;
        boolean mappingSpecified = false;
        boolean requiresTimeFilter = false;

        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i] == null ? "" : segments[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            String lower = segment.toLowerCase();

            if (!mappingSpecified
                && ("ancestor".equals(lower)
                    || lower.startsWith("property:")
                    || lower.startsWith("property=")))
            {
                mappingSpecified = true;
                if ("ancestor".equals(lower)) {
                    mappingType = "ancestor";
                    mappingProperty = null;
                    continue;
                }
                String propertyName = segment.substring(9).trim();
                if (propertyName.isEmpty()) {
                    return ParsedDependsOnRule.error(
                        "Property dependency rule is missing property name: " + trimmedToken);
                }
                mappingType = "property";
                mappingProperty = propertyName;
                continue;
            }

            if ("requirestimefilter".equals(lower)
                || "requirestimefilter=true".equals(lower)
                || "requirestimefilter=false".equals(lower))
            {
                requiresTimeFilter = !"requirestimefilter=false".equals(lower);
                continue;
            }

            if (!mappingSpecified) {
                return ParsedDependsOnRule.error(
                    "Unsupported dependency mapping '" + segment + "' in rule: " + trimmedToken);
            }
            return ParsedDependsOnRule.error(
                "Unsupported dependency option '" + segment + "' in rule: " + trimmedToken);
        }

        return ParsedDependsOnRule.ok(
            determinantRef,
            mappingType,
            mappingProperty,
            requiresTimeFilter);
    }

    private static void validateDependsOnRuleSyntaxAndSafety(
        List<ParsedDependsOnRule> rules,
        boolean hasTimeDimension,
        Map<String, Integer> schemaLevelNameCounts,
        ValidationResult result,
        String schemaName,
        String levelName)
    {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Map<String, ParsedDependsOnRule> seenValidatedByDeterminant =
            new LinkedHashMap<String, ParsedDependsOnRule>();
        for (ParsedDependsOnRule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (rule.parseError != null) {
                result.addWarn(
                    DependencyRegistry.DependencyIssueCodes.INVALID_DEPENDENCY_RULE_SYNTAX,
                    rule.parseError,
                    schemaName,
                    levelName,
                    "Use [Level Unique Name]|ancestor or |property:PropertyName|requiresTimeFilter."
                );
                continue;
            }
            validateDeterminantRef(
                rule,
                schemaLevelNameCounts,
                result,
                schemaName,
                levelName);
            if (rule.requiresTimeFilter && !hasTimeDimension) {
                result.addWarn(
                    DependencyRegistry.DependencyIssueCodes
                        .REQUIRES_TIME_FILTER_WITHOUT_TIME_DIMENSION,
                    "Dependency rule requires time filter but schema has no Time dimension.",
                    schemaName,
                    levelName,
                    "Remove requiresTimeFilter or define a Time dimension in this schema."
                );
            }
            ParsedDependsOnRule previous = seenValidatedByDeterminant.get(rule.determinantRef);
            if (previous == null) {
                seenValidatedByDeterminant.put(rule.determinantRef, rule);
                continue;
            }
            boolean duplicate = previous.sameSemantics(rule);
            result.addWarn(
                duplicate
                    ? DependencyRegistry.DependencyIssueCodes
                        .DUPLICATE_VALIDATED_DEPENDENCY_RULE
                    : DependencyRegistry.DependencyIssueCodes
                        .CONFLICTING_VALIDATED_DEPENDENCY_RULE,
                duplicate
                    ? "Duplicate dependency rule for determinant level '" + rule.determinantRef + "'."
                    : "Conflicting dependency rules for determinant level '" + rule.determinantRef
                        + "'. First rule should win.",
                schemaName,
                levelName,
                duplicate
                    ? "Remove duplicate rule."
                    : "Keep a single explicit rule per determinant level."
            );
        }
    }

    private static void validateDeterminantRef(
        ParsedDependsOnRule rule,
        Map<String, Integer> schemaLevelNameCounts,
        ValidationResult result,
        String schemaName,
        String levelName)
    {
        if (rule == null || isBlank(rule.determinantRef)) {
            return;
        }
        if (looksBracketedUniqueName(rule.determinantRef)) {
            return;
        }
        int count = 0;
        if (schemaLevelNameCounts != null) {
            Integer value = schemaLevelNameCounts.get(rule.determinantRef);
            count = value == null ? 0 : value.intValue();
        }
        if (count == 0) {
            result.addWarn(
                DependencyRegistry.DependencyIssueCodes.UNKNOWN_DEPENDENCY_LEVEL_REF,
                "Dependency rule references unknown level '" + rule.determinantRef + "'.",
                schemaName,
                levelName,
                "Use an existing level name or full level unique name."
            );
        } else if (count > 1) {
            result.addWarn(
                DependencyRegistry.DependencyIssueCodes.AMBIGUOUS_DEPENDENCY_LEVEL_REF,
                "Dependency rule references level name '" + rule.determinantRef
                    + "' that matches multiple levels.",
                schemaName,
                levelName,
                "Use determinant level unique name in drilldown.dependsOn."
            );
        } else {
            result.addInfo(
                DependencyRegistry.DependencyIssueCodes.UNQUALIFIED_DEPENDENCY_LEVEL_REF,
                "Dependency rule references level by name '" + rule.determinantRef + "'.",
                schemaName,
                levelName,
                "Use determinant level unique name in drilldown.dependsOn."
            );
        }
    }

    private static boolean looksBracketedUniqueName(String text) {
        if (isBlank(text)) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("[") && trimmed.indexOf("].[") >= 0;
    }

    private static Map<String, Integer> collectSchemaLevelNameCounts(NodeList levelNodes) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        if (levelNodes == null) {
            return counts;
        }
        for (int i = 0; i < levelNodes.getLength(); i++) {
            Node node = levelNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element level = (Element) node;
            String name = trimToNull(level.getAttribute("name"));
            if (name == null) {
                continue;
            }
            Integer prev = counts.get(name);
            counts.put(name, prev == null ? 1 : prev + 1);
        }
        return counts;
    }

    private static boolean hasTimeDimension(Document document) {
        if (document == null) {
            return false;
        }
        NodeList dimensionNodes = document.getElementsByTagName("Dimension");
        for (int i = 0; i < dimensionNodes.getLength(); i++) {
            Node node = dimensionNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            String type = trimToNull(element.getAttribute("type"));
            if (type != null && "timedimension".equals(type.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized)
            || "true".equals(normalized)
            || "yes".equals(normalized)
            || "on".equals(normalized);
    }

    private static String normalized(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeSchemaName(String schemaName) {
        return isBlank(schemaName) ? "inline-schema.xml" : schemaName.trim();
    }

    private static String safeLevelName(String levelName) {
        return isBlank(levelName) ? "<unnamed>" : levelName.trim();
    }

    public static final class ValidationMessage {
        public final String severity;
        public final String code;
        public final String message;
        public final String schema;
        public final String level;
        public final String recommendation;

        public ValidationMessage(
            String severity,
            String code,
            String message,
            String schema,
            String level,
            String recommendation)
        {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.schema = schema;
            this.level = level;
            this.recommendation = recommendation;
        }
    }

    public static final class ValidationResult {
        private final boolean failOnWarn;
        private final List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
        private int fatalCount;
        private int warnCount;
        private int infoCount;

        public ValidationResult(boolean failOnWarn) {
            this.failOnWarn = failOnWarn;
        }

        public boolean isFailOnWarn() {
            return failOnWarn;
        }

        public List<ValidationMessage> getMessages() {
            return messages;
        }

        public int getFatalCount() {
            return fatalCount;
        }

        public int getWarnCount() {
            return warnCount;
        }

        public int getInfoCount() {
            return infoCount;
        }

        public boolean isOk() {
            return fatalCount == 0 && (!failOnWarn || warnCount == 0);
        }

        public void merge(ValidationResult other) {
            if (other == null) {
                return;
            }
            this.messages.addAll(other.messages);
            this.fatalCount += other.fatalCount;
            this.warnCount += other.warnCount;
            this.infoCount += other.infoCount;
        }

        public void addFatal(
            String code,
            String message,
            String schema,
            String level,
            String recommendation)
        {
            fatalCount++;
            messages.add(new ValidationMessage(
                "fatal",
                code,
                message,
                schema,
                level,
                recommendation));
        }

        public void addWarn(
            String code,
            String message,
            String schema,
            String level,
            String recommendation)
        {
            warnCount++;
            messages.add(new ValidationMessage(
                "warn",
                code,
                message,
                schema,
                level,
                recommendation));
        }

        public void addInfo(
            String code,
            String message,
            String schema,
            String level,
            String recommendation)
        {
            infoCount++;
            messages.add(new ValidationMessage(
                "info",
                code,
                message,
                schema,
                level,
                recommendation));
        }
    }

    private static final class ParsedDependsOnRule {
        final String determinantRef;
        final String mappingType;
        final String mappingProperty;
        final boolean requiresTimeFilter;
        final String parseError;

        private ParsedDependsOnRule(
            String determinantRef,
            String mappingType,
            String mappingProperty,
            boolean requiresTimeFilter,
            String parseError)
        {
            this.determinantRef = determinantRef;
            this.mappingType = mappingType;
            this.mappingProperty = mappingProperty;
            this.requiresTimeFilter = requiresTimeFilter;
            this.parseError = parseError;
        }

        static ParsedDependsOnRule ok(
            String determinantRef,
            String mappingType,
            String mappingProperty,
            boolean requiresTimeFilter)
        {
            return new ParsedDependsOnRule(
                determinantRef,
                mappingType,
                mappingProperty,
                requiresTimeFilter,
                null);
        }

        static ParsedDependsOnRule error(String parseError) {
            return new ParsedDependsOnRule(null, null, null, false, parseError);
        }

        boolean sameSemantics(ParsedDependsOnRule other) {
            if (other == null) {
                return false;
            }
            if (!safeEquals(determinantRef, other.determinantRef)) {
                return false;
            }
            if (!safeEquals(mappingType, other.mappingType)) {
                return false;
            }
            if (!safeEquals(mappingProperty, other.mappingProperty)) {
                return false;
            }
            return requiresTimeFilter == other.requiresTimeFilter;
        }

        private static boolean safeEquals(Object left, Object right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
