package io.vrap.reflection;

import org.raml.v2.api.model.v10.methods.Method;

import java.util.List;

/**
 * This class is used to send {@link Method}s to clients as json..
 */
class MethodView {
    private final String method;
    private final List<TypeDeclarationView> queryParams;
    private final List<TypeDeclarationView> body;
    private final List<ResponseView> responses;

    private MethodView(final String method, final List<TypeDeclarationView> queryParams, List<TypeDeclarationView> body, List<ResponseView> responses) {
        this.method = method;
        this.queryParams = queryParams.isEmpty() ? null : queryParams;
        this.body = body;
        this.responses = responses;
    }

    public List<TypeDeclarationView> getQueryParams() {
        return queryParams;
    }

    public String getMethod() {
        return method;
    }

    public List<TypeDeclarationView> getBody() {
        return body;
    }

    public List<ResponseView> getResponses() {
        return responses;
    }

    public static MethodView of(final Method method) {
        final List<TypeDeclarationView> queryParams = TypeDeclarationView.of(method.queryParameters());
        final List<TypeDeclarationView> body = TypeDeclarationView.of(method.body());
        final List<ResponseView> responses = ResponseView.of(method.responses());

        return new MethodView(method.method(), queryParams, body, responses);
    }
}
