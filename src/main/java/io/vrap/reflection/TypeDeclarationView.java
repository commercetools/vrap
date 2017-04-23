package io.vrap.reflection;

import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to send {@link TypeDeclaration}s as json to the client.
 */
class TypeDeclarationView {
    private final String name;
    private final String type;
    private final String example;
    private final List<TypeDeclarationView> properties;

    private TypeDeclarationView(final String name, final String type, final String example, List<TypeDeclarationView> properties) {
        this.name = name;
        this.type = type;
        this.example = example;
        this.properties = properties;
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

    public List<TypeDeclarationView> getProperties() {
        return properties;
    }

    public static TypeDeclarationView of(final TypeDeclaration typeDeclaration) {
        List<TypeDeclarationView> properties = null;
        if (typeDeclaration instanceof ObjectTypeDeclaration) {
            final ObjectTypeDeclaration objectTypeDeclaration = (ObjectTypeDeclaration) typeDeclaration;
            properties = of(objectTypeDeclaration.properties());
        }
        return new TypeDeclarationView(typeDeclaration.name(),
                typeDeclaration.type(),
                typeDeclaration.example() != null ? typeDeclaration.example().value() : null, properties);
    }

    public static List<TypeDeclarationView> of(final List<TypeDeclaration> typeDeclarations) {
        return typeDeclarations.stream()
                .map(TypeDeclarationView::of)
                .collect(Collectors.toList());
    }
}
