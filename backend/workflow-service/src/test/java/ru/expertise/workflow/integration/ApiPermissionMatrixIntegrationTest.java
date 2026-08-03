package ru.expertise.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-by-endpoint access matrix (TZ §12: "права доступа подтверждены тестами для внутренних
 * пользователей"). Each case asserts only whether the role is allowed through — the endpoints work
 * against ids that do not exist, so an authorised call answers 404/409/422 rather than 403.
 */
class ApiPermissionMatrixIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ABSENT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private record Case(String description, String role, boolean allowed, MockHttpServletRequestBuilder request) {
        @Override
        public String toString() {
            return role + " " + (allowed ? "may" : "may not") + " " + description;
        }
    }

    private static Case allow(String role, String description, MockHttpServletRequestBuilder request) {
        return new Case(description, role, true, request);
    }

    private static Case deny(String role, String description, MockHttpServletRequestBuilder request) {
        return new Case(description, role, false, request);
    }

    private static MockHttpServletRequestBuilder startProcess() {
        return post("/api/process-instances").contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinitionId\":\"" + ABSENT + "\",\"businessKey\":\"K\"}");
    }

    private static MockHttpServletRequestBuilder reassign() {
        return post("/api/tasks/" + ABSENT + "/reassign").contentType(MediaType.APPLICATION_JSON)
                .content("{\"toAssignee\":\"user\",\"reason\":\"reason\"}");
    }

    private static MockHttpServletRequestBuilder complete() {
        return post("/api/tasks/" + ABSENT + "/complete").contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static MockHttpServletRequestBuilder cancelProcess() {
        return post("/api/process-instances/" + ABSENT + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"reason\"}");
    }

    private static MockHttpServletRequestBuilder suspendProcess() {
        return post("/api/process-instances/" + ABSENT + "/suspend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"reason\"}");
    }

    private static MockHttpServletRequestBuilder createTemplate() {
        return post("/api/process-definitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"C\",\"name\":\"N\"}");
    }

    private static MockHttpServletRequestBuilder createSlaPolicy() {
        return post("/api/sla-policies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"C\",\"name\":\"N\",\"durationMinutes\":60,\"businessHoursOnly\":false,\"escalationRules\":[]}");
    }

    static Stream<Case> matrix() {
        return Stream.of(
                // Reading is open to every internal role; the attribute model narrows what comes back.
                allow("ANALYST", "list processes", get("/api/process-instances")),
                allow("COORDINATOR", "list processes", get("/api/process-instances")),
                allow("MANAGER", "list processes", get("/api/process-instances")),
                allow("ADMIN", "list processes", get("/api/process-instances")),
                allow("COORDINATOR", "list tasks", get("/api/tasks")),
                allow("ANALYST", "list tasks", get("/api/tasks")),

                // Starting, completing and cancelling: operational roles only.
                deny("ANALYST", "start a process", startProcess()),
                allow("COORDINATOR", "start a process", startProcess()),
                allow("MANAGER", "start a process", startProcess()),
                allow("ADMIN", "start a process", startProcess()),
                deny("ANALYST", "complete a task", complete()),
                allow("COORDINATOR", "complete a task", complete()),
                deny("ANALYST", "cancel a process", cancelProcess()),
                allow("COORDINATOR", "cancel a process", cancelProcess()),
                deny("ANALYST", "suspend a process", suspendProcess()),
                allow("MANAGER", "suspend a process", suspendProcess()),

                // Reassignment is a supervisory action.
                deny("COORDINATOR", "reassign a task", reassign()),
                deny("ANALYST", "reassign a task", reassign()),
                allow("MANAGER", "reassign a task", reassign()),
                allow("ADMIN", "reassign a task", reassign()),

                // Configuration belongs to the administrator.
                deny("COORDINATOR", "create a template", createTemplate()),
                deny("MANAGER", "create a template", createTemplate()),
                deny("ANALYST", "create a template", createTemplate()),
                allow("ADMIN", "create a template", createTemplate()),
                deny("MANAGER", "create an SLA policy", createSlaPolicy()),
                allow("ADMIN", "create an SLA policy", createSlaPolicy()),
                deny("COORDINATOR", "publish a template", post("/api/process-definitions/" + ABSENT + "/publish")),
                allow("ADMIN", "publish a template", post("/api/process-definitions/" + ABSENT + "/publish")),

                // Reporting surface: analyst, manager and admin only.
                deny("COORDINATOR", "read the dashboard", get("/api/analytics/summary")),
                allow("ANALYST", "read the dashboard", get("/api/analytics/summary")),
                allow("MANAGER", "read the dashboard", get("/api/analytics/summary")),
                deny("COORDINATOR", "export processes", get("/api/process-instances/export")),
                allow("ANALYST", "export processes", get("/api/process-instances/export")),
                deny("COORDINATOR", "export tasks", get("/api/tasks/export")),
                allow("ANALYST", "export tasks", get("/api/tasks/export")),
                deny("COORDINATOR", "read the template journal", get("/api/process-definitions/" + ABSENT + "/journal")),
                allow("ADMIN", "read the template journal", get("/api/process-definitions/" + ABSENT + "/journal")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void enforcesTheRoleMatrix(Case testCase) throws Exception {
        int status = mockMvc.perform(testCase.request().with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + testCase.role()))))
                .andReturn().getResponse().getStatus();

        if (testCase.allowed()) {
            assertThat(status).as("%s", testCase).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s", testCase).isEqualTo(403);
        }
    }

    @Test
    void rejectsUnauthenticatedAccessToEveryProtectedArea() throws Exception {
        List<String> protectedPaths = List.of(
                "/api/process-instances", "/api/tasks", "/api/process-definitions",
                "/api/sla-policies", "/api/analytics/summary");

        for (String path : protectedPaths) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
                    .as("unauthenticated %s", path)
                    .isEqualTo(401);
        }
    }

    @Test
    void publishesTheOpenApiContractForEveryPublicOperation() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var paths = objectMapper.readTree(apiDocs).get("paths");
        assertThat(paths.fieldNames()).toIterable().contains(
                "/api/process-definitions", "/api/process-definitions/{id}/publish",
                "/api/process-definitions/{id}/journal", "/api/process-instances",
                "/api/process-instances/{id}/suspend", "/api/process-instances/{id}/resume",
                "/api/process-instances/{id}/sub-processes", "/api/process-instances/export",
                "/api/tasks", "/api/tasks/{id}/complete", "/api/tasks/{id}/reassign", "/api/tasks/export",
                "/api/sla-policies", "/api/analytics/summary");
    }
}
