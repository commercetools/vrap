package io.vrap;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.resources.Method;
import io.vrap.rmf.raml.model.resources.Resource;
import io.vrap.rmf.raml.model.resources.UriParameter;
import io.vrap.rmf.raml.model.types.*;
import io.vrap.rmf.raml.model.util.InstanceHelper;
import io.vrap.rmf.raml.validation.InstanceValidator;
import org.eclipse.emf.common.util.Diagnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides validation of ratpack requests and received responses against a raml specification.
 */
public class RmfValidator implements Service {
    private final static Logger LOG = LoggerFactory.getLogger(RmfValidator.class);
    private final static String disableValidationHeader = "X-Vrap-Disable-Validation";

    public enum ValidationKind {
        uriParameter,
        queryParameter,
        header,
        body
    }

    public static class ValidationErrors {
        private final List<ValidationError> errors;
        private final Integer responseStatusCode;
        @JsonRawValue
        private final Object responseBody;
        @JsonRawValue
        private final Object requestBody;

        public ValidationErrors(final List<ValidationError> errors) {
            this(errors, null, null, null);
        }

        public ValidationErrors(final List<ValidationError> errors, final Integer responseStatusCode, final Object responseBody) { this(errors, responseStatusCode, responseBody, null); }

        public ValidationErrors(final List<ValidationError> errors, final Integer responseStatusCode, final Object responseBody, final Object requestBody) {
            this.errors = errors;
            this.responseStatusCode = responseStatusCode;
            this.responseBody = responseBody;
            this.requestBody = requestBody;
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

        public Object getRequestBody() {
            return requestBody;
        }

        @Override
        public String toString() {
            return "ValidationErrors{" +
                    "errors=" + errors +
                    ", responseStatusCode=" + responseStatusCode +
                    ", responseBody=" + responseBody +
                    ", requestBody=" + requestBody +
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

        if (disableValidation(context.getRequest().getHeaders(), ValidationFlag.request)) {
            return wrapAndLogErrors(errors);
        }

        final Api api = context.get(RmfModelRepository.class).getApi();

        errors.addAll(validateQueryParameters(context.getRequest(), method));
        errors.addAll(validateRequestHeaders(context.getRequest().getHeaders(), method));

        final String contentType = Optional.ofNullable(body.getContentType().getType()).orElse(
                !method.getBodies().isEmpty() ? method.getBodies().get(0).getName() :
                        !api.getMediaType().isEmpty() ? api.getMediaType().get(0) : "application/json"
        );
        final Optional<AnyType> bodyTypeDeclaration = Optional.ofNullable(method.getBody(contentType)).map(TypedElement::getType);

        final VrapApp.VrapOptions options = context.get(VrapApp.VrapOptions.class);

        errors.addAll(bodyTypeDeclaration
                .map(bodyTypeDecl -> validateBody(body.getText(), bodyTypeDecl, ValidationKind.body, "request", options.getStrictValidation()))
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

    private boolean disableValidation(Headers headers, ValidationFlag flag)
    {
        return headers.getAll(disableValidationHeader).contains(flag.name());
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
        if (disableValidation(ctx.getRequest().getHeaders(), ValidationFlag.response)) {
            return Optional.empty();
        }

        final MediaType contentType = DefaultMediaType.get(receivedResponse.getHeaders().get("Content-Type"));
        final String statusCode = Integer.toString(receivedResponse.getStatusCode());
        final String bodyValue = receivedResponse.getBody().getText();

        final Optional<AnyType> responseTypeDecl = method.getResponses().stream().filter(response -> response.getStatusCode().equals(statusCode))
                .flatMap(r -> r.getBodies().stream())
                .filter(b -> b.getContentType().equals(contentType.getType()))
                .map(TypedElement::getType)
                .findFirst();

        final VrapApp.VrapOptions options = ctx.get(VrapApp.VrapOptions.class);

        final List<ValidationError> errors = responseTypeDecl.map(typeDeclaration ->
                validateBody(receivedResponse.getBody().getText(), typeDeclaration, ValidationKind.body, "response", options.getStrictValidation()))
                .orElse(Collections.emptyList());

        if (errors.isEmpty()) {
            return Optional.empty();
        } else {
            final ValidationErrors validationErrors = new ValidationErrors(errors, receivedResponse.getStatusCode(), bodyValue);
            LOG.info("Received response has errors: {}", validationErrors);

            return Optional.of(validationErrors);
        }
    }

    private List<ValidationError> validateUriParameters(final PathTokens allPathTokens, final Resource resource) {
        final List<ValidationError> validationErrors = new ArrayList<>();

        final List<UriParameter> uriParamDeclarations = resource.getUriParameters();

        for (final UriParameter uriParamDeclaration : uriParamDeclarations) {
            final String name = uriParamDeclaration.getName();
            if (allPathTokens.containsKey(name)) {
                final List<ValidationError> errors = validate(allPathTokens.get(name), uriParamDeclaration.getType(), ValidationKind.uriParameter, uriParamDeclaration.getName());
                validationErrors.addAll(errors);
            } else if (uriParamDeclaration.getRequired()) {
                validationErrors.add(new ValidationError(ValidationKind.uriParameter, uriParamDeclaration.getName(), "Required uri parameter missing"));
            }
        }
        return validationErrors;
    }

    private List<ValidationError> validateQueryParameters(final Request request, final Method method) {
        final List<ValidationError> validationErrors = new ArrayList<>();
        if (disableValidation(request.getHeaders(), ValidationFlag.queryParameter)) {
            return validationErrors;
        }

        final Map<String, QueryParameter> queryParamToDeclaration = method.getQueryParameters().stream()
                .collect(Collectors.toMap(QueryParameter::getName, Function.identity()));

        final Map<Pattern, String> queryParamToPattern = method.getQueryParameters().stream()
                .collect(
                        Collectors.toMap(param -> Pattern.compile(param.getName().replaceAll("(<<[^>]+>>)", "[^=]+")), QueryParameter::getName)
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
            String declarationMatch = queryParamToDeclaration.keySet().stream().filter(s -> {
                final String pattern = s.startsWith("/") && s.endsWith("/") ? s.substring(1, s.length() - 1) : s;
                return queryParamName.matches(pattern);
            }).findFirst().orElse(null);
            if (queryParamToDeclaration.containsKey(declarationMatch)) {
                final QueryParameter queryParamDeclaration = queryParamToDeclaration.get(declarationMatch);
                for (final String queryParamValue : queryParams.get(queryParamName)) {
                    final String validationContext = String.join("=", queryParamName, queryParamValue);
                    final List<ValidationError> errors = validate(queryParamValue, queryParamDeclaration.getType(), ValidationKind.queryParameter, validationContext);
                    validationErrors.addAll(errors);
                }
            } else {
                validationErrors.add(new ValidationError(ValidationKind.queryParameter, queryParamName, "Unknown query parameter"));
            }
        }
        validationErrors.addAll(method.getQueryParameters().stream()
                .filter(QueryParameter::getRequired)
                .map(QueryParameter::getName)
                .filter(queryParamName -> !queryParams.containsKey(queryParamName))
                .map(queryParamName -> new ValidationError(ValidationKind.queryParameter, queryParamName, "Required query parameter missing"))
                .collect(Collectors.toList()));

        return validationErrors;
    }

    private List<ValidationError> validateRequestHeaders(final Headers headers, final Method method) {
        final List<ValidationError> validationErrors = new ArrayList<>();
        if (disableValidation(headers, ValidationFlag.header)) {
            return validationErrors;
        }

        final Map<String, Header> headerToDeclaration = method.getHeaders().stream()
                .collect(Collectors.toMap(Header::getName, Function.identity()));

        final MultiValueMap<String, String> requestHeaders = headers.asMultiValueMap();
        for (final String headerName : requestHeaders.keySet()) {
            if (headerToDeclaration.containsKey(headerName)) {
                final Header headerDeclaration = headerToDeclaration.get(headerName);
                for (final String headerValue : requestHeaders.getAll(headerName)) {
                    final String validationContext = String.join("=", headerName, headerValue);
                    final List<ValidationError> errors = validate(headerValue, headerDeclaration.getType(), ValidationKind.header, validationContext);
                    validationErrors.addAll(errors);
                }
            }
        }
        validationErrors.addAll(method.getHeaders().stream()
                .filter(Header::getRequired)
                .map(Header::getName)
                .filter(headerName -> !requestHeaders.containsKey(headerName))
                .map(headerName -> new ValidationError(ValidationKind.header, headerName, "Required header missing"))
                .collect(Collectors.toList()));

        return validationErrors;
    }

    /**
     * This method is just a wrapper around the raml parsers {@link InstanceValidator#validate(Annotation)} method
     * which catches any exception and converts it into a {@link ValidationError}.
     *
     * @param payload           the payload to validate
     * @param typeDeclaration   the type declaration used to validate the payload
     * @param kind              the validation kind
     * @param validationContext the validation context
     * @return list of validation errors
     */
    private List<ValidationError> validateBody(final String payload, final AnyType typeDeclaration, final ValidationKind kind, final String validationContext, final Boolean strictValidation) {
        try {
            final Instance instance = InstanceHelper.parseJson(payload);

            final List<Diagnostic> validationResults = new InstanceValidator().validate(instance, typeDeclaration, strictValidation);
            return validationResults.stream().map(r -> new ValidationError(kind, validationContext, r.getMessage())).collect(Collectors.toList());
        } catch (final Exception e) {
            return Collections.singletonList(new ValidationError(kind, validationContext, "Exception in validator:" + e.getMessage()));
        }
    }

    /**
     * This method is just a wrapper around the raml parsers {@link InstanceValidator#validate(Annotation)} method
     * which catches any exception and converts it into a {@link ValidationError}.
     *
     * @param payload           the payload to validate
     * @param typeDeclaration   the type declaration used to validate the payload
     * @param kind              the validation kind
     * @param validationContext the validation context
     * @return list of validation errors
     */
    private List<ValidationError> validate(final String payload, final AnyType typeDeclaration, final ValidationKind kind, final String validationContext) {
        try {
            final Instance instance = InstanceHelper.parse(payload, null);

            final List<Diagnostic> validationResults = new InstanceValidator().validate(instance, typeDeclaration);
            return validationResults.stream().map(r -> new ValidationError(kind, validationContext, r.getMessage())).collect(Collectors.toList());
        } catch (final Exception e) {
            return Collections.singletonList(new ValidationError(kind, validationContext, "Exception in validator:" + e.getMessage()));
        }
    }
}
