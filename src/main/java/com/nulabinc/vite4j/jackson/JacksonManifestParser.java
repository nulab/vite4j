package com.nulabinc.vite4j.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nulabinc.vite4j.Chunk;
import com.nulabinc.vite4j.ManifestParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the manifest with Jackson.
 *
 * <p>Jackson is a {@code provided} dependency here: the application keeps control of the version, and
 * this artifact never pulls one in. It reads the fields it needs and ignores everything else, so a
 * manifest from a newer Vite that carries extra keys still parses.
 */
public final class JacksonManifestParser implements ManifestParser {

    private final ObjectMapper mapper;

    public JacksonManifestParser() {
        this(new ObjectMapper());
    }

    /** Uses an application's own mapper, for instance one configured with a shared module set. */
    public JacksonManifestParser(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    @Override
    public Map<String, Chunk> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("The Vite manifest is not valid JSON", e);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("The Vite manifest must be a JSON object, was " + root.getNodeType());
        }

        Map<String, Chunk> chunks = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode chunk = field.getValue();
            chunks.put(field.getKey(), new Chunk(text(chunk, "file"), strings(chunk, "css"), strings(chunk, "imports")));
        }
        return chunks;
    }

    private static String text(JsonNode chunk, String field) {
        JsonNode value = chunk.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static List<String> strings(JsonNode chunk, String field) {
        JsonNode value = chunk.get(field);
        if (value == null || !value.isArray()) {
            return java.util.Collections.emptyList();
        }
        List<String> values = new ArrayList<>(value.size());
        for (JsonNode element : value) {
            if (element.isTextual()) {
                values.add(element.asText());
            }
        }
        return values;
    }
}
