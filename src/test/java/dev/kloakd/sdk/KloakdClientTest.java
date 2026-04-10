package dev.kloakd.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KloakdClientTest {

    @Test
    void builder_requiresApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                Kloakd.builder().organizationId("org-001").build());
    }

    @Test
    void builder_requiresOrganizationId() {
        assertThrows(IllegalArgumentException.class, () ->
                Kloakd.builder().apiKey("sk-test").build());
    }

    @Test
    void builder_blankApiKey_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                Kloakd.builder().apiKey("   ").organizationId("org-001").build());
    }

    @Test
    void builder_success_allNamespacesNonNull() {
        var client = Kloakd.builder()
                .apiKey("sk-test")
                .organizationId("org-001")
                .build();
        assertNotNull(client.evadr());
        assertNotNull(client.webgrph());
        assertNotNull(client.skanyr());
        assertNotNull(client.nexus());
        assertNotNull(client.parlyr());
        assertNotNull(client.fetchyr());
        assertNotNull(client.kolektr());
    }

    @Test
    void defaults_areApplied() {
        assertEquals("https://api.kloakd.dev", Kloakd.DEFAULT_BASE_URL);
        assertEquals(3, Kloakd.DEFAULT_MAX_RETRIES);
    }
}
