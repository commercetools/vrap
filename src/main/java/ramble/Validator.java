package ramble;

import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import ratpack.handling.Context;
import ratpack.http.Headers;
import ratpack.http.MediaType;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.internal.DefaultMediaType;
import ratpack.path.PathTokens;
import ratpack.service.Service;
import ratpack.util.MultiValueMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides validation of ratpack requests and received responses against a raml specification.
 */
public class Validator implements Service {

    public enum ValidationKind {
        uriParameter,
        queryParameter,
        header,
        body
    }

    public class ValidationErrors {
        private final List<ValidationError> errors;
        private final Integer responseStatusCode;
        private final Object responseBody;

        public ValidationErrors(final List<ValidationError> errors) {
            this(errors, null, null);
        }

        public ValidationErrors(final List<ValidationError> errors, final Integer responseStatusCode, final Object responseBody) {
            this.errors = errors;
            this.responseStatusCode = responseStatusCode;
            this.responseBody = responseBody;
        }

        public List<ValidationError> getErrors() {
            return errors;
        }

        public Integer getResponseStatusCode() {
            return responseStatusCode;
        }

        public Object getResponseBody() {
            return responseBody;
        }
    }

    public static class ValidationError {
        private final ValidationKind kind;
        private final String context;
        private final String message;

        public ValidationError(final ValidationKind kind, final String context, final String message) {
            this.kind = kind;
            this.context = context;
            this.message = message;
        }

        public ValidationKind getKind() {
            return kind;
        }

