package org.jboss.pnc.konfluxbuilddriver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.jboss.pnc.konfluxbuilddriver.util.Configuration;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UtilTest {

    @Inject
    Configuration configTest;

    @Test
    public void testMapping() {
        assertEquals("quay.io/redhat-user-workloads-stage/pnc-devel-tenant/pnc-konflux", configTest.quayRepo());
        assertEquals("foobar", configTest.resolverTarget());
    }
}
