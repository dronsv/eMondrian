package emondrian;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Small CLI wrapper around {@link SchemaValidationService} for local workflows.
 */
public class SchemaValidationCli {
    private final SchemaValidationService validationService =
        new SchemaValidationService();

    public static void main(String[] args) throws Exception {
        int exitCode = new SchemaValidationCli().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) throws Exception {
        boolean failOnWarn = false;
        File file = null;
        File directory = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--fail-on-warn".equals(arg)) {
                failOnWarn = true;
                continue;
            }
            if ("--file".equals(arg)) {
                file = requirePathArg(args, ++i, "--file");
                continue;
            }
            if ("--dir".equals(arg)) {
                directory = requirePathArg(args, ++i, "--dir");
                continue;
            }
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                return 0;
            }
            if (arg.startsWith("--")) {
                System.err.println("Unknown option: " + arg);
                printUsage();
                return 2;
            }
            if (file == null && directory == null) {
                file = new File(arg);
            } else {
                System.err.println("Unexpected positional argument: " + arg);
                printUsage();
                return 2;
            }
        }

        if ((file == null && directory == null) || (file != null && directory != null)) {
            printUsage();
            return 2;
        }

        SchemaValidationService.ValidationResult result;
        if (directory != null) {
            result = validationService.validateDirectory(
                directory,
                failOnWarn,
                Locale.getDefault());
            printSummary("dir", directory, result);
        } else {
            String xml = new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
            result = validationService.validateSchemaXml(
                xml,
                file.getName(),
                failOnWarn,
                Locale.getDefault());
            printSummary("file", file, result);
        }

        printMessages(result);
        return result.isOk() ? 0 : 1;
    }

    private static File requirePathArg(
        String[] args,
        int index,
        String flag)
    {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing path after " + flag);
        }
        return new File(args[index]);
    }

    private static void printSummary(
        String mode,
        File path,
        SchemaValidationService.ValidationResult result)
    {
        System.out.println(
            "schema-validation"
                + " mode=" + mode
                + " path=" + path.getAbsolutePath()
                + " ok=" + result.isOk()
                + " fatal=" + result.getFatalCount()
                + " warn=" + result.getWarnCount()
                + " info=" + result.getInfoCount()
                + " failOnWarn=" + result.isFailOnWarn());
    }

    private static void printMessages(
        SchemaValidationService.ValidationResult result)
    {
        for (SchemaValidationService.ValidationMessage message : result.getMessages()) {
            StringBuilder line = new StringBuilder();
            line.append(message.severity.toUpperCase())
                .append(' ')
                .append(message.code)
                .append(" message=")
                .append(message.message);
            if (message.schema != null) {
                line.append(" schema=").append(message.schema);
            }
            if (message.level != null) {
                line.append(" level=").append(message.level);
            }
            if (message.recommendation != null) {
                line.append(" recommendation=").append(message.recommendation);
            }
            System.out.println(line.toString());
        }
    }

    private static void printUsage() {
        System.err.println(
            "Usage: SchemaValidationCli (--file <schema.xml> | --dir <schema-dir>) [--fail-on-warn]");
    }
}
