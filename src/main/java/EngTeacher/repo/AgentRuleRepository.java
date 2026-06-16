package EngTeacher.repo;

import EngTeacher.model.AgentRule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AgentRuleRepository extends MongoRepository<AgentRule, String> {
    List<AgentRule> findByEnabledTrue();
}
