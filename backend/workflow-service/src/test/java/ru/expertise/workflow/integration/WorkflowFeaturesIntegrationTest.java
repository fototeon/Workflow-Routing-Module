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
 * Covers the requirements that go beyond the golden path: pauses with their SLA effect (TZ §2),
 * cancellation closing open tasks (TZ §5), configuration changes reaching the journal (TZ §10,
 * REQ-02-002), sub-processes (REQ-02-009) and the analyst's reporting surface (TZ §3, §9).
 */
class WorkflowFeaturesIntegrationTest extends AbstractIntegrationTest {

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
    void suspendingAProcessStopsTheSlaClockAndResumingShiftsTheDeadline() throws Exception {
        UUID definitionId = publishedDefinition("PAUSE_" + System.nanoTime());
        UUID instanceId = startInstance(definitionId, "REQ-PAUSE-" + System.nanoTime());
        JsonNode taskBefore = firstTask(instanceId);
        Instant dueBefore = Instant.parse(taskBefore.get("dueAt").asText());

        mockMvc.perform(post("/api/process-instances/{id}/suspend", instanceId)
                        .with(jwt().authorities(role("MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ожидание документов от заявителя\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Thread.sleep(1100);

        mockMvc.perform(post("/api/process-instances/{id}/resume", instanceId)
                        .with(jwt().authorities(role("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        Instant dueAfter = Instant.parse(firstTask(instanceId).get("dueAt").asText());
        assertThat(dueAfter).isAfter(dueBefore);

        assertThat(eventTypesOf(instanceId)).contains("ProcessSuspended", "ProcessResumed");
    }

    @Test
    void cancellingAProcessAlsoCancelsItsOpenTasks() throws Exception {
        UUID definitionId = publishedDefinition("CANCEL_" + System.nanoTime());
        UUID instanceId = startInstance(definitionId, "REQ-CANCEL-" + System.nanoTime());

        mockMvc.perform(post("/api/process-instances/{id}/cancel", instanceId)
                        .with(jwt().authorities(role("MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Заявитель отозвал заявку\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(firstTask(instanceId).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(eventTypesOf(instanceId)).contains("TaskCancelled", "ProcessStateChanged");
    }

    @Test
    void templateAndSlaChangesAreRecordedInTheJournal() throws Exception {
        String code = "AUDIT_" + System.nanoTime();
        UUID definitionId = publishedDefinition(code);

        String journal = mockMvc.perform(get("/api/process-definitions/{id}/journal", definitionId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> types = objectMapper.readTree(journal).findValuesAsText("eventType");
        assertThat(types).contains("ProcessDefinitionCreated", "RoutingRuleAdded", "ProcessDefinitionPublished");
        assertThat(objectMapper.readTree(journal).get(0).get("actorId").asText()).isNotBlank();
    }

    @Test
    void subProcessIsLinkedToItsParent() throws Exception {
        UUID parentDefinition = publishedDefinition("SUBPARENT_" + System.nanoTime());
        UUID childDefinition = publishedDefinition("SUBCHILD_" + System.nanoTime());
        UUID parentInstance = startInstance(parentDefinition, "REQ-PARENT-" + System.nanoTime());

        String body = objectMapper.writeValueAsString(Map.of(
                "processDefinitionId", childDefinition,
                "businessKey", "REQ-CHILD-" + System.nanoTime(),
                "attributes", Map.of("requestType", "COMPLEX")));

        mockMvc.perform(post("/api/process-instances/{id}/sub-processes", parentInstance)
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentInstanceId").value(parentInstance.toString()));
    }

    @Test
    void analystCanReadTheDashboardAndExportCsv() throws Exception {
        UUID definitionId = publishedDefinition("REPORT_" + System.nanoTime());
        String businessKey = "REQ-REPORT-" + System.nanoTime();
        startInstance(definitionId, businessKey);

        mockMvc.perform(get("/api/analytics/summary").with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcesses").isNumber())
                .andExpect(jsonPath("$.processesByStatus.RUNNING").isNumber());

        String csv = mockMvc.perform(get("/api/process-instances/export").with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("businessKey,processDefinitionCode");
        assertThat(csv).contains(businessKey);

        mockMvc.perform(get("/api/tasks/export").with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk());
    }

    @Test
    void coordinatorOnlySeesCasesTheyAreInvolvedIn() throws Exception {
        UUID definitionId = publishedDefinition("ACCESS_" + System.nanoTime());
        String foreignKey = "REQ-FOREIGN-" + System.nanoTime();
        UUID foreign = startInstance(definitionId, foreignKey);

        // The task of that instance is routed to COORDINATOR, so a coordinator is involved and sees it.
        String visible = mockMvc.perform(get("/api/process-instances")
                        .param("businessKey", foreignKey)
                        .with(jwt().jwt(builder -> builder.subject("coordinator9").claim("preferred_username", "coordinator9"))
                                .authorities(role("COORDINATOR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(visible).get("totalElements").asInt()).isEqualTo(1);

        // A role with no tasks and no involvement at all sees nothing of it.
        String hidden = mockMvc.perform(get("/api/process-instances")
                        .param("businessKey", foreignKey)
                        .with(jwt().jwt(builder -> builder.subject("outsider1").claim("preferred_username", "outsider1"))
                                .authorities(role("EXPERT"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(hidden).get("totalElements").asInt()).isZero();

        mockMvc.perform(get("/api/process-instances/{id}", foreign)
                        .with(jwt().jwt(builder -> builder.subject("outsider1").claim("preferred_username", "outsider1"))
                                .authorities(role("EXPERT"))))
                .andExpect(status().isForbidden());
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

        String ruleBody = objectMapper.writeValueAsString(Map.of(
                "name", "route-complex", "priority", 0,
                "conditionTree", objectMapper.readTree("{\"type\":\"condition\",\"field\":\"requestType\",\"op\":\"EQ\",\"value\":\"COMPLEX\"}"),
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
                "attributes", Map.of("requestType", "COMPLEX")));
        String response = mockMvc.perform(post("/api/process-instances")
                        .with(jwt().authorities(role("COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private JsonNode firstTask(UUID instanceId) throws Exception {
        String response = mockMvc.perform(get("/api/tasks")
                        .param("processInstanceId", instanceId.toString())
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("content").get(0);
    }

    private List<String> eventTypesOf(UUID instanceId) throws Exception {
        String response = mockMvc.perform(get("/api/process-instances/{id}/events", instanceId)
                        .with(jwt().authorities(role("ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).findValuesAsText("eventType");
    }

    private static GrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
