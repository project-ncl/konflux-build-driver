package org.jboss.pnc.konfluxbuilddriver.dto;

import org.jboss.pnc.api.dto.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

// TODO: This is a direct copy of the same class in jvm-build-service. Both need moved to pnc-api to
//      avoid clashes and duplication.
@Builder(builderClassName = "Builder")
@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineNotification(
        String status,
        String buildId,
        Request completionCallback) {

}
