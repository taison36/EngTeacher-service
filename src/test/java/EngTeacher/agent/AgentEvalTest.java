package EngTeacher.agent;

import EngTeacher.agent.config.AgentTestConfig;
import EngTeacher.agent.utils.AgentTestCase.ExpectedCall;
import EngTeacher.agent.utils.AgentTestCase.Fixture;
import EngTeacher.agent.utils.AgentTestCase.Turn;
import EngTeacher.agent.utils.ToolCallRecorder;
import EngTeacher.agent.utils.TrajectoryAssertions;
import EngTeacher.dto.ChatMessageResponseDto;
import EngTeacher.model.Exercise;
import EngTeacher.model.Phrase;
import EngTeacher.model.Session;
import EngTeacher.model.User;
import EngTeacher.model.UserSettings;
import EngTeacher.repo.UserRepository;
import EngTeacher.service.ChatService;
import EngTeacher.service.ExerciseGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest
@Import(AgentTestConfig.class)
@TestPropertySource(properties = {
        "management.tracing.sampling.probability=0.0",
        "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:9999",
        "management.otlp.metrics.export.enabled=false",
        "logging.pattern.console=%5p %-40.40logger{39} : %m%n%wEx"
})
class AgentEvalTest {

    @Autowired private ChatService chatService;
    @Autowired private ChatMemory chatMemory;
    @Autowired private UserRepository userRepository;
    @Autowired private ToolCallRecorder recorder;
    @Autowired private ExerciseGenerationService exerciseGenerationService;

