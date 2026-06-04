package helper;
/* This file contains all custom Deserialization tools to prevent JsonbExceptions */

import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import java.lang.reflect.Type;

public class Deserialization implements JsonbDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        try {
            jakarta.json.JsonValue val = parser.getValue();
            if (val == null || val.getValueType() == jakarta.json.JsonValue.ValueType.NULL) {
                return null;
            }
            return Long.parseLong(parser.getString());
        } catch (NumberFormatException e) {
            // handle the exception, e.g., return a default value or throw a custom exception
            return -1L;
        } catch (IllegalStateException e) {
            // handle case when parser state is VALUE_NULL or otherwise throws when calling getString()
            return null;
        }
    }
}
