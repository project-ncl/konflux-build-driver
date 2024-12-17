package org.jboss.pnc.konfluxbuilddriver.dto;

import org.jboss.pnc.api.dto.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

@Builder(builderClassName = "Builder")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildRequest(
        String recipeImage,
        String buildTool,
        String buildToolVersion,
        String javaVersion,
        String projectName,
        String scmUrl,
        String scmRevision,
        String buildScript,
        String repositoryDependencyUrl,
        String repositoryDeployUrl,
        String repositoryBuildContentId,
        String namespace,
        String podMemoryOverride,
        // Callback to use for the completion notification
        Request completionCallback) {

}
