package io.vrap;

import org.assertj.core.util.Lists;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RamlRatpackPathMapper {
    private static final String DIRECTORY_PATTERN = "[-a-zA-Z0-9@:%_\\+.~#?&=]+";

    public static String map(String pathTemplate) {
        return RamlRatpackPathMapper.map(pathTemplate, Lists.emptyList());
    }

    public static String map(String pathTemplate, List<TypeDeclaration> uriParameters) {
        if (pathTemplate.startsWith("/")) {
            pathTemplate = pathTemplate.substring(1);
        }
        if (!pathTemplate.contains("{") && !pathTemplate.contains("}")) {
            return pathTemplate;
        }


        final List<String> subPaths = Arrays.stream(pathTemplate.split("/")).map(s -> subPath(s, uriParameters)).collect(Collectors.toList());

        return String.join("/", subPaths);
    }

    private static String subPath(String pathTemplate, List<TypeDeclaration> uriParameters) {
        if (!pathTemplate.contains("{") && !pathTemplate.contains("}")) {
            return pathTemplate;
        }

        List<String> allMatches = Lists.newArrayList();
        Matcher m = Pattern.compile("\\{([^}]*)\\}")
                .matcher(pathTemplate);
        while (m.find()) {
            allMatches.add(m.group(1));
        }
        for (final String match : allMatches) {
            String pattern = DIRECTORY_PATTERN;
            for (final TypeDeclaration uriParamDeclaration : uriParameters) {

                if (uriParamDeclaration instanceof StringTypeDeclaration) {
                    String uriPattern = ((StringTypeDeclaration) uriParamDeclaration).pattern();
                    if (uriParamDeclaration.name().equals(match) && uriPattern != null) {
                        pattern = uriPattern.replace("^", "").replace("$", "");
                    }
                }
            }
            pathTemplate = pathTemplate.replace("{" + match + "}", pattern);
        }

        return "::" + pathTemplate;
    }
}