        public String getContext() {
            return context;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Validates the headers, uri and query parameters of the request of the given context.
     *
     * @param context  the context holding the requesz
     * @param resource the resource to validate the request against
     * @param method   the method to validate the request aginst
     * @return validation errors
     */
    public Optional<ValidationErrors> validateRequest(final Context context, final Resource resource, final Method method) {
        final List<ValidationError> errors = new ArrayList<>();

        errors.addAll(validateUriParameters(context.getAllPathTokens(), resource));
        errors.addAll(validateQueryParameters(context.getRequest(), method));
        errors.addAll(validateRequestHeaders(context.getRequest().getHeaders(), method));

        return errors.isEmpty() ?
                Optional.empty() :
                Optional.of(new ValidationErrors(errors));
    }

    /**
     * Validates the given request body against the given method.
     *
     * @param body   the request body
     * @param method the method to validate the body against
     * @return validation errors
     */
    public Optional<ValidationErrors> validateRequestBody(final TypedData body, final Method method) {
        final Optional<TypeDeclaration> bodyTypeDeclaration = method.body().stream()
                .filter(typeDeclaration -> body.getContentType().getType().equals(typeDeclaration.name()))
                .findFirst();
        final List<ValidationError> errors = bodyTypeDeclaration.map(typeDeclaration -> validate(body.getText(), typeDeclaration, ValidationKind.body, "request"))
                .orElse(Collections.emptyList());

        return errors.isEmpty() ?
                Optional.empty() :
                Optional.of(new ValidationErrors(errors));
    }

    /**
     * Validates the given received response against the given method.
     *
     * @param receivedResponse the received response
     * @param method           the method to validate the body against
     * @return validation errors
     */
    public Optional<ValidationErrors> validateReceivedResponse(final ReceivedResponse receivedResponse, final Method method) {
        final MediaType contentType = DefaultMediaType.get(receivedResponse.getHeaders().get("Content-Type"));
        final String statusCode = Integer.toString(receivedResponse.getStatusCode());
        final Optional<TypeDeclaration> responseTypeDecl = method.responses().stream().filter(response -> response.code().value().equals(statusCode))
                .flatMap(r -> r.body().stream())
                .filter(b -> b.name().equals(contentType.getType()))
                .findFirst();

        final List<ValidationError> errors = responseTypeDecl.map(typeDeclaration ->
                validate(receivedResponse.getBody().getText(), typeDeclaration, ValidationKind.body, "response"))
                .orElse(Collections.emptyList());
        final String bodyValue = receivedResponse.getBody().getText();

        return errors.isEmpty() ?
                Optional.empty() :
                Optional.of(new ValidationErrors(errors, receivedResponse.getStatusCode(), bodyValue));
    }

    private List<ValidationError> validateUriParameters(final PathTokens allPathTokens, final Resource resource) {
        final List<ValidationError> validationErrors = new ArrayList<>();

        final List<TypeDeclaration> uriParamDeclarations = resource.uriParameters();

        for (final TypeDeclaration uriParamDeclaration : uriParamDeclarations) {
            final String name = uriParamDeclaration.name();
            if (allPathTokens.containsKey(name)) {
                final List<ValidationError> errors = validate(allPathTokens.get(name), uriParamDeclaration, ValidationKind.uriParameter, uriParamDeclaration.name());
                validationErrors.addAll(errors);
            } else if (uriParamDeclaration.required()) {
                validationErrors.add(new ValidationError(ValidationKind.uriParameter, uriParamDeclaration.name(), "Required uri parameter missing"));
            }
        }
        return validationErrors;
    }

    private List<ValidationError> validateQueryParameters(final Request request, final Method method) {
        final List<ValidationError> validationErrors = new ArrayList<>();

        final Map<String, TypeDeclaration> queryParamToDeclaration = method.queryParameters().stream()
                .collect(Collectors.toMap(TypeDeclaration::name, Function.identity()));

        final MultiValueMap<String, String> queryParams = request.getQueryParams();
        for (final String queryParamName : queryParams.keySet()) {
            if (queryParamToDeclaration.containsKey(queryParamName)) {
                final TypeDeclaration queryParamDeclaration = queryParamToDeclaration.get(queryParamName);
                for (final String queryParamValue : queryParams.getAll(queryParamName)) {
                    final String validationContext = String.join("=", queryParamName, queryParamValue);
                    final List<ValidationError> errors = validate(queryParamValue, queryParamDeclaration, ValidationKind.queryParameter, validationContext);
                    validationErrors.addAll(errors);
                }
            } else {
                validationErrors.add(new ValidationError(ValidationKind.queryParameter, queryParamName, "Unknown query parameter"));
            }
        }
        validationErrors.addAll(method.queryParameters().stream()
                .filter(TypeDeclaration::required)
                .map(TypeDeclaration::name)
                .filter(queryParamName -> !queryParams.containsKey(queryParamName))
                .map(queryParamName -> new ValidationError(ValidationKind.queryParameter, queryParamName, "Required query parameter missing"))
                .collect(Collectors.toList()));

        return validationErrors;
    }

    private List<ValidationError> validateRequestHeaders(final Headers headers, final Method method) {
        final List<ValidationError> validationErrors = new ArrayList<>();
        final Map<String, TypeDeclaration> headerToDeclaration = method.headers().stream()
                .collect(Collectors.toMap(TypeDeclaration::name, Function.identity()));

        final MultiValueMap<String, String> requestHeaders = headers.asMultiValueMap();
        for (final String headerName : requestHeaders.keySet()) {
            if (headerToDeclaration.containsKey(headerName)) {
                final TypeDeclaration headerDeclaration = headerToDeclaration.get(headerName);
                for (final String headerValue : requestHeaders.getAll(headerName)) {
                    final String validationContext = String.join("=", headerName, headerValue);
                    final List<ValidationError> errors = validate(headerValue, headerDeclaration, ValidationKind.header, validationContext);
                    validationErrors.addAll(errors);
                }
            }
        }
        validationErrors.addAll(method.headers().stream()
                .filter(TypeDeclaration::required)
                .map(TypeDeclaration::name)
                .filter(headerName -> !requestHeaders.containsKey(headerName))
                .map(headerName -> new ValidationError(ValidationKind.header, headerName, "Required header missing"))
                .collect(Collectors.toList()));

        return validationErrors;
    }

    /**
     * This method is just a wrapper around the raml parsers {@link TypeDeclaration#validate(String)} method
     * which catches any exception and converts it into a {@link ValidationError}.
     *
     * @param payload           the payload to validate
     * @param typeDeclaration   the type declaration used to validate the payload
     * @param kind              the validation kind
     * @param validationContext the validation context
     * @return list of validation errors
     */
    private List<ValidationError> validate(final String payload, final TypeDeclaration typeDeclaration, final ValidationKind kind, final String validationContext) {
        try {
            final List<ValidationResult> validationResults = typeDeclaration.validate(payload);
            return validationResults.stream().map(r -> new ValidationError(kind, validationContext, r.getMessage())).collect(Collectors.toList());
        } catch (final Exception e) {
            return Collections.singletonList(new ValidationError(kind, validationContext, "Exception in validator:" + e.getMessage()));
        }
    }
}
