package io.jenkins.plugins.oras.parameter;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import land.oras.ContainerRef;
import land.oras.Descriptor;
import land.oras.ManifestDescriptor;
import land.oras.Platform;
import land.oras.Registry;
import land.oras.utils.Const;
import org.jfree.util.Log;

/**
 * Registry client wrapping the {@link Registry}
 */
public class RegistryClient {

    private final Registry registry;

    /**
     * Create a new registry client.
     * @param credentials The credentials to use
     */
    public RegistryClient(UsernamePasswordCredentials credentials, boolean insecure) {
        this.registry = buildRegistry(credentials, insecure);
    }

    /**
     * Return all tags of an artifact in the registry.
     * @param containerRef The reference
     */
    public List<String> getTags(ContainerRef containerRef) {
        return registry.getTags(containerRef).tags();
    }

    /**
     * Return all tags of an artifact in the registry.
     * @param containerRef The reference
     */
    public List<String> getTags(ContainerRef containerRef, int n) {
        return registry.getTags(containerRef, n, null).tags();
    }

    /**
     * Return the list of repositories of the given registry
     * @param registry The registry
     * @return List of repositories
     */
    public List<String> getRepositories(String registry) {
        return this.registry.copy(registry).getRepositories().repositories();
    }

    /**
     * Return the platforms descriptor for the given index
     * @param containerRef The reference
     * @return The list of platform
     */
    public List<ManifestDescriptor> getPlatforms(ContainerRef containerRef) {
        return this.registry.getIndex(containerRef).getManifests().stream()
                // Ensure we filter empty or unknown platforms
                .filter(d -> !d.getPlatform().equals(Platform.empty())
                        && !d.getPlatform().equals(Platform.unknown()))
                .toList();
    }

    /**
     * Return the digest of an artifact in the registry.
     * @param containerRef The reference
     * @return The digest, or null if the artifact does not exist or an error occurs
     */
    public String getDigest(ContainerRef containerRef) {
        try {
            return registry.getDescriptor(containerRef).getDigest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the descriptor annotations
     * @param containerRef The reference
     * @return The digest, or null if the artifact does not exist or an error occurs
     */
    public @NonNull Map<String, String> getAnnotations(ContainerRef containerRef) {
        try {
            Descriptor descriptor = registry.getDescriptor(containerRef);
            return switch (descriptor.getMediaType()) {
                case Const.ARTIFACT_MANIFEST_MEDIA_TYPE, Const.DEFAULT_MANIFEST_MEDIA_TYPE ->
                    registry.getManifest(containerRef).getAnnotations();
                case Const.DEFAULT_INDEX_MEDIA_TYPE -> {
                    Map<String, String> annotations =
                            registry.getIndex(containerRef).getAnnotations();
                    yield annotations == null ? Map.of() : annotations;
                }
                default -> Map.of();
            };
        } catch (Exception e) {
            Log.warn("Unable to retrieve annotations for container " + containerRef, e);
            return Map.of();
        }
    }

    public ContainerRef getEffectiveReference(ContainerRef containerRef) {
        return registry.getRegistriesConf().rewrite(containerRef);
    }

    public String getReference(ContainerRef containerRef) {
        ContainerRef ref = getEffectiveReference(containerRef);
        return "%s/%s".formatted(ref.getRegistry(), ref.getFullRepository());
    }

    private Registry buildRegistry(UsernamePasswordCredentials credentials, boolean insecure) {
        Registry.Builder builder = Registry.builder().defaults();
        if (insecure) {
            builder = builder.insecure();
        }
        if (credentials == null) {
            return builder.build();
        }
        return builder.defaults(
                        credentials.getUsername(), credentials.getPassword().getPlainText())
                .build();
    }
}
