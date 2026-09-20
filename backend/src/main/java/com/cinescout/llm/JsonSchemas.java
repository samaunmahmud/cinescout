package com.cinescout.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives the JSON Schema an LLM must follow from the Java type it will be parsed into, so
 * the contract sent to the model and the type the answer is validated against cannot drift.
 *
 * <p>The schema is shaped for providers' strict structured-output modes: every property is
 * listed as required, unknown properties are forbidden, and a property is only non-nullable
 * when the type says so ({@code @NotNull}/{@code @NotBlank}). "Optional" therefore means
 * "the model must return null", never "the model may leave it out".
 */
public class JsonSchemas {

    private final SchemaGenerator generator;
    private final Map<Class<?>, JsonNode> cache = new ConcurrentHashMap<>();

    public JsonSchemas() {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
                .with(new JakartaValidationModule(
                        JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                        JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS))
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT, Option.NULLABLE_FIELDS_BY_DEFAULT)
                // Which schema dialect a vendor accepts is undocumented; the marker adds nothing to the contract.
                .without(Option.SCHEMA_VERSION_INDICATOR);
        builder.forFields().withRequiredCheck(field -> true);
        this.generator = new SchemaGenerator(builder.build());
    }

    /** A private copy, safe for the caller to modify. */
    public JsonNode schemaFor(Class<?> type) {
        return cache.computeIfAbsent(type, generator::generateSchema).deepCopy();
    }
}
