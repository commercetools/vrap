package io.vrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ratpack.handling.Context;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

/**
 * Resolves the inline tags of a raml file by inlining their content.
 */
class BaseUriReplacer {
    public StringWriter preprocess(Context ctx, final Path filePath) throws IOException {
        final StringWriter stringWriter = new StringWriter();

        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        final JsonNode file = mapper.readValue(filePath.toFile(), JsonNode.class);
        if (file.has("baseUri")) {
            final Integer port = ctx.getServerConfig().getPort();

            ((ObjectNode) file).put("baseUri", "http://localhost:" + port.toString() + "/api");
        }

        return stringWriter.append(file.toString());
    }
}
