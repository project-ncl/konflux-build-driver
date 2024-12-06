package org.jboss.pnc.konfluxbuilddriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyService;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyTokenRequestDTO;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyTokenResponseDTO;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildRequest;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildResponse;
import org.jboss.pnc.konfluxbuilddriver.dto.CancelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.tekton.client.TektonClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.LogCollectingTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
@QuarkusTestResource(value = LogCollectingTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = LogCollectingTestResource.LEVEL, value = "FINE"))
public class DriverTest {

    private static final String namespace = "test-namespace";

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    KubernetesClient client;

    @InjectMock
    @RestClient
    IndyService indyService;

    @Inject
    Driver driver;

    @BeforeEach
    public void setup() {
        when(indyService.getAuthToken(any(IndyTokenRequestDTO.class), any(String.class)))
                .thenReturn(new IndyTokenResponseDTO("token-for-builder-pod"));

        driver.client = this.client;
    }

    @Test
    void verify() {
        BuildRequest request = BuildRequest.builder().namespace(namespace).podMemoryOverride("1Gi").build();
        BuildResponse response = driver.create(request);

        var pipelineRuns = client.adapt(TektonClient.class).v1().pipelineRuns()
                .inNamespace(response.namespace()).list().getItems();

        assertEquals(1, pipelineRuns.size());
        assertEquals(response.pipelineId(), pipelineRuns.getFirst().getMetadata().getName());

        driver.cancel(CancelRequest.builder().namespace(namespace).pipelineId(response.pipelineId()).build());
        pipelineRuns = client.adapt(TektonClient.class).v1().pipelineRuns()
                .inNamespace(response.namespace()).list().getItems();
        assertEquals("False", pipelineRuns.getFirst().getStatus().getConditions().getFirst().getStatus());
        List<LogRecord> logRecords = LogCollectingTestResource.current().getRecords();
        assertTrue(logRecords.stream().anyMatch(r -> LogCollectingTestResource.format(r)
                .contains("Retrieved pipeline run-mw-pipeline--00000000-0000-0000-0000-000000000005")));
    }
}
