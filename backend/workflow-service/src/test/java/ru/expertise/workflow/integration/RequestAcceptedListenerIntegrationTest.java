package ru.expertise.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.repository.ProcessInstanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publishes a RequestAccepted envelope on the inbound topic and verifies a process instance gets created. */
class RequestAcceptedListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ProcessInstanceRepository processInstanceRepository;

    @Value("${workflow.kafka.topic-request-accepted}")
    private String requestAcceptedTopic;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void startsProcessInstanceFromInboundEvent() throws Exception {
        String code = "INBOUND_" + System.nanoTime();
        createPublishedDefinition(code);

        String businessKey = "REQ-INBOUND-" + System.nanoTime();
        JsonNode envelope = objectMapper.readTree(("""
                {
                  "eventId": "%s",
                  "eventType": "RequestAccepted",
                  "version": 1,
                  "occurredAt": "%s",
                  "correlationId": "corr-%s",
                  "businessKey": "%s",
                  "payload": {
                    "processDefinitionCode": "%s",
                    "attributes": {"requestType": "COMPLEX"}
                  }
                }
                """).formatted(UUID.randomUUID(), Instant.now(), businessKey, businessKey, code));

        kafkaTemplate.send(requestAcceptedTopic, businessKey, envelope).get();

        Optional<ProcessInstance> instance = awaitInstanceFor(businessKey);
        assertThat(instance).isPresent();
        assertThat(instance.get().getStatus().name()).isEqualTo("RUNNING");
    }

    private void createPublishedDefinition(String code) throws Exception {
        String defBody = objectMapper.writeValueAsString(Map.of("code", code, "name", "Inbound test process"));
        String defResponse = mockMvc.perform(post("/api/process-definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID definitionId = UUID.fromString(objectMapper.readTree(defResponse).get("id").asText());

        JsonNode conditionTree = objectMapper.readTree("""
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """);
        String ruleBody = objectMapper.writeValueAsString(Map.of(
                "name", "route-complex", "priority", 0, "conditionTree", conditionTree,
                "targetStepCode", "EXPERT_REVIEW", "targetRole", "COORDINATOR"));
        mockMvc.perform(post("/api/process-definitions/{id}/routing-rules", definitionId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/process-definitions/{id}/publish", definitionId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    private Optional<ProcessInstance> awaitInstanceFor(String businessKey) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            var matches = processInstanceRepository.findAll().stream()
                    .filter(i -> businessKey.equals(i.getBusinessKey()))
                    .findFirst();
            if (matches.isPresent()) {
                return matches;
            }
            Thread.sleep(300);
        }
        return Optional.empty();
    }
}
