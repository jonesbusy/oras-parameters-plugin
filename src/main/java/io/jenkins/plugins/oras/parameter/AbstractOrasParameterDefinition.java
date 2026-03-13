package io.jenkins.plugins.oras.parameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.ParameterDefinition;
import hudson.security.ACL;
import java.util.Collections;
import java.util.Map;
import jenkins.model.Jenkins;
import land.oras.ContainerRef;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Base class for ORAS parameter definition
 */
public abstract class AbstractOrasParameterDefinition extends ParameterDefinition {

    protected final String containerRef;
    protected String credentialsId;
    protected boolean insecure;

    protected AbstractOrasParameterDefinition(@NonNull String name, String description, String containerRef) {
        super(name);
        setDescription(description);
        this.containerRef = containerRef;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public abstract String getEffectiveDefaultTag();

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getContainerRef() {
        return containerRef;
    }

    public boolean isInsecure() {
        return insecure;
    }

    @DataBoundSetter
    public void setInsecure(boolean insecure) {
        this.insecure = insecure;
    }

    protected ContainerRef getEffectiveReference() {
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        ContainerRef ref = ContainerRef.parse(containerRef);
        return client.getEffectiveReference(ref);
    }

    /**
     * Get a digest from a ref and cache it if needed
     * @param effectiveRef The ref
     * @param tag The tag to resolve
     * @return The digest
     */
    protected @Nullable String getDigest(ContainerRef effectiveRef, String tag) {
        String digest = OrasParameterCache.getDigest(effectiveRef.withTag(tag).toString());
        if (digest != null) {
            return digest;
        }
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        digest = client.getDigest(effectiveRef.withTag(tag));
        // In case of invalid default tag
        if (digest != null) {
            OrasParameterCache.putDigest(effectiveRef.withTag(tag).toString(), digest);
        }
        return digest;
    }

    protected Map<String, String> getAnnotationsFromTag(ContainerRef effectiveRef, String tag) {
        return getAnnotations(effectiveRef.withTag(tag));
    }

    protected Map<String, String> getAnnotationsFromDigest(ContainerRef effectiveRef, String digest) {
        return getAnnotations(effectiveRef.withDigest(digest));
    }

    private Map<String, String> getAnnotations(ContainerRef effectiveRef) {
        Map<String, String> annotations = OrasParameterCache.getAnnotations(effectiveRef.toString());
        if (annotations != null) {
            return annotations;
        }
        UsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
        RegistryClient client = new RegistryClient(credentials, insecure);
        annotations = client.getAnnotations(effectiveRef);
        OrasParameterCache.putAnnotations(effectiveRef.toString(), annotations);
        return annotations;
    }

    protected static @Nullable UsernamePasswordCredentials resolveCredentials(@Nullable String credentialsId) {
        if (credentialsId == null || credentialsId.trim().isEmpty()) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        UsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));
    }
}