    private String userId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        userId = "agent-test-user-" + UUID.randomUUID();
        sessionId = "agent-test-session-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        try {
            chatMemory.clear(sessionId);
        } catch (Exception e) {
            log.error(e.toString());
        }
        userRepository.deleteById(userId);
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Testfaelle
    // ------------------------------------------------------------------

    @Test
    void correctAnswerOnFirstExercise() {
        run(defaultFixture(), new Turn(
                "If you don't make a decision, you might end up doing nothing with your life",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseCorrect", Map.of("exerciseId", "exercise1"))
                )
        ));
    }

    @Test
    void correctAnswerWithTenseAdaptation() {
        run(defaultFixture(), new Turn(
                "To achieve your goals, it's essential to stick to the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseCorrect", Map.of("exerciseId", "exercise2"))
                )
        ));
    }

    @Test
    void synonymCountsAsIncorrect() {
        run(defaultFixture(), new Turn(
                "To achieve your goals, it's essential to follow the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise2"))
                ),
                List.of("stick to a plan")
        ));
    }

    @Test
    void wrongTargetPhraseIsIncorrect() {
        run(defaultFixture(), new Turn(
                "The professor felt that the student's argument was confusing and would end up doing something of the main issue at hand",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3"))
                ),
                List.of("miss the point")
        ));
    }

    @Test
    void userAsksForAnswerIsIncorrect() {
        run(defaultFixture(), new Turn(
                "I don't know how to fill the professor sentence — what's the answer?",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3")),
                        ExpectedCall.of("regenerateExerciseQuestion", Map.of(
                                "exerciseId", "exercise3",
                                "newQuestion", ExpectedCall.regex(".+")
                        ))
                )
        ));
    }

    @Test
    void metaQuestionDoesNotChangeState() {
        run(defaultFixture(), new Turn(
                "What does 'miss the point' usually mean?",
                List.of(
                        ExpectedCall.of("getCurrentExercises")
                )
        ));
    }

    @Test
    void userRequestsRegenerate() {
        run(defaultFixture(), new Turn(
                "Can you give me a different fill-in for the professor sentence?",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3")),
                        ExpectedCall.of("regenerateExerciseQuestion", Map.of(
                                "exerciseId", "exercise3",
                                "newQuestion", ExpectedCall.regex(".+")
                        ))
                )
        ));
    }

    @Test
    void regenerateAfterSecondWrongAttempt() {
        Turn firstWrongAttempt = new Turn(
                "To achieve your goals, it's essential to follow the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise2"))
                ),
                List.of("stick to a plan")
        );
        Turn secondWrongAttempt = new Turn(
                "To achieve your goals, it's essential to commit to the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise2")),
                        ExpectedCall.of("regenerateExerciseQuestion", Map.of(
                                "exerciseId", "exercise2",
                                "newQuestion", ExpectedCall.regex(".+")
                        ))
                )
        );
        run(defaultFixture(), firstWrongAttempt, secondWrongAttempt);
    }

    /**
     * Initiale Uebungsgenerierung (FR-1). Anders als die Trajektorien-Tests
     * prueft dieser Fall keine Tool-Aufrufe, sondern deterministische
     * Eigenschaften des Generats: pro Phrase genau eine Uebung, jede Frage
     * enthaelt genau eine Luecke ("...") und die Zielphrase erscheint nicht
     * im Klartext in der Frage.
     */
    @Test
    void initialGenerationProducesValidExercises() {
        Fixture fixture = defaultFixture();
        User user = User.builder()
                .id(userId)
                .name(userId)
                .passwordHash("test")
                .phrases(new ArrayList<>(fixture.phrases()))
                .sessions(new ArrayList<>())
                .settings(UserSettings.builder().build())
                .build();

        List<Exercise> exercises = exerciseGenerationService.generate(user, fixture.phrases().size());

        exercises.forEach(e -> log.info("generated: [{}] -> \"{}\"", e.getPhrase().getContent(), e.getQuestion()));

        assertEquals(fixture.phrases().size(), exercises.size(),
                "expected one exercise per phrase");

        Set<String> usedPhraseIds = exercises.stream()
                .map(e -> e.getPhrase().getId())
                .collect(Collectors.toSet());
        assertEquals(fixture.phrases().size(), usedPhraseIds.size(),
                "each phrase must be used exactly once");

        for (Exercise exercise : exercises) {
            String question = exercise.getQuestion();
            assertNotNull(question, "question must not be null");

            String normalized = question.replace("…", "...");
            assertEquals(1, countOccurrences(normalized, "..."),
                    "question must contain exactly one blank: " + question);

            String phrase = exercise.getPhrase().getContent().toLowerCase();
            assertFalse(normalized.toLowerCase().contains(phrase),
                    "question must not reveal the target phrase \"" + phrase + "\": " + question);
        }
    }

    // ------------------------------------------------------------------
    // Ausfuehrung
    // ------------------------------------------------------------------

    private void run(Fixture fixture, Turn... turns) {
        seedUser(fixture);

        for (int i = 0; i < turns.length; i++) {
            Turn turn = turns[i];
            recorder.clear();

            log.info("--- turn {} ---", i + 1);
            log.info("user: {}", turn.userMessage());

            ChatMessageResponseDto response = chatService.processMessage(userId, sessionId, turn.userMessage());

            log.info("agent: {}", response.getAgentResponse());
            recorder.calls().forEach(c -> log.info("tool: {} {}", c.toolName(), c.rawInput()));

            TrajectoryAssertions.assertTrajectory(recorder.calls(), turn.expectedCalls());
            TrajectoryAssertions.assertResponseDoesNotMention(response.getAgentResponse(), turn.forbiddenInResponse());
        }
    }

    private static Fixture defaultFixture() {
        Phrase p1 = Phrase.builder().id("phrase1").content("end up doing something").build();
        Phrase p2 = Phrase.builder().id("phrase2").content("stick to a plan").build();
        Phrase p3 = Phrase.builder().id("phrase3").content("miss the point").build();

        Exercise e1 = Exercise.builder()
                .id("exercise1")
                .phrase(p1)
                .question("If you don't make a decision, you might ... nothing with your life.")
                .build();
        Exercise e2 = Exercise.builder()
                .id("exercise2")
                .phrase(p2)
                .question("To achieve your goals, it's essential to ... and avoid distractions.")
                .build();
        Exercise e3 = Exercise.builder()
                .id("exercise3")
                .phrase(p3)
                .question("The professor felt that the student's argument was confusing and would ... of the main issue at hand.")
                .build();

        return new Fixture(List.of(p1, p2, p3), List.of(e1, e2, e3));
    }

    private void seedUser(Fixture fixture) {
        Session session = Session.builder()
                .id(sessionId)
                .exercises(new ArrayList<>(deepCopyExercises(fixture.exercises())))
                .build();

        User user = User.builder()
                .id(userId)
                .name(userId)
                .passwordHash("test")
                .phrases(new ArrayList<>(fixture.phrases()))
                .sessions(new ArrayList<>(List.of(session)))
                .settings(UserSettings.builder().build())
                .build();

        userRepository.save(user);
    }

    private static List<Exercise> deepCopyExercises(List<Exercise> src) {
        return src.stream()
                .map(e -> Exercise.builder()
                        .id(e.getId())
                        .question(e.getQuestion())
                        .phrase(Phrase.builder()
                                .id(e.getPhrase().getId())
                                .content(e.getPhrase().getContent())
                                .build())
                        .state(e.getState())
                        .build())
                .toList();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
