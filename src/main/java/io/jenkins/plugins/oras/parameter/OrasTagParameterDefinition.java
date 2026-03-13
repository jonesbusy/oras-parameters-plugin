package io.jenkins.plugins.oras.parameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import land.oras.ContainerRef;
import land.oras.exception.OrasException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parameter definition for ORAS parameters.
 */
public class OrasTagParameterDefinition extends AbstractOrasParameterDefinition {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(OrasTagParameterDefinition.class);

    private String defaultTag;

    @DataBoundConstructor
    public OrasTagParameterDefinition(String name, String description, String containerRef) {
        super(name, description, containerRef);
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    @DataBoundSetter
    public void setDefaultTag(String defaultTag) {
        this.defaultTag = defaultTag;
    }

    @Override
    public @NonNull ParameterValue getDefaultParameterValue() {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        String digest = getDigest(effectiveContainerRef, getEffectiveDefaultTag());
        Map<String, String> annotations = getAnnotationsFromTag(effectiveContainerRef, getEffectiveDefaultTag());
        return new OrasTagParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                getEffectiveDefaultTag(),
                digest,
                annotations);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        String[] tagValues = req.getParameterValues(getName());
        String[] valueParams = req.getParameterValues("value");
        String tag = (tagValues != null && tagValues.length > 0) ? tagValues[0] : getDefaultTag();
        if (valueParams != null && valueParams.length > 0) {
            tag = valueParams[0];
        }
        String digest = getDigest(effectiveContainerRef, tag);
        Map<String, String> annotations = getAnnotationsFromTag(effectiveContainerRef, tag);
        return new OrasTagParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                tag,
                digest,
                annotations);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        String tag = jo.optString("value", getEffectiveDefaultTag());
        String digest = getDigest(effectiveContainerRef, tag);
        Map<String, String> annotations = getAnnotationsFromTag(effectiveContainerRef, tag);
        return new OrasTagParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                tag,
                digest,
                annotations);
    }

    @Override
    public String getEffectiveDefaultTag() {
        return this.defaultTag != null ? this.defaultTag : "latest";
    }

    public TagsResponse getTags() {
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        ContainerRef ref = ContainerRef.parse(containerRef);
        try {
            return TagsResponse.of(client, ref);
        } catch (OrasException e) {
            LOG.warn("Failed to fetch tags. Registry returned status code {}", e.getStatusCode(), e);
            return TagsResponse.empty(client, ref);
        } catch (Exception e) {
            LOG.warn("Failed to fetch tags. Checks logs for more details.", e);
            return TagsResponse.empty(client, ref);
        }
    }

    /**
     * Hold response with tags from the registry
     * @param effectiveReference The reference
     * @param tags List of tags
     */
    public record TagsResponse(String effectiveReference, List<String> tags) {
        public static TagsResponse of(RegistryClient client, ContainerRef containerRef) {
            return new TagsResponse(client.getReference(containerRef), client.getTags(containerRef));
        }

        public static TagsResponse empty(RegistryClient client, ContainerRef containerRef) {
            return new TagsResponse(client.getReference(containerRef), Collections.emptyList());
        }

        public String getEffectiveReference() {
            return effectiveReference;
        }

        public List<String> getTags() {
            return tags;
        }
    }

    @Symbol("orasTagParameter")
    @SuppressWarnings("unused")
    @Extension
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "ORAS Tag Parameter";
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
                return FormValidation.error("Reference is required");
            }
            try {
                UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
                RegistryClient client = new RegistryClient(credentials, insecure);
                ContainerRef ref = ContainerRef.parse(containerRef);
                List<String> tags = client.getTags(ref);
                return FormValidation.ok("Success! Found " + tags.size() + " tags.");

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
