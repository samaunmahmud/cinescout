package com.cinescout.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The questions of a tech recce, each with the JSON name it goes by and the answers it takes. Checking a value
 * returns it as it is kept (text trimmed, numbers in range), or why it is not acceptable.
 */
public enum RecceField {
    SOCKETS("sockets", Kind.WHOLE, 0, 200, null),
    THREE_PHASE("threePhase", Kind.YES_NO, 0, 0, null),
    POWER_NOTES("powerNotes", Kind.TEXT, 0, 500, null),
    CEILING_HEIGHT_M("ceilingHeightM", Kind.DECIMAL, 1, 50, null),
    LOAD_IN_ROUTE("loadInRoute", Kind.TEXT, 0, 500, null),
    STAIRS_OR_LIFT("stairsOrLift", Kind.CHOICE, 0, 0, Set.of("GROUND_LEVEL", "STAIRS", "LIFT", "STAIRS_AND_LIFT")),
    STEP_FREE("stepFree", Kind.YES_NO, 0, 0, null),
    AMBIENT_NOISE("ambientNoise", Kind.WHOLE, 1, 5, null),
    PHONE_SIGNAL("phoneSignal", Kind.CHOICE, 0, 0, Set.of("NONE", "POOR", "OK", "STRONG")),
    TOILETS("toilets", Kind.YES_NO, 0, 0, null),
    HOLDING_SPACE("holdingSpace", Kind.YES_NO, 0, 0, null),
    NOTES("notes", Kind.TEXT, 0, 2000, null);

    enum Kind { WHOLE, DECIMAL, YES_NO, TEXT, CHOICE }

    private final String json;
    private final Kind kind;
    private final int min;
    private final int max;
    private final Set<String> choices;

    RecceField(String json, Kind kind, int min, int max, Set<String> choices) {
        this.json = json;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.choices = choices;
    }

    public String json() {
        return json;
    }

    public static Optional<RecceField> byJson(String name) {
        return Arrays.stream(values()).filter(field -> field.json.equals(name)).findFirst();
    }

    /** The value as kept: a whole number, a decimal, true/false, trimmed text or a choice; or what is wrong with it. */
    public Checked check(JsonNode value) {
        return switch (kind) {
            case WHOLE -> value.isIntegralNumber() && value.asLong() >= min && value.asLong() <= max
                    ? Checked.ok(value.asInt())
                    : Checked.wrong("must be a whole number from " + min + " to " + max);
            case DECIMAL -> value.isNumber() && value.decimalValue().compareTo(BigDecimal.valueOf(min)) >= 0
                    && value.decimalValue().compareTo(BigDecimal.valueOf(max)) <= 0
                    ? Checked.ok(value.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros())
                    : Checked.wrong("must be a number from " + min + " to " + max);
            case YES_NO -> value.isBoolean() ? Checked.ok(value.asBoolean()) : Checked.wrong("must be true or false");
            case TEXT -> {
                if (!value.isTextual()) {
                    yield Checked.wrong("must be text");
                }
                String text = value.asText().strip();
                yield text.length() > max ? Checked.wrong("must be at most " + max + " characters")
                        : text.isEmpty() ? Checked.ok(null) : Checked.ok(text);
            }
            case CHOICE -> value.isTextual() && choices.contains(value.asText())
                    ? Checked.ok(value.asText())
                    : Checked.wrong("must be one of " + String.join(", ", choices.stream().sorted().toList()));
        };
    }

    /** A checked value (null clears the field), or the problem with it. */
    public record Checked(Object value, String problem) {
        static Checked ok(Object value) {
            return new Checked(value, null);
        }

        static Checked wrong(String problem) {
            return new Checked(null, problem);
        }
    }
}
