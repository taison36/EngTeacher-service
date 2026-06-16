package EngTeacher.dto.agent;

public record EvaluationResult(boolean passed, String violatedRule, String reason) {

    public static EvaluationResult pass() {
        return new EvaluationResult(true, null, null);
    }
}
