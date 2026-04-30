package com.onconavigator.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Logback message converter that redacts known PHI field patterns from log output.
 *
 * <p>HIPAA Technical Safeguard: This converter runs on every log message and replaces
 * content that matches PHI field name patterns with [PHI_REDACTED]. This is a best-effort
 * defense against accidental PHI leakage in log statements. The canonical rule is:
 * <em>never log PHI — only log patient UUIDs and care event type codes.</em>
 *
 * <p>Registered in logback-spring.xml as %phiSafe conversion word.
 *
 * <p>Thread safety: Pattern objects are immutable and pre-compiled at class load time.
 * ClassicConverter instances may be reused across threads by Logback.
 */
public class PhiRedactingConverter extends ClassicConverter {

    /**
     * Patterns that match PHI field name=value pairs in log messages.
     * All patterns are case-insensitive and match key=value or key: value syntax.
     */
    private static final Pattern[] PHI_PATTERNS = {
        Pattern.compile("(?i)(patient[_\\s]?name|patientName)\\s*[=:]\\s*\\S+"),
        Pattern.compile("(?i)(date[_\\s]?of[_\\s]?birth|dob|dateOfBirth)\\s*[=:]\\s*\\S+"),
        Pattern.compile("(?i)(ssn|social[_\\s]?security)\\s*[=:]\\s*\\S+"),
        Pattern.compile("(?i)(mrn|medical[_\\s]?record[_\\s]?number)\\s*[=:]\\s*\\S+"),
        Pattern.compile("(?i)(diagnosis|primary[_\\s]?diagnosis)\\s*[=:]\\s*[^,}]+"),
        Pattern.compile("(?i)(phone|telephone|mobile)\\s*[=:]\\s*[\\d\\-\\(\\)\\s]+"),
        Pattern.compile("(?i)(email|e-mail)\\s*[=:]\\s*\\S+@\\S+"),
        Pattern.compile("(?i)(address|street|city|zip)\\s*[=:]\\s*[^,}]+")
    };

    private static final String REDACTED = "[PHI_REDACTED]";

    /**
     * Convert the log event's formatted message with PHI patterns redacted.
     *
     * @param event the logging event
     * @return the message with all PHI field patterns replaced by [PHI_REDACTED]
     */
    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }
        for (Pattern pattern : PHI_PATTERNS) {
            message = pattern.matcher(message).replaceAll(REDACTED);
        }
        return message;
    }
}
