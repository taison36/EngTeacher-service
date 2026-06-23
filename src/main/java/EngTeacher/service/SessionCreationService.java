package EngTeacher.service;

import EngTeacher.model.Exercise;
import EngTeacher.model.Session;
import EngTeacher.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionCreationService {

    private final ExerciseGenerationService exerciseGenerationService;
    private final SessionService sessionService;

    public Session createSession(final User user) {
        final Session created = Session.builder()
                .id(UUID.randomUUID().toString())
                .build();
        final int needed = sessionService.neededExerciseQuantity(created, user.getSettings());
        final List<Exercise> exercises = exerciseGenerationService.generate(user, needed);
        created.getExercises().addAll(exercises);
        user.getSessions().add(created);
        return created;
    }
}
