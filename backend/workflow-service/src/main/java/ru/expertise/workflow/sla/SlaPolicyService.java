package ru.expertise.workflow.sla;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.repository.SlaPolicyRepository;

import java.util.List;
import java.util.UUID;

@Service
public class SlaPolicyService {

    private final SlaPolicyRepository slaPolicyRepository;

    public SlaPolicyService(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @Transactional
    public SlaPolicy create(String code, String name, int durationMinutes, boolean businessHoursOnly, JsonNode escalationRules) {
        SlaPolicy policy = new SlaPolicy();
        policy.setCode(code);
        policy.setName(name);
        policy.setDurationMinutes(durationMinutes);
        policy.setBusinessHoursOnly(businessHoursOnly);
        policy.setEscalationRules(escalationRules);
        return slaPolicyRepository.save(policy);
    }

    @Transactional
    public SlaPolicy update(UUID id, String name, int durationMinutes, boolean businessHoursOnly, JsonNode escalationRules) {
        SlaPolicy policy = get(id);
        policy.setName(name);
        policy.setDurationMinutes(durationMinutes);
        policy.setBusinessHoursOnly(businessHoursOnly);
        policy.setEscalationRules(escalationRules);
        return slaPolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public SlaPolicy get(UUID id) {
        return slaPolicyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SlaPolicy " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<SlaPolicy> list() {
        return slaPolicyRepository.findAll();
    }
}
