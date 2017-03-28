package ramble;

import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.path.PathTokens;
import ratpack.service.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides validation of ratpack requests and responses against a raml specification.
 */
public class Validator implements Service {

    public enum ValidationKind {
        uriParameter,
        queryParameter,
        body
    }

    public class ValidationErrors {
        private final List<ValidationError> errors;

        public ValidationErrors(final List<ValidationError> errors) {
            this.errors = errors;
        }

        public List<ValidationError> getErrors() {
            return errors;
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
     * Validates the uri and query parameters of the request of the given context.
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
        final List<ValidationError> errors = bodyTypeDeclaration.map(t -> t.validate(body.getText()).stream().map(r -> new ValidationError(ValidationKind.body, "request", r.getMessage())))
                .orElseGet(() -> Stream.empty())
                .collect(Collectors.toList());

        return errors.isEmpty() ?
                Optional.empty() :
                Optional.of(new ValidationErrors(errors));
    }

    private List<ValidationError> validateUriParameters(final PathTokens allPathTokens, final Resource resource) {
        final List<ValidationError> validationResults = new ArrayList<>();

        final List<TypeDeclaration> requiredUriParameters = resource.uriParameters();

        for (final TypeDeclaration uriParameter : requiredUriParameters) {
            final String name = uriParameter.name();
            if (allPathTokens.containsKey(name)) {
                final List<ValidationResult> validate = uriParameter.validate(allPathTokens.get(name));
                List<ValidationError> results = validate.stream()
                        .map(r -> new ValidationError(ValidationKind.uriParameter, name, r.getMessage()))
                        .collect(Collectors.toList());
                validationResults.addAll(results);
            } else if (uriParameter.required()) {
                validationResults.add(new ValidationError(ValidationKind.uriParameter, uriParameter.name(), "Required uri parameter missing"));
            }
        }
        return validationResults;
    }

    private List<ValidationError> validateQueryParameters(final Request request, final Method method) {
        final List<ValidationError> validationResults = new ArrayList<>();

        final Map<String, TypeDeclaration> queryParameters = method.queryParameters().stream()
                .collect(Collectors.toMap(TypeDeclaration::name, Function.identity()));

        for (final String paramName : request.getQueryParams().keySet()) {
            if (queryParameters.containsKey(paramName)) {
                final TypeDeclaration queryParamDeclaration = queryParameters.get(paramName);
                for (final String paramValue : request.getQueryParams().getAll(paramName)) {
                    final List<ValidationResult> validate = queryParamDeclaration.validate(paramValue);
                    final List<ValidationError> results = validate.stream()
                            .map(r -> new ValidationError(ValidationKind.queryParameter, String.join("=", paramName, paramValue), r.getMessage()))
                            .collect(Collectors.toList());
                    validationResults.addAll(results);
                }
            } else {
                validationResults.add(new ValidationError(ValidationKind.queryParameter, paramName, "Unknown query parameter"));
            }
        }
        return validationResults;
    }
}
