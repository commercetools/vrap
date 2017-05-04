package io.vrap.reflection;

import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;

import java.util.List;

/**
 * Represents an enum type declaration.
 */
class EnumTypeDeclarationView {
    private final String name;
    private final List<String> enumValues;

    private EnumTypeDeclarationView(final String name, final List<String> enumValues) {
        this.name = name;
        this.enumValues = enumValues;
    }

    public String getName() {
        return name;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public static EnumTypeDeclarationView of(final StringTypeDeclaration stringTypeDeclaration) {
        return new EnumTypeDeclarationView(stringTypeDeclaration.name(), stringTypeDeclaration.enumValues());
    }
}
