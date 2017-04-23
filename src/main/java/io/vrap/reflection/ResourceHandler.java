package io.vrap.reflection;

import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.resources.Resource;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Response;
import ratpack.http.Status;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ratpack.jackson.Jackson.json;

/**
 * This handle provides the resource trtieval api.
 */
public class ResourceHandler implements Handler {
    private final static Pattern URI_TO_PATH_PATTERN = Pattern.compile("(/[^/]+)");

    @Override
    public void handle(final Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        final String uri = ctx.getRequest().getQueryParams().get("uri");

        final Response response = ctx.getResponse();
        if (uri != null) {
            final Matcher uriMatcher = URI_TO_PATH_PATTERN.matcher(uri);
            List<Resource> resources = api.resources();
            Resource resource = null;
            while (uriMatcher.find()) {
                final String currentRelativeUri = uriMatcher.group();
                final Optional<Resource> foundResource = resources.stream()
                        .filter(r -> r.relativeUri().value().equals(currentRelativeUri))
                        .findFirst();
                if (foundResource.isPresent()) {
                    resource = foundResource.get();
                    resources = resource.resources();
                } else {
                    break;
                }
            }
            if (resource != null) {
                ctx.render(json(ResourceView.of(resource)));
            } else {
                response.status(Status.of(404, String.format("Resource 'uri=%s' not found", uri)));
            }
        } else {
            response.status(Status.of(400, String.format("Required query parameter 'uri' missing"))).send();
        }
    }

}
