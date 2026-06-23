package EngTeacher.dto.agent.tools;

public record ExerciseAttempt() {

    public record Correct(
            String exerciseId
    ) {
    }

    public record Incorrect(
            String exerciseId
    ) {
    }

    public record Regenerate(
            String exerciseId,
            String newQuestion
    ) {
    }
}
