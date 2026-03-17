package io.jenkins.plugins.oras.parameter;

import static io.jenkins.plugins.oras.parameter.OrasPlatformParameterDefinition.PlatformWrapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlatformWrapperTest {

    @Test
    void shouldParseValue() {

        // OS arch only
        PlatformWrapper wrapper = PlatformWrapper.of("linux/amd64", "1234");
        assertEquals("linux", wrapper.getOs());
        assertEquals("amd64", wrapper.getArchitecture());
        assertNull(wrapper.getVariant(), "Variant should be null");
        assertNull(wrapper.getOsVersion(), "OS version should be null");

        // OS arch and variant
        wrapper = PlatformWrapper.of("linux/arm/v7", "1234");
        assertEquals("linux", wrapper.getOs());
        assertEquals("arm", wrapper.getArchitecture());
        assertEquals("v7", wrapper.getVariant(), "Variant should be v7");
        assertNull(wrapper.getOsVersion(), "OS version should be null");

        // OS, arch and os version
        wrapper = PlatformWrapper.of("linux/amd64:10.0", "1234");
        assertEquals("linux", wrapper.getOs());
        assertEquals("amd64", wrapper.getArchitecture());
        assertNull(wrapper.getVariant(), "Variant should be null");
        assertEquals("10.0", wrapper.getOsVersion(), "OS version should be 10.0");

        // OS, arch, variant and os version
        wrapper = PlatformWrapper.of("linux/arm/v7:12.0", "1234");
        assertEquals("linux", wrapper.getOs());
        assertEquals("arm", wrapper.getArchitecture());
        assertEquals("v7", wrapper.getVariant(), "Variant should be v7");
        assertEquals("12.0", wrapper.getOsVersion(), "OS version should be 12.0");
    }
}
