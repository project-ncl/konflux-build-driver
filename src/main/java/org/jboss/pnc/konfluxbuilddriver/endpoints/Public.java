package org.jboss.pnc.konfluxbuilddriver.endpoints;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.konfluxbuilddriver.Driver;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildRequest;
import org.jboss.pnc.konfluxbuilddriver.dto.BuildResponse;
import org.jboss.pnc.konfluxbuilddriver.dto.CancelRequest;
import org.jboss.pnc.konfluxbuilddriver.util.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * Endpoint to start/cancel the build.
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Public {

    private static final Logger logger = LoggerFactory.getLogger(Public.class);

    @Inject
    Driver driver;

    @Inject
    Info info;

    @POST
    @Path("/build")
    @RunOnVirtualThread
    @RolesAllowed({ "pnc-app-konflux-build-driver-user", "pnc-users-admin" })
    public BuildResponse build(BuildRequest buildRequest) {
        logger.info("Requested project build: {}", buildRequest.projectName());
        return driver.create(buildRequest);
    }

    @PUT
    @Path("/cancel")
    @RunOnVirtualThread
    @RolesAllowed({ "pnc-app-konflux-build-driver-user", "pnc-users-admin" })
    public void cancel(CancelRequest cancelRequest) {
        logger.info("Requested cancel: {}", cancelRequest.pipelineId());
        driver.cancel(cancelRequest);
    }

    @Path("/version")
    @GET
    public ComponentVersion getVersion() {
        var r = info.getVersion();
        logger.info("Requested version {}", r);
        return r;
    }
}
