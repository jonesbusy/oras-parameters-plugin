package io.jenkins.plugins.oras.parameter;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import java.util.HashMap;
import java.util.Map;
import land.oras.utils.Const;

/**
 * Base class for ORAS parameter
 */
public abstract class AbstractOrasParameterValue extends ParameterValue {

    /**
     * The repository name (e.g. "myrepo/myimage")
     */
    protected final String repository;

    /**
     * The registry URL (e.g. "myregistry.azurecr.io")
     */
    protected final String registry;

    /**
     * The digest of the artifact (e.g. "sha256:abc123")
     */
    protected final String digest;

    /**
     * The registry tag (e.g. "latest")
     */
    protected final String tag;

    /**
     * Annotations
     */
    protected final Map<String, String> annotations;

    /**
     * Get the full image reference (e.g., quay.io/org/repo:tag)
     */
    public String getImageReference() {
        if (digest == null) {
            return "%s/%s:%s".formatted(registry, repository, getTag());
        }
        return "%s/%s@%s".formatted(registry, repository, getDigest());
    }

    public AbstractOrasParameterValue(
            String name,
            String registry,
            String repository,
            String tag,
            String digest,
            Map<String, String> annotations) {
        super(name);
        this.repository = repository;
        this.tag = tag;
        this.registry = registry;
        this.digest = digest;
        this.annotations = new HashMap<>(annotations);
    }

    public String getRepository() {
        return repository;
    }

    public String getRegistry() {
        return registry;
    }

    public String getDigest() {
        return digest;
    }

    public String getTag() {
        return tag;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> {
            String paramName = getName();
            if (paramName.equals(name)) {
                return getImageReference();
            }
            if ((paramName + "_REGISTRY").equals(name)) {
                return registry;
            }
            if ((paramName + "_REPOSITORY").equals(name)) {
                return repository;
            }
            if ((paramName + "_TAG").equals(name)) {
                return tag;
            }
            if (digest != null && (paramName + "_DIGEST").equals(name)) {
                return digest;
            }
            String created = annotations.getOrDefault(Const.ANNOTATION_CREATED, null);
            if (created != null) {
                return created;
            }
            String revision = annotations.getOrDefault(Const.ANNOTATION_REVISION, null);
            if (revision != null) {
                return revision;
            }
            String description = annotations.getOrDefault(Const.ANNOTATION_DESCRIPTION, null);
            if (description != null) {
                return description;
            }
            String source = annotations.getOrDefault(Const.ANNOTATION_SOURCE, null);
            if (source != null) {
                return source;
            }
            String title = annotations.getOrDefault(Const.ANNOTATION_TITLE, null);
            if (title != null) {
                return title;
            }
            return null;
        };
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        addVars(this, env);
    }

    @Override
    public String getShortDescription() {
        return getName() + "=" + getImageReference();
    }

    @Override
    public Object getValue() {
        return getImageReference();
    }

    public void addExtraVars(Map<String, String> vars) {
        // Do nothing by default
    }

    public static void addVars(AbstractOrasParameterValue p, Map<String, String> vars) {
        String name = p.getName();
        vars.put(name, p.getImageReference());
        vars.put(name + "_REGISTRY", p.getRegistry());
        vars.put(name + "_REPOSITORY", p.getRepository());
        if (p.getTag() != null) {
            vars.put(name + "_TAG", p.getTag());
        }
        if (p.getDigest() != null) {
            vars.put(name + "_DIGEST", p.getDigest());
        }
        Map<String, String> annotations = p.getAnnotations();
        String created = annotations.get(Const.ANNOTATION_CREATED);
        if (created != null) {
            vars.put(name + "_CREATED", created);
        }
        String description = annotations.get(Const.ANNOTATION_DESCRIPTION);
        if (description != null) {
            vars.put(name + "_DESCRIPTION", description);
        }
        String revision = annotations.get(Const.ANNOTATION_REVISION);
        if (revision != null) {
            vars.put(name + "_REVISION", revision);
        }
        String source = annotations.get(Const.ANNOTATION_SOURCE);
        if (source != null) {
            vars.put(name + "_SOURCE", source);
        }
        String url = annotations.get(Const.ANNOTATION_IMAGE_URL);
        if (url != null) {
            vars.put(name + "_IMAGE_URL", url);
        }
        String version = annotations.get(Const.ANNOTATION_IMAGE_VERSION);
        if (version != null) {
            vars.put(name + "_IMAGE_VERSION", version);
        }
        String title = annotations.get(Const.ANNOTATION_TITLE);
        if (title != null) {
            vars.put(name + "_TITLE", title);
        }
        // Add extra
        p.addExtraVars(vars);
    }
}
