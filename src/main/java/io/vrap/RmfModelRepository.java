package io.vrap;

import com.google.common.collect.Lists;
import io.vrap.rmf.raml.model.RamlDiagnostic;
import io.vrap.rmf.raml.model.RamlModelBuilder;
import io.vrap.rmf.raml.model.RamlModelResult;
import io.vrap.rmf.raml.model.modules.Api;
import org.eclipse.emf.common.util.URI;
import org.raml.v2.api.model.common.ValidationResult;
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
class RmfModelRepository implements Service {
    private final static Logger LOG = LoggerFactory.getLogger(RmfModelRepository.class);

    private final Path filePath;
    private final RamlModelResult<Api> ramlModelResult;

    RmfModelRepository(final Path filePath, final Boolean strict) {
        this.filePath = filePath;
        RamlModelResult<Api> ramlModelResult = null;

        try {
            ramlModelResult = new RamlModelBuilder().buildApi(URI.createURI(filePath.toAbsolutePath().toUri().toString()));
        } catch (Exception e) {
            if (strict) {
                throw e;
            } else {
                LOG.error("could not parse raml files using RMF");
            }
        }

        if (ramlModelResult != null && ramlModelResult.getValidationResults().size() > 0) {
            for (RamlDiagnostic validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
            if (strict) {
                System.exit(1);
            }
        }
        this.ramlModelResult = ramlModelResult;
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
    public List<RamlDiagnostic> getValidationResults() {
        return ramlModelResult != null ? ramlModelResult.getValidationResults(): Lists.newArrayList();
    }

    @Nullable
    public Api getApi() {
        return ramlModelResult != null ? (Api)ramlModelResult.getRootObject(): null;
    }

    public static RmfModelRepository of(final Path filePath) {
        return of(filePath, false);
    }

    public static RmfModelRepository of(final Path filePath, final Boolean strict) {
        return new RmfModelRepository(filePath, strict);
    }
}
