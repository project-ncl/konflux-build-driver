package org.jboss.pnc.konfluxbuilddriver.util;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "konflux-build-driver")
public interface Configuration {

    String quayRepo();

    @WithName("pipeline-resolver")
    String resolverTarget();

    String domainProxyAllowList();

    String domainProxyInternalNonProxyHosts();

    String indyProxyEnabled();

    String selfBaseUrl();

    Boolean notificationEnabled();

    // Currently defaults to empty string but according to
    // https://github.com/eclipse/microprofile-config/issues/446 we need to use an Optional.
    Optional<String> indyProxyClientID();

    Optional<String> indyProxyClientCredential();
}
