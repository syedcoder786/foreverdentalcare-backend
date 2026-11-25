package com.clinic.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Small value types stored as JSON text columns (portable across PostgreSQL and H2), with their JPA converters.
 * Used for ordered lists that are always read and written as a whole (qualifications, section cards).
 */
public final class JsonValues {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonValues() {
    }

    public record Qualification(String degree, String institution, String year) {
    }

    /** A card inside a website section (feature, facility, equipment item, hero highlight...). */
    public record SectionItem(String icon, String title, String text, String imageUrl) {
    }

    abstract static class ListConverter<T> implements AttributeConverter<List<T>, String> {
        private final TypeReference<List<T>> type;

        ListConverter(TypeReference<List<T>> type) {
            this.type = type;
        }

        @Override
        public String convertToDatabaseColumn(List<T> value) {
            try {
                return MAPPER.writeValueAsString(value == null ? List.of() : value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not serialise JSON column", e);
            }
        }

        @Override
        public List<T> convertToEntityAttribute(String json) {
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            try {
                return new ArrayList<>(MAPPER.readValue(json, type));
            } catch (Exception e) {
                throw new IllegalStateException("Corrupt JSON column value", e);
            }
        }
    }

    @Converter
    public static class QualificationListConverter extends ListConverter<Qualification> {
        public QualificationListConverter() {
            super(new TypeReference<>() {
            });
        }
    }

    @Converter
    public static class SectionItemListConverter extends ListConverter<SectionItem> {
        public SectionItemListConverter() {
            super(new TypeReference<>() {
            });
        }
    }
}
