package io.vrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.security.SecurityScheme;
import ratpack.handling.Context;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves the inline tags of a raml file by inlining their content.
 */
class BaseUriReplacer {
    public StringWriter preprocess(Context ctx, final Path filePath, final Api api, final String apiPath) throws IOException {
        final Integer port = ctx.getServerConfig().getPort();
        final StringWriter stringWriter = new StringWriter();
        final String baseUri = api.baseUri().value();
        final List<SecurityScheme> oauthSchemes = api.securitySchemes().stream().filter(securityScheme -> securityScheme.type().equals("OAuth 2.0")).collect(Collectors.toList());
        String content = new String(Files.readAllBytes(filePath), Charsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        final JsonNode file = mapper.readValue(filePath.toFile(), JsonNode.class);
        if (file.has("baseUri")) {
            content = content.replaceAll(baseUri, "http://localhost:" + port.toString() + "/" + apiPath);
        }

        if (!oauthSchemes.isEmpty()) {
            for (SecurityScheme scheme : oauthSchemes) {
                content = content.replaceAll(scheme.settings().accessTokenUri().value(), "http://localhost:" + port.toString() + "/auth/" + scheme.name());
            }
        }

        return stringWriter.append(content);
    }
}
