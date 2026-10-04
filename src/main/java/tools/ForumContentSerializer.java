package tools;

import io.quarkus.arc.Arc;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

/**
 * Serialises stored forum HTML for a reader: image URLs become absolute and signed
 * (see {@link ForumImageStore#signForOutput(String)}). Only used on content that the
 * resource layer has already authorised for the current reader.
 */
public class ForumContentSerializer implements JsonbSerializer<String> {
    @Override
    public void serialize(String content, JsonGenerator generator, SerializationContext ctx) {
        if (content == null) {
            generator.writeNull();
            return;
        }
        generator.write(Arc.container().instance(ForumImageStore.class).get().signForOutput(content));
    }
}
