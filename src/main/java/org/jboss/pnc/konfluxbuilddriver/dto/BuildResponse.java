package org.jboss.pnc.konfluxbuilddriver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

@Builder(builderClassName = "Builder")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildResponse(
        String pipelineId,
        String namespace) {

}
