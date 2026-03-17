package io.jenkins.plugins.oras.parameter;

import hudson.model.AbstractBuild;
import hudson.util.VariableResolver;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Parameter value for ORAS repositories parameters.
 */
public class OrasRepositoryParameterValue extends AbstractOrasParameterValue {

    @DataBoundConstructor
    public OrasRepositoryParameterValue(String name, String registry, String repository) {
        super(name, registry, repository, null, null, Map.of());
    }

    @Override
    public String getImageReference() {
        return "%s/%s".formatted(registry, repository);
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> {
            String paramName = getName();
            if (paramName.equals(name)) {
                return repository;
            }
            if ((paramName + "_REGISTRY").equals(name)) {
                return registry;
            }
            if ((paramName + "_REPOSITORY").equals(name)) {
                return repository;
            }
            return null;
        };
    }
}
