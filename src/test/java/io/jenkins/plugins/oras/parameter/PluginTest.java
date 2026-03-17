package io.jenkins.plugins.oras.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.tasks.Shell;
import java.util.Map;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WireMockTest
class PluginTest {

    @Test
    void testPluginWithFreestyleAndTag(JenkinsRule rule) throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();

        // Check default values
        OrasTagParameterDefinition paramDef = new OrasTagParameterDefinition(
                "ORAS_PARAM", "An ORAS Tag parameter", "ghcr.io/jenkinsci/helm-charts/jenkins");
        job.addProperty(new ParametersDefinitionProperty(paramDef));

        assertEquals("ORAS_PARAM", paramDef.getName());
        assertEquals("An ORAS Tag parameter", paramDef.getDescription());
        assertEquals("ghcr.io/jenkinsci/helm-charts/jenkins", paramDef.getContainerRef());
        assertEquals("latest", paramDef.getEffectiveDefaultTag());

        // Get tags
        OrasTagParameterDefinition.TagsResponse tagsResponse = paramDef.getTags();
        assertFalse(tagsResponse.getTags().isEmpty(), "Tags should not be empty");
        assertEquals("5.6.0", tagsResponse.getTags().get(0));

        OrasTagParameterValue defaultValue = (OrasTagParameterValue) paramDef.getDefaultParameterValue();
        assertEquals("ORAS_PARAM", defaultValue.getName());
        assertEquals("ghcr.io/jenkinsci/helm-charts/jenkins:latest", defaultValue.getImageReference());

        OrasTagParameterValue paramValue = new OrasTagParameterValue(
                "ORAS_PARAM", "ghcr.io/jenkinsci/helm-charts/jenkins", "jenkins", "1.0.0", "sha256:abc123", Map.of());
        assertEquals("ORAS_PARAM", paramValue.getName());
        assertEquals("ghcr.io/jenkinsci/helm-charts/jenkins", paramValue.getRegistry());
        assertEquals("jenkins", paramValue.getRepository());
        assertEquals("1.0.0", paramValue.getTag());
        assertEquals("sha256:abc123", paramValue.getDigest());

        // Roundtrip
        paramDef = new OrasTagParameterDefinition(
                "ORAS_PARAM", "An ORAS parameter", "ghcr.io/jenkinsci/helm-charts/jenkins");
        paramDef.setDefaultTag("5.6.0");
        paramDef.setCredentialsId("my-credentials-id");
        job.removeProperty(ParametersDefinitionProperty.class);
        job.addProperty(new ParametersDefinitionProperty(paramDef));

        rule.configRoundtrip(job);

        ParametersDefinitionProperty p = job.getProperty(ParametersDefinitionProperty.class);
        assertEquals(1, p.getParameterDefinitions().size());
        OrasTagParameterDefinition roundTrippedDef =
                (OrasTagParameterDefinition) p.getParameterDefinitions().get(0);
        assertEquals("ORAS_PARAM", roundTrippedDef.getName());
        assertEquals("An ORAS parameter", roundTrippedDef.getDescription());
        assertEquals("ghcr.io/jenkinsci/helm-charts/jenkins", roundTrippedDef.getContainerRef());
        assertEquals("5.6.0", roundTrippedDef.getDefaultTag());
        assertEquals("my-credentials-id", roundTrippedDef.getCredentialsId());

        // Add shell action that print env vars
        job.getBuildersList().add(new Shell("echo RESOLVED=$ORAS_PARAM"));
        job.getBuildersList().add(new Shell("echo TAG=$ORAS_PARAM_TAG"));
        job.getBuildersList().add(new Shell("echo REGISTRY=$ORAS_PARAM_REGISTRY"));
        job.getBuildersList().add(new Shell("echo REPOSITORY=$ORAS_PARAM_REPOSITORY"));
        job.getBuildersList().add(new Shell("echo DIGEST=$ORAS_PARAM_DIGEST"));
        job.getBuildersList().add(new Shell("echo DESCRIPTION=$ORAS_PARAM_DESCRIPTION"));
        job.getBuildersList().add(new Shell("echo CREATED=$ORAS_PARAM_CREATED"));
        job.getBuildersList().add(new Shell("echo SOURCE=$ORAS_PARAM_SOURCE"));
        job.getBuildersList().add(new Shell("echo TITLE=$ORAS_PARAM_TITLE"));

