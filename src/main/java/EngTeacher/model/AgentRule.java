package EngTeacher.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
@Builder
public class AgentRule {
    @Id
    private String id;
    private String description;
    private boolean enabled;
}
