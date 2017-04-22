package io.vrap.reflection;

import io.vrap.RamlModelRepository;
import io.vrap.VrapApp;
import io.vrap.VrapMode;
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

    private final VrapApp.VrapOptions options;

    public ApiHandler(final VrapApp.VrapOptions options) {
        this.options = options;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        ctx.render(json(ApiView.of(api, options.getMode())));
    }

    private static class ApiView {
        private final String title;
        private final String description;
        private final String baseUri;
        private final String authorizationUri;
        private final VrapMode vrapMode;

        private ApiView(final String title, String description, final String baseUri, final String authorizationUri, VrapMode vrapMode) {
            this.title = title;
            this.description = description;
            this.baseUri = baseUri;
            this.authorizationUri = authorizationUri;
            this.vrapMode = vrapMode;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getBaseUri() {
            return baseUri;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public VrapMode getVrapMode() {
            return vrapMode;
        }

        public static ApiView of(final Api api, final VrapMode vrapMode) {
            final List<SecurityScheme> securitySchemes = api.securitySchemes();
            final String authorizationUri = securitySchemes.stream()
                    .filter(schema -> schema.type().equals("oauth_2_0")).findFirst()
                    .map(schema -> schema.settings().authorizationUri().value()).orElse(null);
            return new ApiView(api.title().value(), api.description().value(), api.baseUri().value(), authorizationUri, vrapMode);
        }
    }
}
