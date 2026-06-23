package EngTeacher.tools;

import EngTeacher.dto.agent.tools.ExerciseAttempt.Correct;
import EngTeacher.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompleteExerciseTool implements ToolCallback {

    private final ExerciseService exerciseService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("markExerciseCorrect")
                .description("""
                        Mark ONE exercise as CORRECT when the user has successfully produced the target phrase
                        for the fill-in-the-blank question.

                        Be GENEROUS. The goal is for the user to practice the construct, not to reproduce an
                        exact string. Treat the answer as correct when it uses the same core lexical pattern
                        as the target phrase, even if:
                            - the tense, person, or number differs,
                            - inflections differ (gerund vs. infinitive, etc.),
                            - placeholder words ("something", "someone") are filled with concrete content
                              (target "end up doing something" → "end up copying each other's answers" is CORRECT;
                               target "stick to a plan" → "stuck to our plan" is CORRECT),
                            - small function-word swaps (a/the, prepositions that don't change meaning) appear.
                        When in doubt between "close enough" and "wrong", lean CORRECT.

                        To inspect what exercises are currently active in the session, use 'getCurrentExercises' first.
                        To mark multiple exercises, call this tool once per exercise.

                        Returns:
                            "Marked exercise with ID: <exerciseId> as done"
                        """)
                .inputSchema(buildInputSchema())
                .build();
    }

    @Override
    public String call(@NonNull String toolInput) {
        try {
            Correct attempt = objectMapper.readValue(toolInput, Correct.class);

            exerciseService.markCorrect(List.of(attempt));

            return String.format("Marked exercise with ID: %s as done", attempt.exerciseId());

        } catch (JacksonException e) {
            throw new RuntimeException("Failed to parse tool input: " + toolInput, e);
        }
    }

    private String buildInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "exerciseId": {
                      "type": "string",
                      "description": "The unique ID of the exercise the user completed correctly. Must reference an exercise that currently exists in the active session."
                    }
                  },
                  "required": ["exerciseId"]
                }
                """;
    }
}
