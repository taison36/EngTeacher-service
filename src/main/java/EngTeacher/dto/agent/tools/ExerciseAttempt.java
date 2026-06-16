package EngTeacher.dto.agent.tools;

public record ExerciseAttempt() {

    public record Correct(
            String exerciseId
    ) {
    }

    public record Incorrect(
            String exerciseId,
            String newQuestion
    ) {
    }
}
