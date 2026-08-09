package com.moviebooking.common.debezium;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DebeziumConnectorService {

    @Value("${debezium.host:localhost:8083}")
    private String debeziumHost;

    @Value("${debezium.connector-name:outbox-connector}")
    private String connectorName;

    @Value("${debezium.auto-register:true}")
    private boolean autoRegister;

    @Value("${debezium.db.host:mysql}")
    private String dbHost;

    @Value("${debezium.db.port:3306}")
    private String dbPort;

    @Value("${debezium.db.user:root}")
    private String dbUser;

    @Value("${debezium.db.password:123456}")
    private String dbPassword;

    @Value("${debezium.db.server-id:1}")
    private String dbServerId;

    @Value("${debezium.topic-prefix:saga}")
    private String topicPrefix;

    @Value("${debezium.schema.history.bootstrap:kafka:9092}")
    private String schemaHistoryBootstrap;

    private final RestTemplate restTemplate = new RestTemplate();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoRegister) {
            return;
        }

        // Run in background thread to avoid blocking application startup if Debezium is not ready yet
        new Thread(this::ensureConnector).start();
    }

    private void ensureConnector() {
        try {
            waitForDebeziumReady();

            String baseUrl = getBaseUrl();
            ResponseEntity<List> response = restTemplate.getForEntity(baseUrl + "/connectors", List.class);
            List<String> existingConnectors = response.getBody();

            if (existingConnectors != null && existingConnectors.contains(connectorName)) {
                log.info("Debezium connector '{}' exists. Updating configuration...", connectorName);
                upsertConnectorConfig();
                restartConnector();
                return;
            }

            if (existingConnectors != null && !existingConnectors.isEmpty()) {
                log.info("Connectors exist: {}. Skipping auto registration.", existingConnectors);
                return;
            }

            log.info("Registering Debezium connector '{}'...", connectorName);
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", connectorName);
            payload.put("config", buildConnectorConfig());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(baseUrl + "/connectors", new HttpEntity<>(payload, headers), Map.class);
            log.info("Debezium connector '{}' registered successfully.", connectorName);

        } catch (Exception e) {
            log.warn("Debezium connector registration failed: {}", e.getMessage());
        }
    }

    private Map<String, String> buildConnectorConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        config.put("tasks.max", "1");
        config.put("database.hostname", dbHost);
        config.put("database.port", dbPort);
        config.put("database.user", dbUser);
        config.put("database.password", dbPassword);
        config.put("database.server.id", dbServerId);
        config.put("topic.prefix", topicPrefix);
        config.put("database.include.list", "booking_db,seat_db,payment_db,movie_db");
        config.put("table.include.list", "booking_db.outbox,seat_db.outbox,payment_db.outbox,movie_db.outbox");
        config.put("schema.history.internal.kafka.bootstrap.servers", schemaHistoryBootstrap);
        config.put("schema.history.internal.kafka.topic", "_schema_history_saga");
        config.put("schema.history.internal.skip.unparseable.ddl", "true");
        config.put("transforms", "outbox");
        config.put("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter");
        config.put("transforms.outbox.table.expand.json.payload", "true");
        config.put("transforms.outbox.table.field.event.id", "id");
        config.put("transforms.outbox.table.field.event.key", "aggregate_id");
        config.put("transforms.outbox.table.field.event.type", "event_type");
        config.put("transforms.outbox.table.field.event.payload", "payload");
        config.put("transforms.outbox.table.fields.additional.placement", "event_type:header:eventType");
        config.put("transforms.outbox.route.by.field", "aggregate_type");
        config.put("transforms.outbox.route.topic.replacement", "${routedByValue}.events");
        config.put("key.converter", "org.apache.kafka.connect.storage.StringConverter");
        config.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("value.converter.schemas.enable", "false");
        config.put("tombstones.on.delete", "false");
        config.put("include.schema.changes", "false");
        return config;
    }

    private void waitForDebeziumReady() throws InterruptedException {
        int maxRetries = 30;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                restTemplate.getForEntity(getBaseUrl(), String.class);
                log.info("Debezium REST API is ready.");
                return;
            } catch (Exception ignored) {
            }
            Thread.sleep(2000);
        }
        log.warn("Debezium REST API not ready after max retries.");
    }

    private void upsertConnectorConfig() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(buildConnectorConfig(), headers);
        restTemplate.exchange(getBaseUrl() + "/connectors/" + connectorName + "/config", HttpMethod.PUT, entity, Map.class);
    }

    private void restartConnector() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(getBaseUrl() + "/connectors/" + connectorName + "/restart", entity, Void.class);
        } catch (Exception e) {
            log.warn("Failed to restart connector: {}", e.getMessage());
        }
    }

    private String getBaseUrl() {
        if (debeziumHost.startsWith("http://") || debeziumHost.startsWith("https://")) {
            return debeziumHost;
        }
        return "http://" + debeziumHost;
    }
}
