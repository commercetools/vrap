package io.vrap.reflection;

import org.raml.v2.api.model.v10.resources.Resource;
import org.raml.v2.api.model.v10.system.types.MarkdownString;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to send {@link Resource}s to clients as json.
 */
final class ResourceView {
    private final String uri;
    private final String name;
    private final String description;
    private final List<TypeDeclarationView> uriParams;
    private final List<MethodView> methods;

    private ResourceView(final String uri, final String name, final String description,
                         final List<TypeDeclarationView> uriParams, final List<MethodView> methods) {
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

    public List<TypeDeclarationView> getUriParams() {
        return uriParams;
    }

    public List<MethodView> getMethods() {
        return methods;
    }

    public static ResourceView of(final Resource resource) {
        final List<TypeDeclarationView> uriParams = resource.uriParameters().stream()
                .map(TypeDeclarationView::of)
                .collect(Collectors.toList());
        final List<MethodView> methods = resource.methods().stream()
                .map(MethodView::of)
                .collect(Collectors.toList());

        final MarkdownString description = resource.description();
        return new ResourceView(resource.resourcePath(), resource.displayName().value(),
                description == null ? null : description.value(), uriParams, methods);
    }
}
