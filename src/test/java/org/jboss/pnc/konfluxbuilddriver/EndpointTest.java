package org.jboss.pnc.konfluxbuilddriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.api.indy.dto.IndyTokenRequestDTO;
import org.jboss.pnc.api.indy.dto.IndyTokenResponseDTO;
import org.jboss.pnc.api.konfluxbuilddriver.dto.BuildRequest;
import org.jboss.pnc.api.konfluxbuilddriver.dto.BuildResponse;
import org.jboss.pnc.konfluxbuilddriver.clients.IndyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@WithKubernetesTestServer
@QuarkusTest
public class EndpointTest {

    @KubernetesTestServer
    KubernetesServer mockServer;

    @InjectMock
    @RestClient
    IndyService indyService;

    @BeforeEach
    public void setup() {
        when(indyService.getAuthToken(any(IndyTokenRequestDTO.class), any(String.class)))
                .thenReturn(new IndyTokenResponseDTO("token-for-builder-pod"));
    }

    @Test
    @TestSecurity(user = "testUser", roles = { "pnc-users-admin" })
    void verify() {
        BuildRequest request = BuildRequest.builder().namespace("default").podMemoryOverride("1Gi").build();
        Response res = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/build");

        assertEquals(200, res.statusCode());
        assertEquals("default", res.as(BuildResponse.class).getNamespace());
    }

    @Test
    void version() {
        var result = RestAssured.given()
                .when()
                .get("/version")
                .as(ComponentVersion.class);
        assertEquals("konflux-build-driver", result.getName());
    }
}
