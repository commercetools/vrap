package io.vrap.reflection;

import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.raml.v2.api.model.v10.system.types.MarkdownString;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Response;
import ratpack.http.Status;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private final static class ResourceView {
        private final String uri;
        private final String name;
        private final String description;
        private final List<TypeDeclaration> uriParams;
        private final List<MethodDeclaration> methods;

        private ResourceView(final String uri, final String name, final String description,
                             final List<TypeDeclaration> uriParams, final List<MethodDeclaration> methods) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.uriParams = uriParams;
            this.methods = methods;
        }

        public String getUri() {
            return uri;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<TypeDeclaration> getUriParams() {
            return uriParams;
        }

        public List<MethodDeclaration> getMethods() {
            return methods;
        }

        public static ResourceView of(final Resource resource) {
            final List<TypeDeclaration> uriParams = resource.uriParameters().stream()
                    .map(TypeDeclaration::of)
                    .collect(Collectors.toList());
            final List<MethodDeclaration> methods = resource.methods().stream()
                    .map(MethodDeclaration::of)
                    .collect(Collectors.toList());

            final MarkdownString description = resource.description();
            return new ResourceView(resource.resourcePath(), resource.displayName().value(),
                    description == null ? null : description.value(), uriParams, methods);
        }
    }

    private static class MethodDeclaration {
        private final String method;
        private final List<TypeDeclaration> queryParams;

        private MethodDeclaration(final String method, final List<TypeDeclaration> queryParams) {
            this.method = method;
            this.queryParams = queryParams.isEmpty() ? null : queryParams;
        }

        public List<TypeDeclaration> getQueryParams() {
            return queryParams;
        }

        public String getMethod() {
            return method;
        }

        public static MethodDeclaration of(final Method method) {
            final List<TypeDeclaration> queryParams = method.queryParameters().stream()
                    .map(TypeDeclaration::of)
                    .collect(Collectors.toList());
            return new MethodDeclaration(method.method(), queryParams);
        }
    }

    private static class TypeDeclaration {
        private final String name;
        private final String type;
        private final String example;

        private TypeDeclaration(final String name, final String type, final String example) {
            this.name = name;
            this.type = type;
            this.example = example;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getExample() {
            return example;
        }

        public static TypeDeclaration of(final org.raml.v2.api.model.v10.datamodel.TypeDeclaration typeDeclaration) {
            return new TypeDeclaration(typeDeclaration.name(),
                    typeDeclaration.type(),
                    typeDeclaration.example() != null ? typeDeclaration.example().value() : null);
        }
    }
}
