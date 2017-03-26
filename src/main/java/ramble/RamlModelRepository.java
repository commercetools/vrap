package ramble;

import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Handler;
import ratpack.service.Service;
import ratpack.service.StartEvent;

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
    private RamlModelResult ramlModelResult;
    private Handler[] routes;

    RamlModelRepository(final Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public void onStart(final StartEvent event) throws Exception {
        ramlModelResult = new RamlModelBuilder().buildApi(filePath.toFile());
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
        }
        routes = new RamlRouter.Routes(getApi()).getRoutes();
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

    public Handler[] getRoutes() {
        return routes;
    }

    public static RamlModelRepository of(final Path filePath) {
        return new RamlModelRepository(filePath);
    }
}
