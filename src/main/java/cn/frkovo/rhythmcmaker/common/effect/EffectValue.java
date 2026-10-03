package cn.frkovo.rhythmcmaker.common.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A typed effect value. Raw JSON values are kept as text until persistence code decodes them. */
public final class EffectValue {
    private final EffectPropertyValueKind kind;
    private final String textValue;
    private final Double decimalValue;
    private final Boolean booleanValue;
    private final List<Long> integerArrayValue;
    private final List<Double> vectorValue;
    private final String rawJson;

    private EffectValue(EffectPropertyValueKind kind, String textValue, Double decimalValue,
                        Boolean booleanValue, List<Long> integerArrayValue, List<Double> vectorValue, String rawJson) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.textValue = textValue;
        this.decimalValue = decimalValue;
        this.booleanValue = booleanValue;
        this.integerArrayValue = integerArrayValue == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(integerArrayValue));
        this.vectorValue = vectorValue == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(vectorValue));
        this.rawJson = rawJson;
    }

    public static EffectValue text(String value) {
        return new EffectValue(EffectPropertyValueKind.TEXT, Objects.requireNonNull(value, "value"), null, null, null, null, null);
    }

    public static EffectValue enumeration(String value) {
        return new EffectValue(EffectPropertyValueKind.ENUM, Objects.requireNonNull(value, "value"), null, null, null, null, null);
    }

    public static EffectValue integer(long value) {
        return new EffectValue(EffectPropertyValueKind.INTEGER, null, (double) value, null, null, null, null);
    }

    public static EffectValue decimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return new EffectValue(EffectPropertyValueKind.DECIMAL, null, value, null, null, null, null);
    }

    public static EffectValue color(int argb) {
        return new EffectValue(EffectPropertyValueKind.COLOR, null, (double) argb, null, null, null, null);
    }

    public static EffectValue bool(boolean value) {
        return new EffectValue(EffectPropertyValueKind.BOOLEAN, null, null, value, null, null, null);
    }

    public static EffectValue integerArray(List<Long> values) {
        Objects.requireNonNull(values, "values");
        for (Long value : values) if (value == null) throw new IllegalArgumentException("integer array values must not be null");
        return new EffectValue(EffectPropertyValueKind.INTEGER_ARRAY, null, null, null, values, null, null);
    }

    public static EffectValue vector(List<Double> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) throw new IllegalArgumentException("vector must not be empty");
        for (Double value : values) if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("vector values must be finite");
        return new EffectValue(EffectPropertyValueKind.VECTOR, null, null, null, null, values, null);
    }

    public static EffectValue rawJson(String json) {
        String value = Objects.requireNonNull(json, "json").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("json must not be blank");
        return new EffectValue(EffectPropertyValueKind.JSON, null, null, null, null, null, value);
    }

    public EffectPropertyValueKind kind() {
        return kind;
    }

    public String textValue() {
        return textValue;
    }

    public double numericValue() {
        if (decimalValue == null) throw new IllegalStateException("value is not numeric");
        return decimalValue;
    }

    public boolean booleanValue() {
        if (booleanValue == null) throw new IllegalStateException("value is not boolean");
        return booleanValue;
    }

    public List<Double> vectorValue() {
        return vectorValue;
    }

    public List<Long> integerArrayValue() {
        return integerArrayValue;
    }

    public String rawJson() {
        return rawJson;
    }
}
