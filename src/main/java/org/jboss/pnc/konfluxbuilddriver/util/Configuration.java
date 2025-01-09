package org.jboss.pnc.konfluxbuilddriver.util;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "konflux-build-driver")
public interface Configuration {

    @WithName("pnc-konflux-tooling")
    String toolingImage();

    String quayRepo();

    @WithName("pipeline-resolver")
    String resolverTarget();

    String indyProxyEnabled();

    String selfBaseUrl();

    Boolean notificationEnabled();
}
