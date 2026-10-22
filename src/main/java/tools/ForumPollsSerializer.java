package tools;

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import model.Forum.Polls.Polls;
import org.hibernate.Hibernate;

/** List responses never initialise polls. Detail reads explicitly load them. */
public class ForumPollsSerializer implements JsonbSerializer<List<Polls>> {
    @Override
    public void serialize(List<Polls> polls, JsonGenerator generator, SerializationContext context) {
        generator.writeStartArray();
        if (Hibernate.isInitialized(polls)) {
            for (Polls poll : polls) context.serialize(poll, generator);
        }
        generator.writeEnd();
    }
}