        // Run the job and assert logs
        FreeStyleBuild build = rule.buildAndAssertSuccess(job);
        rule.assertLogContains(
                "RESOLVED=ghcr.io/jenkinsci/helm-charts/jenkins@sha256:42869d33a9b684f4c960b0256f1c0a444750b6e9fc70d03b929e84d7c728e19a",
                build);
        rule.assertLogContains("TAG=5.6.0", build);
        rule.assertLogContains("REGISTRY=ghcr.io", build);
        rule.assertLogContains("REPOSITORY=jenkinsci/helm-charts/jenkins", build);
        rule.assertLogContains("DIGEST=sha256:42869d33a9b684f4c960b0256f1c0a444750b6e9fc70d03b929e84d7c728e19a", build);
        rule.assertLogContains(
                "DESCRIPTION=Jenkins - Build great things at any scale! As the leading open source automation server, Jenkins provides over 1800 plugins to support building, deploying and automating any project.",
                build);
        rule.assertLogContains("CREATED=2024-09-12T16:01:30Z", build);
        rule.assertLogContains("SOURCE=https://github.com/jenkinsci/jenkins", build);
        rule.assertLogContains("TITLE=jenkins", build);
    }

    @Test
    void testPluginWithFreestyleAndPlatform(JenkinsRule rule) throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();

        // Check default values
        OrasPlatformParameterDefinition paramDef = new OrasPlatformParameterDefinition(
                "ORAS_PARAM", "An ORAS Tag parameter", "ghcr.io/oras-project/oras:v1.3.0");
        job.addProperty(new ParametersDefinitionProperty(paramDef));

        assertEquals("ORAS_PARAM", paramDef.getName());
        assertEquals("An ORAS Tag parameter", paramDef.getDescription());
        assertEquals("ghcr.io/oras-project/oras:v1.3.0", paramDef.getContainerRef());
        assertEquals("v1.3.0", paramDef.getEffectiveDefaultTag());

        // Get platform
        OrasPlatformParameterDefinition.PlatformResponse platformsResponse = paramDef.getPlatforms();
        assertFalse(platformsResponse.getPlatforms().isEmpty(), "Platform should not be empty");
        assertEquals("linux", platformsResponse.getPlatforms().get(0).getOs());
        assertEquals("amd64", platformsResponse.getPlatforms().get(0).getArchitecture());

        // Add shell action that print env vars
        job.getBuildersList().add(new Shell("echo RESOLVED=$ORAS_PARAM"));
        job.getBuildersList().add(new Shell("echo TAG=$ORAS_PARAM_TAG"));
        job.getBuildersList().add(new Shell("echo REGISTRY=$ORAS_PARAM_REGISTRY"));
        job.getBuildersList().add(new Shell("echo REPOSITORY=$ORAS_PARAM_REPOSITORY"));
        job.getBuildersList().add(new Shell("echo DIGEST=$ORAS_PARAM_DIGEST"));
        job.getBuildersList().add(new Shell("echo PLATFORM_OS=$ORAS_PARAM_PLATFORM_OS"));
        job.getBuildersList().add(new Shell("echo PLATFORM_ARCH=$ORAS_PARAM_PLATFORM_ARCH"));

        // Run the job and assert logs
        FreeStyleBuild build = rule.buildAndAssertSuccess(job);
        rule.assertLogContains(
                "RESOLVED=ghcr.io/oras-project/oras@sha256:a3ce6b38d4c510ea9fdc0449b942ea44fb790f157e79b5e7e30b1e7460fe5579",
                build);
        rule.assertLogContains("TAG=v1.3.0", build);
        rule.assertLogContains("REGISTRY=ghcr.io", build);
        rule.assertLogContains("REPOSITORY=oras-project/oras", build);
        rule.assertLogContains("DIGEST=sha256:a3ce6b38d4c510ea9fdc0449b942ea44fb790f157e79b5e7e30b1e7460fe5579", build);
        rule.assertLogContains("PLATFORM_OS=linux", build);
        rule.assertLogContains("PLATFORM_ARCH=amd64", build);
    }

    @Test
    void testPluginWithPipelineJobAndTag(JenkinsRule rule) throws Exception {

        String pipelineScript = """
                pipeline {
                    agent any
                    parameters {
                        orasTagParameter(name: 'ORAS_PARAM', description: 'An ORAS parameter', containerRef: 'ghcr.io/jenkinsci/helm-charts/jenkins', defaultTag: '5.6.0')
                    }
                    stages {
                        stage('Print') {
                            steps {
                                echo "RESOLVED=${params.ORAS_PARAM}"
                                sh '''
                                    echo "TAG=$ORAS_PARAM_TAG"
                                    echo "REGISTRY=$ORAS_PARAM_REGISTRY"
                                    echo "REPOSITORY=$ORAS_PARAM_REPOSITORY"
                                    echo "DIGEST=$ORAS_PARAM_DIGEST"
                                    echo "SOURCE=$ORAS_PARAM_SOURCE"
                                    echo "CREATED=$ORAS_PARAM_CREATED"
                                    echo "DESCRIPTION=$ORAS_PARAM_DESCRIPTION"
                                    echo "TITLE=$ORAS_PARAM_TITLE"
                                 '''
                            }
                        }
                    }
                }
                """;

        // Create job, run it and assert logs
        WorkflowJob job = rule.createProject(WorkflowJob.class);
        job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true));
        WorkflowRun run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains(
                "RESOLVED=ghcr.io/jenkinsci/helm-charts/jenkins@sha256:42869d33a9b684f4c960b0256f1c0a444750b6e9fc70d03b929e84d7c728e19a",
                run);
        rule.assertLogContains("TAG=5.6.0", run);
        rule.assertLogContains("REGISTRY=ghcr.io", run);
        rule.assertLogContains("REPOSITORY=jenkinsci/helm-charts/jenkins", run);
        rule.assertLogContains("DIGEST=sha256:42869d33a9b684f4c960b0256f1c0a444750b6e9fc70d03b929e84d7c728e19a", run);
        rule.assertLogContains(
                "DESCRIPTION=Jenkins - Build great things at any scale! As the leading open source automation server, Jenkins provides over 1800 plugins to support building, deploying and automating any project.",
                run);
        rule.assertLogContains("CREATED=2024-09-12T16:01:30Z", run);
        rule.assertLogContains("SOURCE=https://github.com/jenkinsci/jenkins", run);
        rule.assertLogContains("TITLE=jenkins", run);
    }

    @Test
    void testPluginWithPipelineJobAndPlatform(JenkinsRule rule) throws Exception {

        String pipelineScript = """
                pipeline {
                    agent any
                    parameters {
                        orasPlatformParameter(name: 'ORAS_PARAM', description: 'An ORAS parameter', containerRef: 'ghcr.io/oras-project/oras:v1.3.0')
                    }
                    stages {
                        stage('Print') {
                            steps {
                                echo "RESOLVED=${params.ORAS_PARAM}"
                                sh '''
                                    echo "TAG=$ORAS_PARAM_TAG"
                                    echo "REGISTRY=$ORAS_PARAM_REGISTRY"
                                    echo "REPOSITORY=$ORAS_PARAM_REPOSITORY"
                                    echo "DIGEST=$ORAS_PARAM_DIGEST"
                                    echo "PLATFORM_OS=$ORAS_PARAM_PLATFORM_OS"
                                    echo "PLATFORM_ARCH=$ORAS_PARAM_PLATFORM_ARCH"
                                 '''
                            }
                        }
                    }
                }
                """;

        // Create job, run it and assert logs
        WorkflowJob job = rule.createProject(WorkflowJob.class);
        job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true));
        WorkflowRun run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains(
                "RESOLVED=ghcr.io/oras-project/oras@sha256:a3ce6b38d4c510ea9fdc0449b942ea44fb790f157e79b5e7e30b1e7460fe5579",
                run);
        rule.assertLogContains("TAG=v1.3.0", run);
        rule.assertLogContains("REGISTRY=ghcr.io", run);
        rule.assertLogContains("REPOSITORY=oras-project/oras", run);
        rule.assertLogContains("DIGEST=sha256:a3ce6b38d4c510ea9fdc0449b942ea44fb790f157e79b5e7e30b1e7460fe5579", run);
        rule.assertLogContains("PLATFORM_OS=linux", run);
        rule.assertLogContains("PLATFORM_ARCH=amd64", run);
    }

    @Test
    void testPluginWithPipelineJobAndCatalog(JenkinsRule rule, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {

        // Catalog url
        WireMock wireMock = wmRuntimeInfo.getWireMock();
        String registry = wmRuntimeInfo.getHttpBaseUrl().replaceAll("http://", "");
        wireMock.register(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.okJson("""
                        {
                            "repositories": [
                                "my-repo1",
                                "my-repo1"
                            ]
                        }
                        """)));

        String pipelineScript = """
                pipeline {
                    agent any
                    parameters {
                        orasRepositoryParameter(name: 'ORAS_PARAM', description: 'An ORAS repository', containerRef: '%s', insecure: true)
                    }
                    stages {
                        stage('Print') {
                            steps {
                                echo "RESOLVED=${params.ORAS_PARAM}"
                                sh '''
                                    echo "REGISTRY=$ORAS_PARAM_REGISTRY"
                                    echo "REPOSITORY=$ORAS_PARAM_REPOSITORY"
                                 '''
                            }
                        }
                    }
                }
                """.formatted(registry);

        // Create job, run it and assert logs
        WorkflowJob job = rule.createProject(WorkflowJob.class);
        job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true));
        WorkflowRun run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("RESOLVED=%s/my-repo1".formatted(registry), run);
        rule.assertLogContains("REGISTRY=%s".formatted(registry), run);
        rule.assertLogContains("REPOSITORY=my-repo1", run);
    }
}
