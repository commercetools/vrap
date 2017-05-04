package io.vrap.reflection;

import com.google.common.collect.ImmutableMap;
import io.vrap.RamlModelRepository;
import org.apache.commons.lang3.StringUtils;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.ArrayTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import ratpack.func.Block;
import ratpack.handlebars.Template;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Status;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ratpack.handlebars.Template.handlebarsTemplate;
import static ratpack.jackson.Jackson.json;

/**
 * This handler provides a introspection api for type declarations.
 */
public class TypeDeclarationHandler implements Handler {


    @Override
    public void handle(Context ctx) throws Exception {
        final Api api = ctx.get(RamlModelRepository.class).getApi();
        final String typeName = ctx.getPathBinding().getPastBinding().replace("[]", "");

        final Optional<TypeDeclaration> foundTypeDeclaration = findTypeDeclaration(api, typeName);

        if (foundTypeDeclaration.isPresent()) {
            ctx.byContent(byContent -> {
                final TypeDeclaration typeDeclaration = foundTypeDeclaration.get();
                byContent
                        .json(renderAsJson(ctx, typeDeclaration))
                        .plainText(renderAsDot(ctx, api, typeDeclaration));
            });
            ;
        } else {
            ctx.getResponse().status(Status.of(404, String.format("Type '%s' not found", typeName))).send();
        }
    }

    private Optional<TypeDeclaration> findTypeDeclaration(final Api api, final String typeName) {
        final String simpleTypeName = typeName.replace("[]", "");
        return api.types().stream()
                    .filter(type -> type.name().equals(simpleTypeName))
                    .findFirst();
    }

    private Block renderAsDot(final Context ctx, final Api api, final TypeDeclaration typeDeclaration) {
        return () -> ctx.render(dotTemplate(api, typeDeclaration));
    }

    private Template dotTemplate(final Api api, final TypeDeclaration typeDeclaration) {
        final List<TypeDeclarationView> complexProperties = new ArrayList<>();
        final TypeDeclarationView typeDeclarationView;
        if (typeDeclaration instanceof ObjectTypeDeclaration) {
            final ObjectTypeDeclaration objectTypeDeclaration = (ObjectTypeDeclaration) typeDeclaration;
            complexProperties.addAll(objectTypeDeclaration.properties().stream()
                    .filter(TypeDeclarationView::isComplexProperty)
                    .map(TypeDeclarationView::of)
                    .collect(Collectors.toList()));
            typeDeclarationView = TypeDeclarationView.of(objectTypeDeclaration);
        } else {
            typeDeclarationView = TypeDeclarationView.of(typeDeclaration);
        }

        final Set<ObjectTypeDeclaration> complexTypeDeclarations = complexProperties.stream()
                .map(t -> findTypeDeclaration(api, t.getItemType()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ObjectTypeDeclaration.class::cast)
                .collect(Collectors.toSet());
        final Set<ObjectTypeDeclaration> subTypes = api.types().stream()
                .filter(ObjectTypeDeclaration.class::isInstance)
                .filter(subType -> subType.parentTypes().stream().anyMatch(complexTypeDeclarations::contains))
                .map(ObjectTypeDeclaration.class::cast)
                .collect(Collectors.toSet());
        complexTypeDeclarations.addAll(subTypes);

        final Set<EnumTypeDeclarationView> enumTypes = complexTypeDeclarations.stream()
                .flatMap(typeDeclaration1 -> typeDeclaration1.properties().stream())
                .filter(StringTypeDeclaration.class::isInstance)
                .map(StringTypeDeclaration.class::cast)
                .filter(stringTypeDeclaration -> stringTypeDeclaration.enumValues().size() > 0)
                .map(EnumTypeDeclarationView::of)
                .collect(Collectors.toSet());

        final List<TypeDeclarationView> complexTypes = complexTypeDeclarations.stream()
                .map(TypeDeclarationView::of)
                .collect(Collectors.toList());
        complexTypes.add(typeDeclarationView);

        final List<Generalization> generalizations = new ArrayList<>();
        for (final TypeDeclaration t : complexTypeDeclarations) {
            generalizations.addAll(t.parentTypes().stream()
                    .filter(complexTypeDeclarations::contains)
                    .map(p -> Generalization.of(p, t))
                    .collect(Collectors.toList()));
        }
        final List<Association> associations = complexTypes.stream()
                .map(t -> Association.of(typeDeclarationView, t, complexProperties))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        final Map<String, Object> model =
                ImmutableMap.of("typeDeclarations", complexTypes,
                        "enumTypes", enumTypes,
                        "associations", associations,
                        "generalizations", generalizations);
        return handlebarsTemplate(model, "reflection/Type-Declaration.dot");
    }

    private Block renderAsJson(final Context ctx, final TypeDeclaration typeDeclaration) {
        return () -> ctx.render(json(TypeDeclarationView.of(typeDeclaration)));
    }


    private static class Generalization {
        private final String source;
        private final String target;

        private Generalization(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getTarget() {
            return target;
        }

        public String getSource() {
            return source;
        }

        public static Generalization of(final TypeDeclaration source, final TypeDeclaration target) {
            return new Generalization(source.name(), target.name());
        }
    }

    private static class Association {
        private final String name;
        private final String source;
        private final String target;
        private final String multiplicity;

        private Association(String name, String source, String target, String multiplicity) {
            this.name = name;
            this.source = source;
            this.target = target;
            this.multiplicity = multiplicity;
        }

        public String getName() {
            return name;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

        public String getMultiplicity() {
            return multiplicity;
        }

        public static List<Association> of(final TypeDeclarationView source, final TypeDeclarationView target, final List<TypeDeclarationView> complexProperties) {
            return complexProperties.stream()
                    .filter(property -> property.getItemType().equals(target.getName()))
                    .map(property -> Association.of(property, source, target))
                    .collect(Collectors.toList());
        }

        private static Association of(final TypeDeclarationView property, final TypeDeclarationView source, final TypeDeclarationView target) {
            final String multiplicity = property.getItemType().equals(property.getType()) ? "0..1" : "*";
            return new Association(property.getName(), source.getName(), target.getName(), multiplicity);
        }
    }
}
