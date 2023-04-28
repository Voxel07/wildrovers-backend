package helper;
/* This file contains all custom Deserialization tools to prevent JsonbExceptions */

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;

import java.lang.reflect.Type;

public class Deserialization implements JsonbDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        try {
            return Long.parseLong(parser.getString());
        } catch (NumberFormatException e) {
            // handle the exception, e.g., return a default value or throw a custom exception
            return -1L;
        }
    }
}
