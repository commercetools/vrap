package io.vrap.reflection;

import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.security.SecurityScheme;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.List;

import static ratpack.jackson.Jackson.json;

/**
 * This handler provides information about the loaded raml api definition.
 */
public class ApiHandler implements Handler {

    @Override
    public void handle(final Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        ctx.render(json(ApiView.of(api)));
    }

    private static class ApiView {
        private final String title;
        private final String baseUri;
        private final String authorizationUri;

        private ApiView(final String title, final String baseUri, final String authorizationUri) {
            this.title = title;
            this.baseUri = baseUri;
            this.authorizationUri = authorizationUri;
        }

        public String getTitle() {
            return title;
        }

        public String getBaseUri() {
            return baseUri;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public static ApiView of(final Api api) {
            final List<SecurityScheme> securitySchemes = api.securitySchemes();
            final String authorizationUri = securitySchemes.stream()
                    .filter(schema -> schema.type().equals("oauth_2_0")).findFirst()
                    .map(schema -> schema.settings().authorizationUri().value()).orElse(null);
            return new ApiView(api.title().value(), api.baseUri().value(), authorizationUri);
        }
    }
}
