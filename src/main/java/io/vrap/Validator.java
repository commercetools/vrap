package io.vrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.http.Headers;
import ratpack.http.MediaType;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.internal.DefaultMediaType;
import ratpack.parse.Parse;
import ratpack.path.PathTokens;
import ratpack.service.Service;
import ratpack.util.MultiValueMap;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides validation of ratpack requests and received responses against a raml specification.
 */
public class Validator implements Service {
    private final static Logger LOG = LoggerFactory.getLogger(Validator.class);

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

        @Override
        public String toString() {
            return "ValidationErrors{" +
                    "errors=" + errors +
                    ", responseStatusCode=" + responseStatusCode +
                    ", responseBody=" + responseBody +
                    '}';
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

        @Override
        public String toString() {
            return "ValidationError{" +
                    "kind=" + kind +
                    ", context='" + context + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    /**
     * Validates the body, headers, uri and query parameters of the request of the given context.
     *
     * @param context the context holding the requesz
     * @param body    the request body to validate
     * @param method  the method to validate the request aginst
     * @return validation errors
     */
    public Optional<ValidationErrors> validateRequest(final Context context, final TypedData body, final Method method) {
        final List<ValidationError> errors = new ArrayList<>();

        errors.addAll(validateQueryParameters(context.getRequest(), method));
        errors.addAll(validateRequestHeaders(context.getRequest().getHeaders(), method));
        final Optional<TypeDeclaration> bodyTypeDeclaration = method.body().stream()
                .filter(typeDeclaration -> body.getContentType().getType().equals(typeDeclaration.name())).findFirst();

        errors.addAll(bodyTypeDeclaration
                .map(bodyTypeDecl -> validate(body.getText(), bodyTypeDecl, ValidationKind.body, "request"))
                .orElse(Collections.emptyList()));

        return wrapAndLogErrors(errors);
    }

    private Optional<ValidationErrors> wrapAndLogErrors(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            return Optional.empty();
        } else {
            final ValidationErrors validationErrors = new ValidationErrors(errors);
            LOG.info("Request has errors: {}", validationErrors);

            return Optional.of(validationErrors);
        }
    }

    /**
     * Validates the given received response against the given method.
     *
     * @param ctx              the context, required so that the response body can be parsed
     * @param receivedResponse the received response
     * @param method           the method to validate the body against
     * @return validation errors
     */
    public Optional<ValidationErrors> validateReceivedResponse(final Context ctx, final ReceivedResponse receivedResponse, final Method method) {
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

        if (errors.isEmpty()) {
            return Optional.empty();
        } else {
            Object responseBody = bodyValue;
            try {
                responseBody = ctx.parse(receivedResponse.getBody(), Parse.of(JsonNode.class));
            } catch (Exception e) {
                LOG.debug("Unable to parse body of response", e);
            }
            final ValidationErrors validationErrors = new ValidationErrors(errors, receivedResponse.getStatusCode(), responseBody);
            LOG.info("Received response has errors: {}", validationErrors);

            return Optional.of(validationErrors);
        }
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

        final Map<Pattern, String> queryParamToPattern = method.queryParameters().stream()
                .collect(
                        Collectors.toMap(param -> Pattern.compile(param.name().replaceAll("(<<[^>]+>>)", "[^=]+")), TypeDeclaration::name)
                );

        final Multimap<String, String> queryParams = ArrayListMultimap.create();
        for (final Map.Entry<String, String> queryParam : request.getQueryParams().entrySet()) {
            Optional<Map.Entry<Pattern, String>> patternEntry = queryParamToPattern.entrySet().stream().filter(
                    patternStringEntry -> patternStringEntry.getKey().matcher(queryParam.getKey()).matches()
            ).findFirst();
            if (patternEntry.isPresent()) {
                queryParams.put(patternEntry.get().getValue(), queryParam.getValue());
            } else {
                queryParams.put(queryParam.getKey(), queryParam.getValue());
            }
        }
        for (final String queryParamName : queryParams.keySet()) {
            if (queryParamToDeclaration.containsKey(queryParamName)) {
                final TypeDeclaration queryParamDeclaration = queryParamToDeclaration.get(queryParamName);
                for (final String queryParamValue : queryParams.get(queryParamName)) {
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
