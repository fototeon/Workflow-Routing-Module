package ru.expertise.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Everything a running case leaves behind (TZ §10): the configuration journal of a template and
 * the event journal of a process instance, both written by the audit aspect in the same
 * transaction as the change they describe.
 */
class ConfigurationJournalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void templateChangesAreRecordedInTheConfigurationJournal() throws Exception {
        UUID definitionId = publishedDefinition("AUDIT_" + System.nanoTime());

        String journal = mockMvc.perform(get("/api/process-definitions/{id}/journal", definitionId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> types = objectMapper.readTree(journal).findValuesAsText("eventType");
        assertThat(types).contains("ProcessDefinitionCreated", "RoutingRuleAdded", "ProcessDefinitionPublished");
        assertThat(objectMapper.readTree(journal).get(0).get("actorId").asText()).isNotBlank();
    }

    @Test
    void aRunningCaseCollectsItsOwnEventJournal() throws Exception {
        UUID definitionId = publishedDefinition("JOURNAL_" + System.nanoTime());
        UUID instanceId = startInstance(definitionId, "REQ-JOURNAL-" + System.nanoTime());

        String journal = mockMvc.perform(get("/api/process-instances/{id}/events", instanceId)
                        .with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(journal).findValuesAsText("eventType"))
                .contains("ProcessStarted", "TaskCreated");
    }

    /** The routing rule of the seeded template groups two conditions, which only a tree can express. */
    @Test
    void routesOnAGroupedCondition() throws Exception {
        UUID definitionId = publishedDefinition("GROUP_" + System.nanoTime());

        UUID instanceId = startInstance(definitionId, "REQ-GROUP-" + System.nanoTime());
        mockMvc.perform(get("/api/process-instances/{id}", instanceId).with(jwt().authorities(role("ADMIN"))))
                .andExpect(jsonPath("$.currentStepCode").value("EXPERT_REVIEW"));

        // Same template, an amount below the threshold: the AND group no longer holds, nothing routes.
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId,
                "businessKey", "REQ-GROUP-MISS-" + System.nanoTime(),
                "attributes", Map.of("requestType", "COMPLEX", "amount", 10)));
        mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- helpers -------------------------------------------------------------------------------

    private UUID publishedDefinition(String code) throws Exception {
        String slaBody = objectMapper.writeValueAsString(Map.of(
                "code", code, "name", "SLA " + code, "durationMinutes", 480, "businessHoursOnly", false,
                "escalationRules", List.of(Map.of("afterPercent", 80, "escalateToRole", "MANAGER"))));
        String slaResponse = mockMvc.perform(post("/api/sla-policies")
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(slaBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID slaId = UUID.fromString(objectMapper.readTree(slaResponse).get("id").asText());

        String defBody = objectMapper.writeValueAsString(Map.of(
                "code", code, "name", "Process " + code, "slaPolicyId", slaId));
        String defResponse = mockMvc.perform(post("/api/process-definitions")
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(defBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID definitionId = UUID.fromString(objectMapper.readTree(defResponse).get("id").asText());

        JsonNode condition = objectMapper.readTree("""
                {"type":"group","op":"AND","children":[
                  {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"},
                  {"type":"condition","field":"amount","op":"GTE","value":1000}
                ]}
                """);
        String ruleBody = objectMapper.writeValueAsString(Map.of(
                "name", "route-complex", "priority", 10, "conditionTree", condition,
                "targetStepCode", "EXPERT_REVIEW", "targetRole", "COORDINATOR"));
        mockMvc.perform(post("/api/process-definitions/{id}/routing-rules", definitionId)
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(ruleBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/process-definitions/{id}/publish", definitionId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk());
        return definitionId;
    }

    private UUID startInstance(UUID definitionId, String businessKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId,
                "businessKey", businessKey,
                "attributes", Map.of("requestType", "COMPLEX", "amount", 5000)));
        String response = mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private static GrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
