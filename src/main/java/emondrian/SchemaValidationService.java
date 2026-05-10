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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SchemaValidationService {
    private static final String UNICODE_IDENTIFIER_REGEX =
        "[\\p{L}_][\\p{L}\\p{N}_]*";
    private static final Pattern IDENTIFIER_PATTERN =
        Pattern.compile("^" + UNICODE_IDENTIFIER_REGEX + "$");
    private static final String DEPENDS_ON_ANNOTATION_NAME = "drilldown.dependsOn";
    private static final String DEPENDS_ON_CHAIN_ANNOTATION_NAME = "drilldown.dependsOnChain";
    private final MondrianSchemaDependencyValidationAdapter dependencyValidationAdapter =
        new MondrianSchemaDependencyValidationAdapter(this);

    public ValidationResult validateDirectory(File schemaDir, boolean failOnWarn) {
        return validateDirectory(schemaDir, failOnWarn, Locale.getDefault());
    }

    public ValidationResult validateDirectory(
        File schemaDir,
        boolean failOnWarn,
        Locale locale)
    {
        ValidationResult result = new ValidationResult(failOnWarn);
        if (schemaDir == null || !schemaDir.exists() || !schemaDir.isDirectory()) {
            result.addWarn(
                "SCHEMA_DIR_NOT_FOUND",
                msg(
                    locale,
                    "schema.dir.not.found.message",
                    schemaDir == null ? "<null>" : schemaDir.getAbsolutePath()),
                null,
                null,
                msg(locale, "schema.dir.not.found.recommendation")
            );
            return result;
        }

        File[] files = schemaDir.listFiles();
        if (files == null) {
            result.addWarn(
                "SCHEMA_DIR_READ_ERROR",
                msg(locale, "schema.dir.read.error.message", schemaDir.getAbsolutePath()),
                null,
                null,
                msg(locale, "schema.dir.read.error.recommendation")
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
                ValidationResult fileResult = validateSchemaFile(file, failOnWarn, locale);
                result.merge(fileResult);
            } catch (Exception ex) {
                result.addFatal(
                    "SCHEMA_PARSE_ERROR",
                    msg(
                        locale,
                        "schema.parse.file.error.message",
                        file.getAbsolutePath(),
                        ex.getMessage()),
                    file.getName(),
                    null,
                    msg(locale, "schema.parse.file.error.recommendation")
                );
            }
        }

        if (xmlCount == 0) {
            result.addWarn(
                "SCHEMA_FILES_NOT_FOUND",
                msg(locale, "schema.files.not.found.message", schemaDir.getAbsolutePath()),
                null,
                null,
                msg(locale, "schema.files.not.found.recommendation")
            );
        }

        return result;
    }

    public ValidationResult validateSchemaXml(String schemaXml, String schemaName, boolean failOnWarn) {
        return validateSchemaXml(schemaXml, schemaName, failOnWarn, Locale.getDefault());
    }

    public ValidationResult validateSchemaXml(
        String schemaXml,
        String schemaName,
        boolean failOnWarn,
        Locale locale)
    {
        ValidationResult result = new ValidationResult(failOnWarn);
        if (isBlank(schemaXml)) {
            result.addFatal(
                "EMPTY_SCHEMA_PAYLOAD",
                msg(locale, "schema.payload.empty.message"),
                schemaName,
                null,
                msg(locale, "schema.payload.empty.recommendation")
            );
            return result;
        }

        try {
            Document document = parseXml(
                new ByteArrayInputStream(schemaXml.getBytes(StandardCharsets.UTF_8)));
            return validateDocument(document, safeSchemaName(schemaName), failOnWarn, locale);
        } catch (Exception ex) {
            result.addFatal(
                "SCHEMA_PARSE_ERROR",
                msg(locale, "schema.parse.payload.error.message", ex.getMessage()),
                safeSchemaName(schemaName),
                null,
                msg(locale, "schema.parse.payload.error.recommendation")
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

    private ValidationResult validateSchemaFile(
        File schemaFile,
        boolean failOnWarn,
        Locale locale) throws Exception
    {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(schemaFile);
            Document document = parseXml(inputStream);
            return validateDocument(document, schemaFile.getName(), failOnWarn, locale);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private ValidationResult validateDocument(
        Document document,
        String schemaName,
        boolean failOnWarn,
        Locale locale)
    {
        ValidationResult result = new ValidationResult(failOnWarn);
        final boolean hasTimeDimension = hasTimeDimension(document);

        NodeList levelNodes = document.getElementsByTagName("Level");
        final Map<String, Integer> schemaLevelNameCounts =
            collectSchemaLevelNameCounts(levelNodes);
        final Map<String, String> schemaLevelRefs =
            collectSchemaLevelRefs(document);
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
                    msg(locale, "level.namecolumn.nameexpression.both.message"),
                    schemaName,
                    levelName,
                    msg(locale, "level.namecolumn.nameexpression.both.recommendation")
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
                            msg(locale, "nameexpression.generic.unqualified.message", expression),
                            schemaName,
                            levelName,
                            msg(locale, "nameexpression.generic.unqualified.recommendation")
                        );
                    } else if (expression.indexOf('.') < 0 && expression.indexOf('(') < 0) {
                        result.addWarn(
                            "WEAKLY_QUALIFIED_GENERIC_NAME_EXPRESSION",
                            msg(locale, "nameexpression.generic.weakly.qualified.message", expression),
                            schemaName,
                            levelName,
                            msg(locale, "nameexpression.generic.weakly.qualified.recommendation")
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

            String dependsOnText = collectAnnotationText(level, DEPENDS_ON_ANNOTATION_NAME);
            String dependsOnChainText = collectAnnotationText(level, DEPENDS_ON_CHAIN_ANNOTATION_NAME);
            boolean hasDependsOn = dependsOnText != null || dependsOnChainText != null;
            if (hasDependsOn) {
                List<ParsedDependsOnRule> parsedRules = new ArrayList<ParsedDependsOnRule>();
                parsedRules.addAll(parseDependsOnRules(dependsOnText));
                parsedRules.addAll(parseDependsOnChainRules(dependsOnChainText));
                validateDependsOnRuleSyntaxAndSafety(
                    parsedRules,
                    hasTimeDimension,
                    schemaLevelNameCounts,
                    result,
                    schemaName,
                    levelName,
                    locale);
                LinkedHashSet<String> referencedProperties =
                    extractDependsOnReferences(parsedRules);
                if (referencedProperties.isEmpty()
                    && !hasAncestorDependsOnRule(parsedRules)
                    && !hasInferredPropertyDependsOnRule(parsedRules)) {
                    result.addWarn(
                        "DEPENDS_ON_WITHOUT_PROPERTY_REFS",
                        msg(locale, "dependson.without.property.refs.message"),
                        schemaName,
                        levelName,
                        msg(locale, "dependson.without.property.refs.recommendation")
                    );
                }
                for (String propertyName : referencedProperties) {
                    Boolean dependsOnLevelValue = propertyDepends.get(propertyName);
                    if (dependsOnLevelValue == null) {
                        result.addFatal(
                            "DEPENDS_ON_MISSING_PROPERTY",
                            msg(locale, "dependson.missing.property.message", propertyName),
                            schemaName,
                            levelName,
                            msg(locale, "dependson.missing.property.recommendation", propertyName)
                        );
                    } else if (!dependsOnLevelValue.booleanValue()) {
                        // Warn, not fatal: dependsOnLevelValue=false still works
                        // for chain ordering, and avoids multi-column level
                        // blocking agg table routing in SqlTupleReader.
                        result.addWarn(
                            "DEPENDS_ON_PROPERTY_FLAG_MISSING",
                            msg(locale, "dependson.property.flag.missing.message", propertyName),
                            schemaName,
                            levelName,
                            msg(locale, "dependson.property.flag.missing.recommendation")
                        );
                    }
                }
            } else if (dependsOnLevelValueCount > 0) {
                result.addWarn(
                    "PROPERTY_FLAG_WITHOUT_DEPENDS_ON",
                    msg(locale, "property.flag.without.dependson.message"),
                    schemaName,
                    levelName,
                    msg(locale, "property.flag.without.dependson.recommendation")
                );
            }
        }

        if (hasAnyNameExpression) {
            for (Map.Entry<String, Integer> entry : levelColumnCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    result.addWarn(
                        "DUPLICATE_LEVEL_COLUMNS_WITH_NAME_EXPRESSION",
                        msg(
                            locale,
                            "schema.duplicate.level.columns.with.nameexpression.message",
                            entry.getKey(),
                            entry.getValue()),
                        schemaName,
                        null,
                        msg(
                            locale,
                            "schema.duplicate.level.columns.with.nameexpression.recommendation")
                    );
                }
            }
        }

        validateAggregateLevelCoverage(
            document,
            schemaName,
            locale,
            result,
            schemaLevelRefs);

        return result;
    }

    private void validateAggregateLevelCoverage(
        Document document,
        String schemaName,
        Locale locale,
        ValidationResult result,
        Map<String, String> schemaLevelRefs)
    {
        if (document == null) {
            return;
        }
        final NodeList aggNameNodes = document.getElementsByTagName("AggName");
        for (int i = 0; i < aggNameNodes.getLength(); i++) {
            final Node node = aggNameNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element aggName = (Element) node;
            final String aggTableName =
                trimToNull(aggName.getAttribute("name")) == null
                    ? "<unnamed-agg>"
                    : trimToNull(aggName.getAttribute("name"));
            final Map<String, List<String>> levelRefsByColumn =
                new LinkedHashMap<String, List<String>>();
            final Map<String, LinkedHashSet<String>> hierarchyRefsByColumn =
                new HashMap<String, LinkedHashSet<String>>();
            final NodeList aggLevelNodes = aggName.getElementsByTagName("AggLevel");
            for (int j = 0; j < aggLevelNodes.getLength(); j++) {
                final Node aggLevelNode = aggLevelNodes.item(j);
                if (!(aggLevelNode instanceof Element)) {
                    continue;
                }
                final Element aggLevel = (Element) aggLevelNode;
                final String levelRef = trimToNull(aggLevel.getAttribute("name"));
                final String column = trimToNull(aggLevel.getAttribute("column"));
                if (levelRef == null || column == null) {
                    continue;
                }
                if (!schemaLevelRefs.containsKey(levelRef)) {
                    result.addWarn(
                        "AGG_LEVEL_UNKNOWN_LEVEL_REF",
                        msg(
                            locale,
                            "agg.level.unknown.ref.message",
                            levelRef,
                            aggTableName),
                        schemaName,
                        levelRef,
                        msg(locale, "agg.level.unknown.ref.recommendation"));
                }

                List<String> refs = levelRefsByColumn.get(column);
                if (refs == null) {
                    refs = new ArrayList<String>();
                    levelRefsByColumn.put(column, refs);
                }
                refs.add(levelRef);

                LinkedHashSet<String> hierarchyRefs = hierarchyRefsByColumn.get(column);
                if (hierarchyRefs == null) {
                    hierarchyRefs = new LinkedHashSet<String>();
                    hierarchyRefsByColumn.put(column, hierarchyRefs);
                }
                hierarchyRefs.add(extractHierarchyRef(levelRef));
            }

            for (Map.Entry<String, List<String>> entry : levelRefsByColumn.entrySet()) {
                final LinkedHashSet<String> distinctRefs =
                    new LinkedHashSet<String>(entry.getValue());
                if (distinctRefs.size() > 1) {
                    result.addWarn(
                        "AGG_DUPLICATE_COLUMN_LEVEL_REFS",
                        msg(
                            locale,
                            "agg.column.duplicate.level.refs.message",
                            aggTableName,
                            entry.getKey(),
                            distinctRefs.size(),
                            joinList(distinctRefs)),
                        schemaName,
                        null,
                        msg(locale, "agg.column.duplicate.level.refs.recommendation"));
                }
                final LinkedHashSet<String> hierarchyRefs =
                    hierarchyRefsByColumn.get(entry.getKey());
                if (hierarchyRefs != null && hierarchyRefs.size() > 1) {
                    result.addWarn(
                        "AGG_FLAT_HIERARCHY_COLUMN_REUSE",
                        msg(
                            locale,
                            "agg.column.flat.hierarchy.reuse.message",
                            aggTableName,
                            entry.getKey(),
                            joinList(hierarchyRefs)),
                        schemaName,
                        null,
                        msg(locale, "agg.column.flat.hierarchy.reuse.recommendation"));
                }
            }
        }
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

    private static String collectAnnotationText(Element level, String annotationNameToCollect) {
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
            if (!safeEquals(annotationNameToCollect, annotationName)) {
                continue;
            }
            found = true;
            out.append(' ').append(normalized(annotation.getTextContent()));
        }
        return found ? out.toString() : null;
    }

    private static String collectDependsOnText(Element level) {
        return collectAnnotationText(level, DEPENDS_ON_ANNOTATION_NAME);
    }

    private static LinkedHashSet<String> extractDependsOnReferences(
        List<ParsedDependsOnRule> rules)
    {
        LinkedHashSet<String> refs = new LinkedHashSet<String>();
        if (rules == null || rules.isEmpty()) {
            return refs;
        }
        for (ParsedDependsOnRule rule : rules) {
            if (rule == null || rule.parseError != null) {
                continue;
            }
            if (!"property".equals(rule.mappingType)) {
                continue;
            }
            String propertyName = trimToNull(rule.mappingProperty);
            if (propertyName != null) {
                refs.add(propertyName);
            }
        }
        return refs;
    }

    private static boolean hasAncestorDependsOnRule(List<ParsedDependsOnRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (ParsedDependsOnRule rule : rules) {
            if (rule == null || rule.parseError != null) {
                continue;
            }
            if ("ancestor".equals(rule.mappingType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInferredPropertyDependsOnRule(List<ParsedDependsOnRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (ParsedDependsOnRule rule : rules) {
            if (rule == null || rule.parseError != null) {
                continue;
            }
            if ("property".equals(rule.mappingType) && rule.propertyInferenceRequested) {
                return true;
            }
        }
        return false;
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

    private static List<ParsedDependsOnRule> parseDependsOnChainRules(String text) {
        if (isBlank(text)) {
            return Collections.emptyList();
        }
        String[] optionSplit = text.split("\\|");
        if (optionSplit.length == 0) {
            return Collections.emptyList();
        }
        String chainBody = optionSplit[0] == null ? "" : optionSplit[0].trim();
        if (chainBody.isEmpty()) {
            return Collections.<ParsedDependsOnRule>singletonList(
                ParsedDependsOnRule.error("Dependency chain is empty: " + text));
        }

        boolean requiresTimeFilter = false;
        for (int i = 1; i < optionSplit.length; i++) {
            String option = optionSplit[i] == null ? "" : optionSplit[i].trim();
            if (option.isEmpty()) {
                continue;
            }
            String lower = option.toLowerCase();
            if ("requirestimefilter".equals(lower)
                || "requirestimefilter=true".equals(lower)
                || "requirestimefilter=false".equals(lower))
            {
                requiresTimeFilter = !"requirestimefilter=false".equals(lower);
                continue;
            }
            return Collections.<ParsedDependsOnRule>singletonList(
                ParsedDependsOnRule.error(
                    "Unsupported dependency chain option '" + option + "' in rule: " + text));
        }

        String[] rawSteps = chainBody.split(">");
        List<ParsedDependsOnChainStep> steps =
            new ArrayList<ParsedDependsOnChainStep>(rawSteps.length);
        for (String rawStep : rawSteps) {
            ParsedDependsOnChainStep rule =
                parseDependsOnChainStep(rawStep, requiresTimeFilter, text);
            if (rule != null) {
                steps.add(rule);
            }
        }
        return expandDependsOnChainTransitively(steps, text);
    }

    private static List<ParsedDependsOnRule> expandDependsOnChainTransitively(
        List<ParsedDependsOnChainStep> steps,
        String fullText)
    {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParsedDependsOnRule> result = new ArrayList<ParsedDependsOnRule>(steps.size());
        java.util.Set<String> seenDeterminants = new java.util.LinkedHashSet<String>();
        for (ParsedDependsOnChainStep step : steps) {
            if (step == null) {
                continue;
            }
            if (step.parseError != null) {
                result.add(ParsedDependsOnRule.error(step.parseError));
                continue;
            }
            if (!seenDeterminants.add(step.determinantRef)) {
                result.add(ParsedDependsOnRule.error(
                    "Duplicate determinant level '" + step.determinantRef
                        + "' in dependency chain: " + fullText));
                continue;
            }
            // Runtime consumes pair-wise rules. Chain DSL compiles to a canonical
            // transitive pair set in chain order.
            result.add(ParsedDependsOnRule.ok(
                step.determinantRef,
                "property",
                step.mappingProperty,
                step.requiresTimeFilter,
                step.propertyInferenceRequested));
        }
        return result;
    }

    private static ParsedDependsOnChainStep parseDependsOnChainStep(
        String rawStep,
        boolean requiresTimeFilter,
        String fullText)
    {
        if (rawStep == null) {
            return null;
        }
        String step = rawStep.trim();
        if (step.isEmpty()) {
            return null;
        }
        int eq = step.indexOf('=');
        String determinantRef;
        String propertyName;
        boolean inferProperty;
        if (eq < 0) {
            determinantRef = step;
            propertyName = null;
            inferProperty = true;
        } else {
            if (eq == 0 || eq >= step.length() - 1) {
                return ParsedDependsOnChainStep.error(
                    "Invalid dependency chain step '" + step + "' in rule: " + fullText
                        + ". Use [Level Unique Name] or [Level Unique Name]=property_name");
            }
            determinantRef = step.substring(0, eq).trim();
            propertyName = step.substring(eq + 1).trim();
            inferProperty = false;
        }
        if (isBlank(determinantRef) || (!inferProperty && isBlank(propertyName))) {
            return ParsedDependsOnChainStep.error(
                "Invalid dependency chain step '" + step + "' in rule: " + fullText
                    + ". Use [Level Unique Name] or [Level Unique Name]=property_name");
        }
        return ParsedDependsOnChainStep.ok(
            determinantRef,
            propertyName,
            inferProperty,
            requiresTimeFilter);
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
        String levelName,
        Locale locale)
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
                    msg(locale, "dependency.rule.invalid.syntax.recommendation")
                );
                continue;
            }
            validateDeterminantRef(
                rule,
                schemaLevelNameCounts,
                result,
                schemaName,
                levelName,
                locale);
            if (rule.requiresTimeFilter && !hasTimeDimension) {
                result.addWarn(
                    DependencyRegistry.DependencyIssueCodes
                        .REQUIRES_TIME_FILTER_WITHOUT_TIME_DIMENSION,
                    msg(locale, "dependency.rule.requires.time.filter.without.time.dimension.message"),
                    schemaName,
                    levelName,
                    msg(
                        locale,
                        "dependency.rule.requires.time.filter.without.time.dimension.recommendation")
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
                    ? msg(locale, "dependency.rule.duplicate.message", rule.determinantRef)
                    : msg(locale, "dependency.rule.conflicting.message", rule.determinantRef),
                schemaName,
                levelName,
                duplicate
                    ? msg(locale, "dependency.rule.duplicate.recommendation")
                    : msg(locale, "dependency.rule.conflicting.recommendation")
            );
        }
    }

    private static void validateDeterminantRef(
        ParsedDependsOnRule rule,
        Map<String, Integer> schemaLevelNameCounts,
        ValidationResult result,
        String schemaName,
        String levelName,
        Locale locale)
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
                msg(locale, "dependency.rule.unknown.level.ref.message", rule.determinantRef),
                schemaName,
                levelName,
                msg(locale, "dependency.rule.unknown.level.ref.recommendation")
            );
        } else if (count > 1) {
            result.addWarn(
                DependencyRegistry.DependencyIssueCodes.AMBIGUOUS_DEPENDENCY_LEVEL_REF,
                msg(locale, "dependency.rule.ambiguous.level.ref.message", rule.determinantRef),
                schemaName,
                levelName,
                msg(locale, "dependency.rule.ambiguous.level.ref.recommendation")
            );
        } else {
            result.addInfo(
                DependencyRegistry.DependencyIssueCodes.UNQUALIFIED_DEPENDENCY_LEVEL_REF,
                msg(locale, "dependency.rule.unqualified.level.ref.message", rule.determinantRef),
                schemaName,
                levelName,
                msg(locale, "dependency.rule.unqualified.level.ref.recommendation")
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

    private static String msg(Locale locale, String key, Object... args) {
        return SchemaValidationMessages.get(locale, key, args);
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
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

    private static Map<String, String> collectSchemaLevelRefs(Document document) {
        final Map<String, String> refs = new LinkedHashMap<String, String>();
        if (document == null) {
            return refs;
        }
        final NodeList hierarchyNodes = document.getElementsByTagName("Hierarchy");
        for (int i = 0; i < hierarchyNodes.getLength(); i++) {
            final Node node = hierarchyNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element hierarchy = (Element) node;
            final String hierarchyName = resolveHierarchyName(hierarchy);
            if (hierarchyName == null) {
                continue;
            }
            final NodeList childNodes = hierarchy.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                final Node childNode = childNodes.item(j);
                if (!(childNode instanceof Element)) {
                    continue;
                }
                final Element child = (Element) childNode;
                if (!"Level".equals(child.getTagName())) {
                    continue;
                }
                final String levelName = trimToNull(child.getAttribute("name"));
                if (levelName == null) {
                    continue;
                }
                final String columnRef = trimToNull(child.getAttribute("column"));
                refs.put(
                    "[" + hierarchyName + "].[" + levelName + "]",
                    columnRef);
                final String qualifiedHierarchyName =
                    resolveQualifiedHierarchyName(hierarchy);
                if (qualifiedHierarchyName != null) {
                    refs.put(
                        "[" + qualifiedHierarchyName + "].[" + levelName + "]",
                        columnRef);
                }
            }
        }
        return refs;
    }

    private static String resolveHierarchyName(Element hierarchy) {
        if (hierarchy == null) {
            return null;
        }
        final String explicitName = trimToNull(hierarchy.getAttribute("name"));
        if (explicitName != null) {
            return explicitName;
        }
        final Node parent = hierarchy.getParentNode();
        if (parent instanceof Element) {
            return trimToNull(((Element) parent).getAttribute("name"));
        }
        return null;
    }

    private static String resolveQualifiedHierarchyName(Element hierarchy) {
        final String hierarchyName = resolveHierarchyName(hierarchy);
        if (hierarchyName == null || hierarchy == null) {
            return null;
        }
        final Node parent = hierarchy.getParentNode();
        if (!(parent instanceof Element)) {
            return null;
        }
        final String dimensionName =
            trimToNull(((Element) parent).getAttribute("name"));
        if (dimensionName == null || dimensionName.equals(hierarchyName)) {
            return null;
        }
        return dimensionName + "." + hierarchyName;
    }

    private static String extractHierarchyRef(String levelRef) {
        if (levelRef == null) {
            return "";
        }
        final int separator = levelRef.indexOf("].[");
        return separator < 0 ? levelRef : levelRef.substring(0, separator + 1);
    }

    private static String joinList(Iterable<String> values) {
        final StringBuilder out = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
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
        final boolean propertyInferenceRequested;
        final String parseError;

        private ParsedDependsOnRule(
            String determinantRef,
            String mappingType,
            String mappingProperty,
            boolean requiresTimeFilter,
            boolean propertyInferenceRequested,
            String parseError)
        {
            this.determinantRef = determinantRef;
            this.mappingType = mappingType;
            this.mappingProperty = mappingProperty;
            this.requiresTimeFilter = requiresTimeFilter;
            this.propertyInferenceRequested = propertyInferenceRequested;
            this.parseError = parseError;
        }

        static ParsedDependsOnRule ok(
            String determinantRef,
            String mappingType,
            String mappingProperty,
            boolean requiresTimeFilter)
        {
            return ok(
                determinantRef,
                mappingType,
                mappingProperty,
                requiresTimeFilter,
                false);
        }

        static ParsedDependsOnRule ok(
            String determinantRef,
            String mappingType,
            String mappingProperty,
            boolean requiresTimeFilter,
            boolean propertyInferenceRequested)
        {
            return new ParsedDependsOnRule(
                determinantRef,
                mappingType,
                mappingProperty,
                requiresTimeFilter,
                propertyInferenceRequested,
                null);
        }

        static ParsedDependsOnRule error(String parseError) {
            return new ParsedDependsOnRule(null, null, null, false, false, parseError);
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
            if (propertyInferenceRequested != other.propertyInferenceRequested) {
                return false;
            }
            return requiresTimeFilter == other.requiresTimeFilter;
        }

        private static boolean safeEquals(Object left, Object right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    private static final class ParsedDependsOnChainStep {
        final String determinantRef;
        final String mappingProperty;
        final boolean propertyInferenceRequested;
        final boolean requiresTimeFilter;
        final String parseError;

        private ParsedDependsOnChainStep(
            String determinantRef,
            String mappingProperty,
            boolean propertyInferenceRequested,
            boolean requiresTimeFilter,
            String parseError)
        {
            this.determinantRef = determinantRef;
            this.mappingProperty = mappingProperty;
            this.propertyInferenceRequested = propertyInferenceRequested;
            this.requiresTimeFilter = requiresTimeFilter;
            this.parseError = parseError;
        }

        static ParsedDependsOnChainStep ok(
            String determinantRef,
            String mappingProperty,
            boolean propertyInferenceRequested,
            boolean requiresTimeFilter)
        {
            return new ParsedDependsOnChainStep(
                determinantRef,
                mappingProperty,
                propertyInferenceRequested,
                requiresTimeFilter,
                null);
        }

        static ParsedDependsOnChainStep error(String parseError) {
            return new ParsedDependsOnChainStep(null, null, false, false, parseError);
        }
    }
}
