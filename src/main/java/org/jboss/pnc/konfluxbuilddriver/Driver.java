package org.jboss.pnc.konfluxbuilddriver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.api.constants.HttpHeaders;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyService;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyTokenRequestDTO;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyTokenResponseDTO;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildCompleted;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildRequest;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildResponse;
import org.jboss.pnc.konfluxbuilddriver.dto.CancelRequest;
import org.jboss.pnc.konfluxbuilddriver.dto.PipelineNotification;
import org.jboss.pnc.konfluxbuilddriver.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.knative.internal.pkg.apis.Condition;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamBuilder;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunStatusBuilder;
import io.quarkus.logging.Log;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class Driver {

    private static final Logger logger = LoggerFactory.getLogger(Driver.class);

    @Inject
    OidcClient oidcClient;

    @RestClient
    IndyService indyService;

    @Inject
    KubernetesClient client;

    @Inject
    Configuration config;

    @Inject
    ObjectMapper objectMapper;

    URL pipelineRunTemplate;

    void onStart(@Observes StartupEvent ev) {
        try {
            pipelineRunTemplate = IOUtils.resourceToURL("pipeline-run.yaml", Thread.currentThread().getContextClassLoader());
            logger.debug("Driver creating with {}", pipelineRunTemplate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BuildResponse create(BuildRequest buildRequest) {

        logger.info("Establishing token from Indy using clientId {}",
                ConfigProvider.getConfig().getConfigValue("quarkus.oidc.client-id").getValue());
        IndyTokenResponseDTO tokenResponseDTO = indyService.getAuthToken(
                new IndyTokenRequestDTO(buildRequest.repositoryBuildContentId()),
                "Bearer " + getFreshAccessToken());

        Map<String, String> templateProperties = new HashMap<>();
        templateProperties.put("ACCESS_TOKEN", tokenResponseDTO.token());
        templateProperties.put("BUILD_ID", buildRequest.repositoryBuildContentId());
        templateProperties.put("BUILD_SCRIPT", buildRequest.buildScript());
        templateProperties.put("BUILD_TOOL", buildRequest.buildTool());
        templateProperties.put("BUILD_TOOL_VERSION", buildRequest.buildToolVersion());
        templateProperties.put("JAVA_VERSION", buildRequest.javaVersion());
        templateProperties.put("MVN_REPO_DEPENDENCIES_URL", buildRequest.repositoryDependencyUrl());
        templateProperties.put("MVN_REPO_DEPLOY_URL", buildRequest.repositoryDeployUrl());
        templateProperties.put("QUAY_REPO", config.quayRepo());
        templateProperties.put("RECIPE_IMAGE", buildRequest.recipeImage());
        templateProperties.put("PNC_KONFLUX_TOOLING_IMAGE", config.toolingImage());
        templateProperties.put("REVISION", buildRequest.scmRevision());
        templateProperties.put("URL", buildRequest.scmUrl());
        templateProperties.put("caTrustConfigMapName", "custom-ca");
        // TODO: This should be changed to true eventually.
        templateProperties.put("ENABLE_INDY_PROXY", config.indyProxyEnabled());

        if (config.notificationEnabled()) {
            try {
                Request notificationCallback = new Request(
                        Request.Method.PUT,
                        new URI(StringUtils.appendIfMissing(config.selfBaseUrl(), "/") + "internal/completed"),
                        Collections.singletonList(
                                new Request.Header(HttpHeaders.CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)),
                        buildRequest.completionCallback());

                templateProperties.put("NOTIFICATION_CONTEXT", objectMapper.writeValueAsString(notificationCallback));
            } catch (JsonProcessingException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            templateProperties.put("NOTIFICATION_CONTEXT", "");
        }

        // Various ways to create the initial PipelineRun object. We can use an objectmapper,
        // client.getKubernetesSerialization() or the load calls on the Fabric8 objects.
        PipelineRun pipelineRun = client.adapt(TektonClient.class).v1().pipelineRuns().load(pipelineRunTemplate).item();
        pipelineRun = pipelineRun.edit().editOrNewSpec()
                .editPipelineRef()
                .editFirstParam().editOrNewValue().withStringVal(config.resolverTarget()).endValue()
                .endParam()
                .endPipelineRef()
                .addAllToParams(templateProperties.entrySet().stream()
                        .map(t -> new ParamBuilder().withName(t.getKey()).withNewValue(t.getValue()).build()).toList())
                .editFirstTaskRunSpec()
                .editFirstStepSpec()
                .editComputeResources()
                .addToLimits("memory", new Quantity(buildRequest.podMemoryOverride()))
                .addToRequests("memory", new Quantity(buildRequest.podMemoryOverride()))
                .endComputeResources()
                .endStepSpec()
                .endTaskRunSpec()
                .endSpec().build();

        var created = client.resource(pipelineRun).inNamespace(buildRequest.namespace()).create();

        return BuildResponse.builder().namespace(buildRequest.namespace()).pipelineId(created.getMetadata().getName()).build();
    }

    public void cancel(CancelRequest request) {
        var tc = client.adapt(TektonClient.class);
        var pipeline = tc.v1().pipelineRuns().inNamespace(request.namespace()).withName(request.pipelineId()).get();

        logger.info("Retrieved pipeline {}", pipeline.getMetadata().getName());

        // https://tekton.dev/docs/pipelines/pipelineruns/#monitoring-execution-status
        Condition cancelCondition = new Condition();
        cancelCondition.setType("Succeeded");
        cancelCondition.setStatus("False");
        // https://github.com/tektoncd/community/blob/main/teps/0058-graceful-pipeline-run-termination.md
        cancelCondition.setReason("CancelledRunFinally");
        cancelCondition.setMessage("The PipelineRun was cancelled");

        pipeline.setStatus(new PipelineRunStatusBuilder(pipeline.getStatus()).withConditions(cancelCondition).build());

        tc.v1().pipelineRuns().inNamespace(request.namespace()).resource(pipeline).updateStatus();
    }

    /**
     * Get a fresh access token for the service account. This is done because we want to get a super-new token to be used since
     * we're not entirely sure when the http request will be done.
     *
     * @return fresh access token
     */
    public String getFreshAccessToken() {
        return oidcClient.getTokens().await().indefinitely().getAccessToken();
    }

    public void completed(PipelineNotification notification) {

        // TODO: PNC build-driver uses BuildCompleted when notifying the callback.
        String body = Serialization
                .asJson(BuildCompleted.builder().buildId(notification.buildId()).status(notification.status()).build());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(notification.completionCallback().getUri())
                .method(notification.completionCallback().getMethod().name(), HttpRequest.BodyPublishers.ofString(body))
        // TOOD: Timeouts?
        // .timeout(Duration.ofSeconds(requestTimeout))
        ;
        notification.completionCallback().getHeaders().forEach(h -> builder.header(h.getName(), h.getValue()));

        HttpRequest request = builder.build();
        // TODO: Retry? Send async? Some useful mutiny examples from quarkus in https://gist.github.com/cescoffier/e9abce907a1c3d05d70bea3dae6dc3d5
        // TODO: Do we need the bearer token here?
        HttpResponse<String> response;
        try (var httpClient = HttpClient.newHttpClient()) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        Log.infof("Response %s", response);

    }
}
