package emondrian;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

final class SchemaValidationMessages {
    private static final String BUNDLE_BASE_NAME =
        "emondrian.SchemaValidationMessages";

    private SchemaValidationMessages() {
    }

    static String get(Locale locale, String key, Object... args) {
        final Locale resolved = locale == null ? Locale.getDefault() : locale;
        final String pattern = resolvePattern(resolved, key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    private static String resolvePattern(Locale locale, String key) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            try {
                ResourceBundle fallback = ResourceBundle.getBundle(
                    BUNDLE_BASE_NAME,
                    Locale.ENGLISH);
                return fallback.getString(key);
            } catch (MissingResourceException ignored) {
                return key;
            }
        }
    }
}
