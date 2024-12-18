package org.jboss.pnc.konfluxbuilddriver.endpoints;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.konfluxbuilddriver.Driver;
import org.jboss.pnc.konfluxbuilddriver.dto.PipelineNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * Endpoint to receive build result from the build agent.
 */
@Path("/internal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Internal {

    private static final Logger logger = LoggerFactory.getLogger(Internal.class);

    @Inject
    Driver driver;

    @PUT
    @Path("/completed")
    @RunOnVirtualThread
    @RolesAllowed({ "pnc-app-konflux-build-driver-user", "pnc-users-admin" })
    public void buildExecutionCompleted(PipelineNotification notification) {
        logger.info("Build completed, taskId: {}; status: {}.", notification.buildId(), notification.status());
        driver.completed(notification);
    }
}
