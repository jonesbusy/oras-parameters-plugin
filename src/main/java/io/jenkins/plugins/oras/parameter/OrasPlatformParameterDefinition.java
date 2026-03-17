package io.jenkins.plugins.oras.parameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import land.oras.ContainerRef;
import land.oras.ManifestDescriptor;
import land.oras.Platform;
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
public class OrasPlatformParameterDefinition extends AbstractOrasParameterDefinition {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(OrasPlatformParameterDefinition.class);

    @DataBoundConstructor
    public OrasPlatformParameterDefinition(String name, String description, String containerRef) {
        super(name, description, containerRef);
    }

    @Override
    public @NonNull ParameterValue getDefaultParameterValue() {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        Map<String, String> annotations = getAnnotationsFromTag(effectiveContainerRef, getEffectiveDefaultTag());
        List<PlatformWrapper> platforms = getPlatforms().getPlatforms();
        PlatformWrapper defaultPlatform = platforms.isEmpty() ? PlatformWrapper.of(Platform.empty()) : platforms.get(0);
        return new OrasPlatformParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                getEffectiveDefaultTag(),
                defaultPlatform.getDigest(),
                annotations,
                defaultPlatform);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        String tag = getEffectiveDefaultTag();
        String[] platformValues = req.getParameterValues(getName());
        String[] valueParams = req.getParameterValues("value");
        List<PlatformWrapper> platforms = getPlatforms().getPlatforms();
        PlatformWrapper platformWrapper = (platformValues != null && platformValues.length > 0)
                ? PlatformWrapper.select(platforms, platformValues[0])
                : PlatformWrapper.of(Platform.empty());
        if (valueParams != null && valueParams.length > 0) {
            platformWrapper = PlatformWrapper.select(platforms, valueParams[0]);
        }
        String digest = platformWrapper.getDigest();
        Map<String, String> annotations = getAnnotationsFromDigest(effectiveContainerRef, platformWrapper.getDigest());
        return new OrasPlatformParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                tag,
                digest,
                annotations,
                platformWrapper);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        ContainerRef effectiveContainerRef = getEffectiveReference();
        String tag = getEffectiveDefaultTag();
        List<PlatformWrapper> platforms = getPlatforms().getPlatforms();
        PlatformWrapper platformWrapper = PlatformWrapper.select(platforms, jo.optString("value"));
        String digest = platformWrapper.getDigest();
        Map<String, String> annotations = getAnnotationsFromDigest(effectiveContainerRef, digest);
        return new OrasPlatformParameterValue(
                getName(),
                effectiveContainerRef.getRegistry(),
                effectiveContainerRef.getFullRepository(),
                tag,
                digest,
                annotations,
                platformWrapper);
    }

    @Override
    public String getEffectiveDefaultTag() {
        return ContainerRef.parse(containerRef).getTag();
    }

    public PlatformResponse getPlatforms() {
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        ContainerRef ref = ContainerRef.parse(containerRef);
        try {
            return PlatformResponse.of(client, ref);
        } catch (OrasException e) {
            LOG.warn("Failed to fetch platforms. Registry returned status code {}", e.getStatusCode(), e);
            return PlatformResponse.empty(client, ref);
        } catch (Exception e) {
            LOG.warn("Failed to fetch platforms. Checks logs for more details.", e);
            return PlatformResponse.empty(client, ref);
        }
    }

    /**
     * Wrap a platform for Jelly views and serialization
     */
    public static class PlatformWrapper implements Serializable {

        private final String os;
        private final String architecture;
        private final @Nullable String variant;
        private final @Nullable String osVersion;
        private final String digest;

        public PlatformWrapper(
                String os, String architecture, @Nullable String variant, @Nullable String osVersion, String digest) {
            this.os = os;
            this.architecture = architecture;
            this.variant = variant;
            this.osVersion = osVersion;
            this.digest = digest;
        }

        public String getOs() {
            return os;
        }

        public String getArchitecture() {
            return architecture;
        }

        @Nullable
        public String getVariant() {
            return variant;
        }

        @Nullable
        public String getOsVersion() {
            return osVersion;
        }

        public String getDigest() {
            return digest;
        }

        public static PlatformWrapper of(String value, String digest) {
            String[] parts = value.split("/");
            if (parts.length != 2 && parts.length != 3) {
                throw new IllegalArgumentException("Invalid platform parameter: " + value);
            }
            if (parts.length == 2) {
                String osVersion = null;
                if (parts[1].contains(":")) {
                    osVersion = parts[1].split(":")[1];
                    parts[1] = parts[1].split(":")[0];
                }
                return new PlatformWrapper(parts[0], parts[1], null, osVersion, digest);
            }
            String osVersion = null;
            if (parts[2].contains(":")) {
                osVersion = parts[2].split(":")[1];
                parts[2] = parts[2].split(":")[0];
            }
            return new PlatformWrapper(parts[0], parts[1], parts[2], osVersion, digest);
        }

        public static PlatformWrapper of(Platform platform) {
            return new PlatformWrapper(
                    platform.os(), platform.architecture(), platform.variant(), platform.osVersion(), null);
        }

        public static PlatformWrapper of(ManifestDescriptor manifestDescriptor) {
            Platform platform = manifestDescriptor.getPlatform();
            return new PlatformWrapper(
                    platform.os(),
                    platform.architecture(),
                    platform.variant(),
                    platform.osVersion(),
                    manifestDescriptor.getDigest());
        }

        public static PlatformWrapper select(List<PlatformWrapper> values, String value) {
            PlatformWrapper wrapper = of(value, null);
            return values.stream()
                    .filter(v -> v.os.equals(wrapper.os)
                            && v.architecture.equals(wrapper.architecture)
                            && ((v.variant == null && wrapper.variant == null)
                                    || (v.variant != null && v.variant.equals(wrapper.variant)))
                            && ((v.osVersion == null && wrapper.osVersion == null)
                                    || (v.osVersion != null && v.osVersion.equals(wrapper.osVersion))))
                    .findFirst()
                    .orElse(PlatformWrapper.of(Platform.unknown()));
        }

        @SuppressWarnings("unused")
        public String getPlatformIdentifier() {
            return toString();
        }

        @Override
        public @NonNull String toString() {
            if (variant == null) {
                if (osVersion != null) {
                    return "%s/%s:%s".formatted(os, architecture, osVersion);
                }
                return "%s/%s".formatted(os, architecture);
            }
            if (osVersion != null) {
                return "%s/%s/%s:%s".formatted(os, architecture, variant, osVersion);
            }
            return "%s/%s/%s".formatted(os, architecture, variant);
        }
    }

    /**
     * Hold response when getting platforms from Registry
     * @param effectiveReference The reference
     * @param descriptors List of platform
     */
    public record PlatformResponse(String effectiveReference, List<ManifestDescriptor> descriptors) {
        public static PlatformResponse of(RegistryClient client, ContainerRef containerRef) {
            return new PlatformResponse(client.getReference(containerRef), client.getPlatforms(containerRef));
        }

        public static PlatformResponse empty(RegistryClient client, ContainerRef containerRef) {
            return new PlatformResponse(client.getReference(containerRef), Collections.emptyList());
        }

        public String getEffectiveReference() {
            return effectiveReference;
        }

        public List<PlatformWrapper> getPlatforms() {
            return descriptors.stream().map(PlatformWrapper::of).toList();
        }
    }

    @Symbol("orasPlatformParameter")
    @SuppressWarnings("unused")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "ORAS Platform Parameter";
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
                List<ManifestDescriptor> platforms = client.getPlatforms(ref);
                return FormValidation.ok("Success! Found " + platforms.size() + " platforms.");

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
