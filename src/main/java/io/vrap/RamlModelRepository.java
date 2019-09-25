package io.vrap;

import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.service.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * This service provides access to the raml api model.
 */
class RamlModelRepository implements Service {
    private final static Logger LOG = LoggerFactory.getLogger(RamlModelRepository.class);

    private final Path filePath;
    private final RamlModelResult ramlModelResult;

    RamlModelRepository(final Path filePath, final Boolean strict) {
        this.filePath = filePath;
        this.ramlModelResult = new RamlModelBuilder().buildApi(filePath.toFile());

        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
            if (strict) {
                System.exit(1);
            }
        }
    }

    /**
     * Returns the absolute raml file path.
     *
     * @return absolute raml file path
     */

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Returns the raml file name.
     *
     * @return raml file name
     */
    public Path getFileName() {
        return filePath.getFileName();
    }

    /**
     * Returns the parent path of the raml file.
     *
     * @return parent path of the raml file
     */
    public Path getParent() {
        return filePath.getParent();
    }

    @Nonnull
    public List<ValidationResult> getValidationResults() {
        return ramlModelResult.getValidationResults();
    }

    @Nullable
    public Api getApi() {
        return ramlModelResult.getApiV10();
    }

    public static RamlModelRepository of(final Path filePath) {
        return of(filePath, false);
    }

    public static RamlModelRepository of(final Path filePath, final Boolean strict) {
        return new RamlModelRepository(filePath, strict);
    }
}
