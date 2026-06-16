package EngTeacher.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document
@Builder
@Data
public class User {
    @Id
    private String id;
    private String name;
    //TODO user dto
    private String passwordHash;
    @Builder.Default
    private List<Phrase> phrases = new ArrayList<>();
    @Builder.Default
    private List<Session> sessions = new ArrayList<>();
    private UserSettings settings;
}
