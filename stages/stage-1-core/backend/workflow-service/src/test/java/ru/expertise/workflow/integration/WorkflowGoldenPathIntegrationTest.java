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
 * The golden path of TZ §12 as far as this stage implements it, against a real Postgres: create a
 * template, give it a routing rule, publish it, start an instance, complete the task the routing
 * opened, and see the process finish.
 */
class WorkflowGoldenPathIntegrationTest extends AbstractIntegrationTest {

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
    void startsAndCompletesAProcessInstance() throws Exception {
        UUID definitionId = publishedDefinition("GOLDEN_PATH_" + System.nanoTime());

        String businessKey = "REQ-" + System.nanoTime();
        UUID instanceId = startInstance(definitionId, businessKey);
        completeTask(firstTaskIdFor(instanceId));

        mockMvc.perform(get("/api/process-instances/{id}", instanceId).with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void refusesToPublishATemplateWithoutRoutingRules() throws Exception {
        UUID definitionId = draftDefinition("NO_RULES_" + System.nanoTime());

        mockMvc.perform(post("/api/process-definitions/{id}/publish", definitionId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToStartFromAnUnpublishedTemplate() throws Exception {
        UUID definitionId = draftDefinition("DRAFT_START_" + System.nanoTime());
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId, "businessKey", "REQ-DRAFT"));

        mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /** Routing is what picks the first step, so a case no rule matches cannot be started (REQ-02-004). */
    @Test
    void refusesToStartWhenNoRoutingRuleMatches() throws Exception {
        UUID definitionId = publishedDefinition("NO_MATCH_" + System.nanoTime());
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId,
                "businessKey", "REQ-NO-MATCH",
                "attributes", Map.of("requestType", "SIMPLE")));

        mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void analystCannotStartAProcessInstance() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", UUID.randomUUID(), "businessKey", "REQ-FORBIDDEN"));

        mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/process-instances")).andExpect(status().isUnauthorized());
    }

    // --- helpers -------------------------------------------------------------------------------

    private UUID draftDefinition(String code) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("code", code, "name", "Process " + code));
        String response = mockMvc.perform(post("/api/process-definitions")
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID publishedDefinition(String code) throws Exception {
        UUID definitionId = draftDefinition(code);

        JsonNode condition = objectMapper.readTree("""
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        return definitionId;
    }

    private UUID startInstance(UUID definitionId, String businessKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId,
                "businessKey", businessKey,
                "attributes", Map.of("requestType", "COMPLEX")));

        String response = mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentStepCode").value("EXPERT_REVIEW"))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID firstTaskIdFor(UUID instanceId) throws Exception {
        String response = mockMvc.perform(get("/api/tasks")
                        .param("processInstanceId", instanceId.toString())
                        .with(jwt().authorities(role("COORDINATOR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(response).get("content");
        assertThat(content).hasSize(1);
        return UUID.fromString(content.get(0).get("id").asText());
    }

    private void completeTask(UUID taskId) throws Exception {
        mockMvc.perform(post("/api/tasks/{id}/complete", taskId)
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private static GrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
