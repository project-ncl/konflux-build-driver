package org.jboss.pnc.konfluxbuilddriver.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

@Builder(builderClassName = "Builder")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildNotification(
        String buildId,
        String status,
        Set<String> paths) {
}
