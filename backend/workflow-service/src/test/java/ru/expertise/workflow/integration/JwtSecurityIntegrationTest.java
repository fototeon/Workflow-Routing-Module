package ru.expertise.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OAuth2 resource server wiring end-to-end: a WireMock server stands in for Keycloak,
 * serving OIDC discovery + JWKS for a real RSA key pair, and a JWT signed with that key is sent as
 * a genuine bearer token (not the {@code jwt()} test-support shortcut used elsewhere).
 */
class JwtSecurityIntegrationTest extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;
    private static RSAKey rsaKey;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        wireMockServer.stubFor(WireMock.get(urlEqualTo("/realms/workflow/.well-known/openid-configuration"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"issuer":"%s","jwks_uri":"%s/realms/workflow/protocol/openid-connect/certs"}
                                """.formatted(issuerUri(), wireMockServer.baseUrl()))));

        wireMockServer.stubFor(WireMock.get(urlEqualTo("/realms/workflow/protocol/openid-connect/certs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toJSONObject().toString())));
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    private static String issuerUri() {
        return wireMockServer.baseUrl() + "/realms/workflow";
    }

    @DynamicPropertySource
    static void overrideIssuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", JwtSecurityIntegrationTest::issuerUri);
    }

    private String signedToken(List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer(issuerUri())
                .claim("preferred_username", "test-user")
                .claim("realm_access", Map.of("roles", roles))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

        mockMvc.perform(get("/api/sla-policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidTokenWithAdminRole() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String token = signedToken(List.of("ADMIN"));

        String body = objectMapper.writeValueAsString(Map.of(
                "code", "JWT_TEST_" + System.nanoTime(),
                "name", "JWT test policy",
                "durationMinutes", 60,
                "businessHoursOnly", true,
                "escalationRules", List.of()));

        mockMvc.perform(post("/api/sla-policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
