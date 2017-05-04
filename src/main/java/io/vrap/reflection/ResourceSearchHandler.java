package io.vrap.reflection;

import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.raml.v2.api.model.v10.system.types.MarkdownString;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.util.MultiValueMap;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;

/**
 * This handler provides the resource search api.
 */
public class ResourceSearchHandler implements Handler {
    private final static Pattern PATH_SEGMENT_PATTERN = Pattern.compile("(/[^/]*)(.*)");

    @Override
    public void handle(final Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        final MultiValueMap<String, String> queryParams = ctx.getRequest().getQueryParams();
        final String query = queryParams.get("query");
        final String resourcePath = queryParams.get("path");
        final Optional<Resource> foundResource = resourcePath != null ?
                findResource(api.resources(), resourcePath) :
                Optional.empty();
        final List<Resource> resources = foundResource.isPresent() ?
                foundResource.get().resources() :
                api.resources();
        final List<SearchResult> results;

        if (query != null && query.contains("/")) {
            results =
                    suggestPathResources(resources, query).stream()
                            .map(result -> SearchResult.of(result.relativeUri().value(), result))
                            .collect(Collectors.toList());
        } else {
            results = suggestNameResources(resources, query).stream()
                    .map(result -> SearchResult.of(result.displayName().value(), result))
                    .collect(Collectors.toList());
        }
        ctx.render(json(results));
    }

    private Optional<Resource> findResource(final List<Resource> resources, final String resourcePath) {
        final Optional<Resource> foundResource = resources.stream()
                .filter(resource -> resourcePath.startsWith(resource.resourcePath()))
                .findFirst();

        if (foundResource.isPresent()) {
            final Resource resource = foundResource.get();
            if (resource.resourcePath().length() < resourcePath.length()) {
                final String remainingPath = resourcePath.substring(resource.resourcePath().length());
                return findResource(resource.resources(), remainingPath);
            }
        }
        return foundResource;
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

    private final static class SearchResults {
        private final List<SearchResult> results;

        private SearchResults(final List<SearchResult> results) {
            this.results = results;
        }

        public List<SearchResult> getResults() {
            return results;
        }

        public static SearchResults of(final List<SearchResult> results) {
            return new SearchResults(results);
        }
    }

    private final static class SearchResult {
        private final String label;
        private final String link;

        private SearchResult(final String label, final String uri) {
            this.label = label;
            this.link = "/reflection/resources?uri=" + uri;
        }

        public String getLabel() {
            return label;
        }

        public String getLink() {
            return link;
        }

        public static SearchResult of(final String label, final Resource resource) {
            return new SearchResult(label, resource.resourcePath());
        }
    }
}
