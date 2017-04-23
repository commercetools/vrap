package io.vrap.reflection;

import com.google.common.collect.ImmutableMap;
import io.vrap.RamlModelRepository;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import ratpack.func.Block;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Status;

import java.util.Optional;

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

        final Optional<TypeDeclaration> foundTypeDeclaration = api.types().stream()
                .filter(type -> type.name().equals(typeName))
                .findFirst();

        if (foundTypeDeclaration.isPresent()) {
            ctx.byContent(byContent -> {
                final TypeDeclaration typeDeclaration = foundTypeDeclaration.get();
                byContent
                        .json(renderAsJson(ctx, typeDeclaration))
                        .plainText(renderAsDot(ctx, typeDeclaration));
            });
            ;
        } else {
            ctx.getResponse().status(Status.of(404, String.format("Type '%s' not found", typeName))).send();
        }
    }

    private Block renderAsDot(Context ctx, TypeDeclaration typeDeclaration) {
        return () -> ctx.render(handlebarsTemplate(ImmutableMap.of("typeDeclaration", TypeDeclarationView.of(typeDeclaration)), "reflection/Type-Declaration.dot"));
    }

    private Block renderAsJson(final Context ctx, final TypeDeclaration typeDeclaration) {
        return () -> ctx.render(json(TypeDeclarationView.of(typeDeclaration)));
    }
}
