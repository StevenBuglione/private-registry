package com.stevenbuglione.registry.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class JdbcCatalogChangeNotifier implements CatalogActivationNotifier {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCatalogChangeNotifier(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyChanged(String publicId, String version) {
        jdbc.sql("SELECT pg_notify('registry_catalog_changes', :payload)")
                .param("payload", json(Map.of("package_id", publicId, "version", version)))
                .query(String.class)
                .optional();
    }

    private String json(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize catalog notification", exception);
        }
    }
}
