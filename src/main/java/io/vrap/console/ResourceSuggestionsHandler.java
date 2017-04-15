package io.vrap.console;

import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.raml.v2.api.model.v10.system.types.MarkdownString;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;

/**
 * This handle provides the resource suggestions api.
 */
public class ResourceSuggestionsHandler implements Handler {
    private final static Pattern PATH_SEGMENT_PATTERN = Pattern.compile("(/[^/]*)(.*)");

    @Override
    public void handle(final Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        final String query = ctx.getRequest().getQueryParams().get("query");
        final List<ResourceSuggestion> resourceSuggestions;
        if (query.contains("/")) {
            resourceSuggestions =
                    suggestPathResources(api.resources(), query).stream()
                            .map(result -> ResourceSuggestion.of(result, result.resourcePath()))
                            .collect(Collectors.toList());
        } else {
            resourceSuggestions = suggestNameResources(api.resources(), query).stream()
                            .map(result -> ResourceSuggestion.of(result, result.displayName().value()))
                            .collect(Collectors.toList());
        }
        if (resourceSuggestions.isEmpty()) {
            ctx.notFound();
        } else {
            ctx.render(json(resourceSuggestions));
        }
    }

    private List<Resource> suggestPathResources(final List<Resource> resources, final String path) {
        final List<Resource> foundResources = new ArrayList<>();

        final Matcher matcher = PATH_SEGMENT_PATTERN.matcher(path);
        if (matcher.matches()) {
            final String pathSegment = matcher.group(1);
            final List<Resource> matches = resources.stream()
                    .filter(res -> res.relativeUri().value().startsWith(pathSegment))
                    .collect(Collectors.toList());
            final String remainingPath = matcher.group(2);
            if (remainingPath.isEmpty()) {
                foundResources.addAll(matches);
            } else {
                final List<Resource> subResources = matches.stream()
                        .flatMap(match -> match.resources().stream())
                        .collect(Collectors.toList());
                foundResources.addAll(suggestPathResources(subResources, remainingPath));
            }
        }

        return foundResources;
    }


    private List<Resource> suggestNameResources(final List<Resource> resources, final String name) {
        final List<Resource> foundResources = new ArrayList<>();

        final String[] words = name.toLowerCase().split("\\s+");
        for (final String word : words) {
            foundResources.addAll(suggestResourcesContainingWord(resources, word));
        }

        return foundResources;
    }

    private List<Resource> suggestResourcesContainingWord(final List<Resource> resources, final String word) {
        final List<Resource> foundResources = new ArrayList<>();
        for (final Resource resource : resources) {
            if (containsWord(resource, word)) {
                foundResources.add(resource);
            }
            foundResources.addAll(suggestResourcesContainingWord(resource.resources(), word));
        }
        return foundResources;
    }

    private boolean containsWord(final Resource resource, final String word) {
        return resource.displayName() != null && resource.displayName().value().toLowerCase().contains(word)
                || resource.description() != null && resource.description().value().toLowerCase().contains(word);
    }

    private static class ResourceSuggestion {
        private final String label;
        private final String uri;
        private final String name;
        private final String description;
        private final Map<String, TypeDecl> uriParams;
        private final Map<String, MethodDecl> methods;

        private ResourceSuggestion(String label, final String uri, final String name, final String description,
                                   final Map<String, TypeDecl> uriParams, final Map<String, MethodDecl> methods) {
            this.label = label;
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.uriParams = uriParams;
            this.methods = methods;
        }

        public String getLabel() {
            return label;
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

        public Map<String, TypeDecl> getUriParams() {
            return uriParams;
        }

        public Map<String, MethodDecl> getMethods() {
            return methods;
        }

        static ResourceSuggestion of(final Resource resource, final String label) {
            final Map<String, TypeDecl> uriParams = resource.uriParameters().stream()
                    .map(TypeDecl::of)
                    .collect(Collectors.toMap(TypeDecl::getName, Function.identity()));
            final Map<String, MethodDecl> methods = resource.methods().stream()
                    .map(MethodDecl::of)
                    .collect(Collectors.toMap(MethodDecl::getMethod, Function.identity()));

            final MarkdownString description = resource.description();
            return new ResourceSuggestion(label, resource.resourcePath(), resource.displayName().value(),
                    description == null ? null : description.value(), uriParams, methods);
        }
    }

    private final static class MethodDecl {
        private final String method;
        private final Map<String, TypeDecl> queryParams;

        private MethodDecl(final String method, final Map<String, TypeDecl> queryParams) {
            this.method = method;
            this.queryParams = queryParams.isEmpty() ? null : queryParams;
        }

        public Map<String, TypeDecl> getQueryParams() {
            return queryParams;
        }

        public String getMethod() {
            return method;
        }

        public static MethodDecl of(final Method method) {
            final Map<String, TypeDecl> queryParams = method.queryParameters().stream()
                    .map(TypeDecl::of)
                    .collect(Collectors.toMap(TypeDecl::getName, Function.identity()));
            return new MethodDecl(method.method(), queryParams);
        }
    }

    private static class TypeDecl {
        private final String name;
        private final String type;
        private final String example;

        private TypeDecl(final String name, final String type, final String example) {
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

        public static TypeDecl of (final TypeDeclaration typeDeclaration) {
            return new TypeDecl(typeDeclaration.name(),
                    typeDeclaration.type(),
                    typeDeclaration.example() != null ? typeDeclaration.example().value() : null);
        }
    }
}
