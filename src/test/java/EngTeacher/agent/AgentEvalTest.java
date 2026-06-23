package EngTeacher.agent;

import EngTeacher.agent.config.AgentTestConfig;
import EngTeacher.agent.utils.AgentTestCase;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    static Stream<AgentTestCase> testCases() {
        return Stream.of(
                TestCases.correctAnswerOnFirstExercise(),
                TestCases.correctAnswerWithTenseAdaptation(),
                TestCases.synonymCountsAsIncorrect(),
                TestCases.wrongTargetPhraseIsIncorrect(),
                TestCases.userAsksForAnswerIsIncorrect(),
                TestCases.metaQuestionDoesNotChangeState(),
                TestCases.userRequestsRegenerate(),
                TestCases.regenerateAfterSecondWrongAttempt()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void runTrajectory(AgentTestCase tc) {
        seedUser(tc.fixture());

        log.info("=== test case: {} ===", tc.name());
        for (int i = 0; i < tc.turns().size(); i++) {
            AgentTestCase.Turn turn = tc.turns().get(i);
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

    private void seedUser(AgentTestCase.Fixture fixture) {
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
}
