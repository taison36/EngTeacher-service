package EngTeacher.controller;

import EngTeacher.dto.ChatMessageDto;
import EngTeacher.model.Exercise;
import EngTeacher.model.Session;
import EngTeacher.model.User;
import EngTeacher.security.AuthUtils;
import EngTeacher.service.ExerciseGenerationService;
import EngTeacher.service.SessionCreationService;
import EngTeacher.service.SessionService;
import EngTeacher.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final UserService userService;
    private final SessionService sessionService;
    private final SessionCreationService sessionCreationService;
    private final ExerciseGenerationService exerciseGenerationService;

    @PostMapping
    public Session createSession() {
        User user = userService.getUser(AuthUtils.currentUserId());
        Session createdSession = sessionCreationService.createSession(user);
        userService.save(user);
        return createdSession;
    }

    @GetMapping("/{sessionId}")
    public Session getSession(@PathVariable String sessionId) {
        User user = userService.getUser(AuthUtils.currentUserId());
        return sessionService.getSession(user, sessionId);
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageDto> getSessionMessages(@PathVariable String sessionId) {
        return sessionService.getMessages(sessionId);
    }

    @PostMapping("/{sessionId}/exercise")
    public List<Exercise> createExercises(@PathVariable String sessionId) {
        User user = userService.getUser(AuthUtils.currentUserId());
        Session session = sessionService.getSession(user, sessionId);

        sessionService.deleteDoneExercises(session);
        int neededExerciseQuantity = sessionService.neededExerciseQuantity(session, user.getSettings());

        List<Exercise> createdExercises = exerciseGenerationService.generate(user, neededExerciseQuantity);

        session.getExercises().addAll(createdExercises);
        userService.save(user);

        return createdExercises;
    }
}
