package EngTeacher.agent;

import EngTeacher.agent.utils.AgentTestCase;
import EngTeacher.model.Exercise;
import EngTeacher.model.Phrase;

import java.util.List;
import java.util.Map;

import static EngTeacher.agent.utils.AgentTestCase.ExpectedCall;
import static EngTeacher.agent.utils.AgentTestCase.Fixture;
import static EngTeacher.agent.utils.AgentTestCase.Turn;

public final class TestCases {

    private TestCases() {}

    public static Fixture defaultFixture() {
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

    public static AgentTestCase correctAnswerOnFirstExercise() {
        Turn turn = new Turn(
                "If you don't make a decision, you might end up doing nothing with your life",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseCorrect", Map.of("exerciseId", "exercise1"))
                )
        );
        return new AgentTestCase("correctAnswerOnFirstExercise", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase correctAnswerWithTenseAdaptation() {
        Turn turn = new Turn(
                "To achieve your goals, it's essential to stick to the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseCorrect", Map.of("exerciseId", "exercise2"))
                )
        );
        return new AgentTestCase("correctAnswerWithTenseAdaptation", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase synonymCountsAsIncorrect() {
        Turn turn = new Turn(
                "To achieve your goals, it's essential to follow the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise2"))
                ),
                List.of("stick to a plan")
        );
        return new AgentTestCase("synonymCountsAsIncorrect", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase wrongTargetPhraseIsIncorrect() {
        Turn turn = new Turn(
                "The professor felt that the student's argument was confusing and would end up doing something of the main issue at hand",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3"))
                ),
                List.of("miss the point")
        );
        return new AgentTestCase("wrongTargetPhraseIsIncorrect", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase userAsksForAnswerIsIncorrect() {
        Turn turn = new Turn(
                "I don't know how to fill the professor sentence — what's the answer?",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3")),
                        ExpectedCall.of("regenerateExerciseQuestion", Map.of(
                                "exerciseId", "exercise3",
                                "newQuestion", ExpectedCall.regex(".+")
                        ))
                )
        );
        return new AgentTestCase("userAsksForAnswerIsIncorrect", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase metaQuestionDoesNotChangeState() {
        Turn turn = new Turn(
                "What does 'miss the point' usually mean?",
                List.of(
                        ExpectedCall.of("getCurrentExercises")
                )
        );
        return new AgentTestCase("metaQuestionDoesNotChangeState", defaultFixture(), List.of(turn));
    }

    public static AgentTestCase regenerateAfterSecondWrongAttempt() {
        Turn turn1 = new Turn(
                "To achieve your goals, it's essential to follow the plan and avoid distractions",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise2"))
                ),
                List.of("stick to a plan")
        );
        Turn turn2 = new Turn(
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
        return new AgentTestCase("regenerateAfterSecondWrongAttempt", defaultFixture(), List.of(turn1, turn2));
    }

    public static AgentTestCase userRequestsRegenerate() {
        Turn turn = new Turn(
                "Can you give me a different fill-in for the professor sentence?",
                List.of(
                        ExpectedCall.of("getCurrentExercises"),
                        ExpectedCall.of("markExerciseIncorrect", Map.of("exerciseId", "exercise3")),
                        ExpectedCall.of("regenerateExerciseQuestion", Map.of(
                                "exerciseId", "exercise3",
                                "newQuestion", ExpectedCall.regex(".+")
                        ))
                )
        );
        return new AgentTestCase("userRequestsRegenerate", defaultFixture(), List.of(turn));
    }
}
