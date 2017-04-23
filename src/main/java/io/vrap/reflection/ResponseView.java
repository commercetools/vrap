package io.vrap.reflection;

import org.raml.v2.api.model.v10.bodies.Response;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to send {@link Response}s to clients as json.
 */
public class ResponseView {
    private final String code;
    private final List<TypeDeclarationView> body;

    private ResponseView(final String code, List<TypeDeclarationView> body) {
        this.code = code;
        this.body = body;
    }

    public String getCode() {
        return code;
    }

    public List<TypeDeclarationView> getBody() {
        return body;
    }

    public static ResponseView of(final Response response) {
        final List<TypeDeclarationView> body = TypeDeclarationView.of(response.body());
        return new ResponseView(response.code().value(), body);
    }

    public static List<ResponseView> of(final List<Response> responses) {
        return responses.stream()
                .map(ResponseView::of)
                .collect(Collectors.toList());
    }
}
