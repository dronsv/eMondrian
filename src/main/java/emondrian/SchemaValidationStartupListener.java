package emondrian;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;

public class SchemaValidationStartupListener implements ServletContextListener {
    private static final String DEFAULT_SCHEMA_DIR =
        "/usr/local/tomcat/webapps/emondrian/WEB-INF/schema";

    private final SchemaValidationService validationService = new SchemaValidationService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        boolean validateOnStartup = parseBool(
            System.getenv("EMONDRIAN_SCHEMA_VALIDATE_ON_STARTUP"), true);
        if (!validateOnStartup) {
            context.log("[schema-validator] Startup schema validation is disabled.");
            return;
        }

        boolean failOnWarn = parseBool(
            System.getenv("EMONDRIAN_SCHEMA_WARNINGS_AS_ERRORS"), false);
        File schemaDir = resolveSchemaDir(context);

        SchemaValidationService.ValidationResult result =
            validationService.validateDirectory(schemaDir, failOnWarn);

        for (SchemaValidationService.ValidationMessage message : result.getMessages()) {
            context.log("[schema-validator][" + message.severity.toUpperCase() + "][" + message.code + "] "
                + message.message
                + (message.schema == null ? "" : " | schema=" + message.schema)
                + (message.level == null ? "" : " | level=" + message.level)
                + (message.recommendation == null ? "" : " | hint=" + message.recommendation));
        }

        if (!result.isOk()) {
            throw new IllegalStateException(
                "Schema validation failed. fatal=" + result.getFatalCount()
                    + ", warn=" + result.getWarnCount()
                    + ", failOnWarn=" + result.isFailOnWarn());
        }

        context.log("[schema-validator] Schema validation completed successfully. "
            + "fatal=" + result.getFatalCount()
            + ", warn=" + result.getWarnCount());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // no-op
    }

    private static File resolveSchemaDir(ServletContext context) {
        String envDir = System.getenv("EMONDRIAN_SCHEMA_DIR");
        if (envDir != null && !envDir.trim().isEmpty()) {
            return new File(envDir.trim());
        }

        String realPath = context.getRealPath("/WEB-INF/schema");
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
}
