package emondrian;

import mondrian.rolap.sql.dependency.DependencyRegistry;
import mondrian.rolap.sql.dependency.SchemaDependencyValidationReport;
import mondrian.rolap.sql.dependency.SchemaDependencyValidator;

import java.io.File;

/**
 * Temporary adapter from eMondrian validator implementation to shared Mondrian
 * dependency validation DTOs/API.
 *
 * <p>Long-term goal: move validation logic into Mondrian and keep this class as
 * a thin delegating facade only (or remove it entirely).</p>
 */
public class MondrianSchemaDependencyValidationAdapter
    implements SchemaDependencyValidator
{
    private final SchemaValidationService delegate;

    public MondrianSchemaDependencyValidationAdapter(SchemaValidationService delegate) {
        this.delegate = delegate;
    }

    public SchemaDependencyValidationReport validateDirectory(
        File schemaDir,
        boolean failOnWarn)
    {
        return toReport(delegate.validateDirectory(schemaDir, failOnWarn));
    }

    public SchemaDependencyValidationReport validateSchemaXml(
        String schemaXml,
        String schemaName,
        boolean failOnWarn)
    {
        return toReport(delegate.validateSchemaXml(schemaXml, schemaName, failOnWarn));
    }

    public static SchemaDependencyValidationReport toReport(
        SchemaValidationService.ValidationResult source)
    {
        boolean failOnWarn = source != null && source.isFailOnWarn();
        SchemaDependencyValidationReport report =
            new SchemaDependencyValidationReport(failOnWarn);
        if (source == null) {
            report.addIssue(new DependencyRegistry.DependencyValidationIssue(
                DependencyRegistry.DependencyValidationSeverity.FATAL,
                "VALIDATION_RESULT_NULL",
                "Schema validation returned null result.",
                null,
                null,
                "Return a non-null ValidationResult from SchemaValidationService."));
            return report;
        }

        for (SchemaValidationService.ValidationMessage message : source.getMessages()) {
            report.addIssue(new DependencyRegistry.DependencyValidationIssue(
                toSeverity(message.severity),
                message.code,
                message.message,
                message.schema,
                message.level,
                message.recommendation));
        }
        return report;
    }

    private static DependencyRegistry.DependencyValidationSeverity toSeverity(
        String severity)
    {
        if (severity == null) {
            return DependencyRegistry.DependencyValidationSeverity.INFO;
        }
        String normalized = severity.trim().toLowerCase();
        if ("fatal".equals(normalized) || "error".equals(normalized)) {
            return DependencyRegistry.DependencyValidationSeverity.FATAL;
        }
        if ("warn".equals(normalized) || "warning".equals(normalized)) {
            return DependencyRegistry.DependencyValidationSeverity.WARN;
        }
        return DependencyRegistry.DependencyValidationSeverity.INFO;
    }
}

