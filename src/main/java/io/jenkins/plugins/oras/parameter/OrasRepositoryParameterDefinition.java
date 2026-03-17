package io.jenkins.plugins.oras.parameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import land.oras.exception.OrasException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parameter definition for ORAS parameters.
 */
public class OrasRepositoryParameterDefinition extends AbstractOrasParameterDefinition {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(OrasRepositoryParameterDefinition.class);

    @DataBoundConstructor
    public OrasRepositoryParameterDefinition(String name, String description, String containerRef) {
        super(name, description, containerRef);
    }

    @Override
    public String getEffectiveDefaultTag() {
        return "latest";
    }

    private String getDefaultRepository(List<String> repositories) {
        if (repositories.isEmpty()) {
            return "";
        }
        return repositories.get(0);
    }

    @Override
    public @NonNull ParameterValue getDefaultParameterValue() {
        List<String> repositories = getRepositories();
        return new OrasRepositoryParameterValue(getName(), containerRef, getDefaultRepository(repositories));
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        String[] valueParams = req.getParameterValues("value");
        String repository;
        List<String> repositories = getRepositories();
        if (valueParams != null && valueParams.length > 0) {
            repository = valueParams[0];
        } else {
            repository = getDefaultRepository(repositories);
        }
        return new OrasRepositoryParameterValue(getName(), containerRef, repository);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        List<String> repositories = getRepositories();
        return new OrasRepositoryParameterValue(
                getName(), containerRef, jo.optString("value", getDefaultRepository(repositories)));
    }

    public List<String> getRepositories() {
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        try {
            return client.getRepositories(containerRef);
        } catch (OrasException e) {
            LOG.warn("Failed to repositories. Registry returned status code {}", e.getStatusCode(), e);
            return List.of();
        } catch (Exception e) {
            LOG.warn("Failed to repositories. Checks logs for more details.", e);
            return List.of();
        }
    }

    @Symbol("orasRepositoryParameter")
    @SuppressWarnings("unused")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "ORAS Repository Parameter";
        }

        /**
         * Test connection to the registry
         */
        @POST
        public FormValidation doTestConnection(
                @AncestorInPath Item item,
                @QueryParameter boolean insecure,
                @QueryParameter String containerRef,
                @QueryParameter String credentialsId) {
            if (item != null) {
                item.checkPermission(Item.CONFIGURE);
            } else {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            }
            if (containerRef == null || containerRef.trim().isEmpty()) {
                return FormValidation.error("Registry is required");
            }
            try {
                UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
                RegistryClient client = new RegistryClient(credentials, insecure);
                List<String> repositories = client.getRepositories(containerRef);
                return FormValidation.ok("Success! Found " + repositories.size() + " repositories.");

            } catch (OrasException e) {
                return FormValidation.error("Connection failed: " + e.getMessage());
            }
        }

        @POST
        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            StandardUsernameCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernameCredentials.class))
                    .includeCurrentValue(credentialsId);
        }
    }
}
