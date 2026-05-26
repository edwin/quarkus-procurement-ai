package com.edw.config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * <pre>
 *  com.edw.config.GuardrailsConfig
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 06 May 2026 10:47
 */
@ApplicationScoped
public class GuardrailsConfig implements InputGuardrail {

    private Logger logger = LoggerFactory.getLogger(GuardrailsConfig.class);

    public static final String PROMPT_INJECTION_DETECTED_REQUEST_BLOCKED = "Prompt injection detected. Request blocked.";

    private static final Pattern SQL_COMMAND_REGEX = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|GRANT|REVOKE|COMMIT|ROLLBACK|TRUNCATE|MERGE)\\b"
    );

    private boolean validateInputForSqlCommand(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return SQL_COMMAND_REGEX.matcher(query).find();
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();

        if (validateInputForSqlCommand(text)) {
            logger.warn(PROMPT_INJECTION_DETECTED_REQUEST_BLOCKED);
            return failure(PROMPT_INJECTION_DETECTED_REQUEST_BLOCKED);
        }

        return success();
    }
}
