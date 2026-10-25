package com.plantshop.user.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.plantshop.logging.CorrelationId;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.User;
import com.plantshop.user.repository.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simulated mail provider: nothing leaves the service, the message is recorded in email_outbox.
 * Consent is not checked here; callers sending MARKETING mail go through the consent policy gate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final EmailMessageRepository repository;
    private final EmailProviderProperties provider;
    private final ObjectMapper objectMapper;

    /** Template parameters (one-time tokens, links) go to the provider only and are not stored. */
    public EmailMessage send(User recipient, EmailCategory category, String template, Map<String, String> params) {
        EmailMessage message = repository.save(
                new EmailMessage(recipient.getId(), category, template, recipient.getEmail()));
        // Wire-level diagnostics of the provider call, off unless this logger is at DEBUG. Even then
        // secure-logging redacts the API key and the tokens and masks the recipient before writing.
        if (log.isDebugEnabled()) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + provider.apiKey());
            headers.put(CorrelationId.HEADER, CorrelationId.current());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("to", recipient.getEmail());
            body.put("template", template);
            body.put("params", params);
            log.debug("Mail provider request POST {} headers={} body={}", provider.url(), headers, toJson(body));
        }
        log.info("E-mail queued messageId={} category={} template={}", message.getId(), category, template);
        return message;
    }

    /** Always one line, whatever the API's JSON formatting: a log event must not span several lines. */
    private String toJson(Object body) {
        try {
            return objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(body);
        } catch (JsonProcessingException e) {
            return "<unserializable>";
        }
    }
}
