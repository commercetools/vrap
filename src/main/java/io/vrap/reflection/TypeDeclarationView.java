package io.vrap.reflection;

import org.raml.v2.api.model.v10.datamodel.ArrayTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is used to send {@link TypeDeclaration}s as json to the client.
 */
class TypeDeclarationView {
    private final String name;
    private final String type;
    private final String example;
    private final List<TypeDeclarationView> properties;
    private final List<String> enumValues;

    private TypeDeclarationView(final String name, final String type, final String example, final List<TypeDeclarationView> properties) {
        this.name = name;
        this.type = type;
        this.example = example;
        this.properties = properties;
        this.enumValues = null;
    }

    private TypeDeclarationView(final String name, final String type, final List<String> enumValues) {
        this.name = name;
        this.type = type;
        this.example = null;
        this.properties = null;
        this.enumValues = enumValues;
    }

    public String getName() {
        return name.contains("/") ? "regex" : name;
    }

    public String getType() {
        return type;
    }

    public String getExample() {
        return example;
    }

    public String getItemType() {
        return type.replace("[]", "");
    }

    public List<TypeDeclarationView> getProperties() {
        return properties;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public static TypeDeclarationView of(final TypeDeclaration typeDeclaration) {
        List<TypeDeclarationView> properties = Collections.EMPTY_LIST;
        return of(typeDeclaration, properties);
    }

    public static TypeDeclarationView of(final TypeDeclaration typeDeclaration, final List<TypeDeclarationView> properties) {
        return new TypeDeclarationView(typeDeclaration.name(),
                typeDeclaration.type(),
                typeDeclaration.example() != null ? typeDeclaration.example().value() : null, properties);
    }

    public static TypeDeclarationView of(final ObjectTypeDeclaration objectTypeDeclaration) {
        final List<TypeDeclaration> properties = objectTypeDeclaration.properties().stream()
                .filter(TypeDeclarationView::isPrimitiveType)
                .collect(Collectors.toList());
        return of(objectTypeDeclaration, of(properties));
    }

   public static boolean isComplexProperty(final TypeDeclaration typeDeclaration) {
        return typeDeclaration instanceof ObjectTypeDeclaration ||
                (typeDeclaration instanceof ArrayTypeDeclaration
                        && ((ArrayTypeDeclaration) typeDeclaration).items() instanceof ObjectTypeDeclaration);
    }

    public static boolean isPrimitiveType(final TypeDeclaration typeDeclaration) {
        return !isComplexProperty(typeDeclaration);
    }

    public static List<TypeDeclarationView> of(final List<TypeDeclaration> typeDeclarations) {
        return typeDeclarations.stream()
                .map(TypeDeclarationView::of)
                .collect(Collectors.toList());
    }
}
