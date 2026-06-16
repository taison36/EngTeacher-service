package EngTeacher.service;

import EngTeacher.model.AgentRule;
import EngTeacher.repo.AgentRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRuleService {

    private final AgentRuleRepository agentRuleRepository;

    public void insert(List<AgentRule> rules) {
        agentRuleRepository.insert(rules);
    }

    public List<AgentRule> getEnabledRules() {
        return agentRuleRepository.findByEnabledTrue();
    }
}
