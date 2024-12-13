package org.jboss.pnc.konfluxbuilddriver.util;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "konflux-build-driver")
public interface Configuration {

    @WithName("konflux-processor")
    String processor();

    String quayRepo();

    @WithName("pipeline-resolver")
    String resolverTarget();

    String indyProxyEnabled();

    String selfBaseUrl();
}
