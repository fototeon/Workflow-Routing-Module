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
import ru.expertise.workflow.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.Instant;
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
 * Exercises the full golden path from TZ §12 against a real Postgres + Kafka (Testcontainers):
 * create SLA policy -> create + configure + publish a process template -> start an instance ->
 * complete its only task -> process reaches COMPLETED -> journal is populated -> the domain events
 * generated along the way are actually delivered to Kafka via the outbox.
 */
class WorkflowGoldenPathIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void startsCompletesAndAuditsAProcessInstance() throws Exception {
        String uniqueCode = "GOLDEN_PATH_" + System.nanoTime();

        UUID slaPolicyId = createSlaPolicy(uniqueCode);
        UUID definitionId = createProcessDefinition(uniqueCode, slaPolicyId);
        addRoutingRule(definitionId);
        publishDefinition(definitionId);

        String businessKey = "REQ-" + System.nanoTime();
        UUID instanceId = startInstance(definitionId, businessKey);

        UUID taskId = firstTaskIdFor(instanceId);
        completeTask(taskId);

        mockMvc.perform(get("/api/process-instances/{id}", instanceId).with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/process-instances/{id}/events", instanceId).with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").exists());

        awaitOutboxEventPublishedFor(businessKey);
    }

    @Test
    void analystCannotStartAProcessInstance() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", UUID.randomUUID(),
                "businessKey", "REQ-FORBIDDEN"));

        mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private UUID createSlaPolicy(String code) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code,
                "name", "Golden path SLA",
                "durationMinutes", 480,
                "businessHoursOnly", false,
                "escalationRules", List.of(Map.of("afterPercent", 80, "escalateToRole", "MANAGER"))));

        String response = mockMvc.perform(post("/api/sla-policies")
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createProcessDefinition(String code, UUID slaPolicyId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code, "name", "Golden path process", "slaPolicyId", slaPolicyId));

        String response = mockMvc.perform(post("/api/process-definitions")
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addRoutingRule(UUID definitionId) throws Exception {
        JsonNode conditionTree = objectMapper.readTree("""
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """);
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "route-complex", "priority", 0, "conditionTree", conditionTree,
                "targetStepCode", "EXPERT_REVIEW", "targetRole", "COORDINATOR"));

        mockMvc.perform(post("/api/process-definitions/{id}/routing-rules", definitionId)
                        .with(jwt().authorities(role("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void publishDefinition(UUID definitionId) throws Exception {
        mockMvc.perform(post("/api/process-definitions/{id}/publish", definitionId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private UUID startInstance(UUID definitionId, String businessKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", definitionId,
                "businessKey", businessKey,
                "attributes", Map.of("requestType", "COMPLEX"),
                "correlationId", "corr-" + businessKey));

        String response = mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private void awaitOutboxEventPublishedFor(String businessKey) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            boolean anyPublished = outboxEventRepository.findAll().stream()
                    .anyMatch(e -> e.getPublishedAt() != null && e.getPayload().toString().contains(businessKey));
            if (anyPublished) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Expected at least one outbox event related to " + businessKey + " to be published within timeout");
    }

    private static GrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
