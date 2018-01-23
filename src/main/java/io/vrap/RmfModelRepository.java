package io.vrap;

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

    RmfModelRepository(final Path filePath) {
        this.filePath = filePath;
        this.ramlModelResult = new RamlModelBuilder().buildApi(URI.createURI(filePath.toAbsolutePath().toUri().toString()));

        if (ramlModelResult.getValidationResults().size() > 0) {
            for(RamlDiagnostic validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
            System.exit(1);
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
    public List<RamlDiagnostic> getValidationResults() {
        return ramlModelResult.getValidationResults();
    }

    @Nullable
    public Api getApi() {
        return (Api)ramlModelResult.getRootObject();
    }

    public static RmfModelRepository of(final Path filePath) {
        return new RmfModelRepository(filePath);
    }
}
