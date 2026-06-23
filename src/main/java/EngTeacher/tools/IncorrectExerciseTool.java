package EngTeacher.tools;

import EngTeacher.dto.agent.tools.ExerciseAttempt.Incorrect;
import EngTeacher.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IncorrectExerciseTool implements ToolCallback {

    private final ExerciseService exerciseService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("markExerciseIncorrect")
                .description("""
                        Mark ONE exercise as FAILED when the user's answer does NOT use the target phrase's
                        core lexical pattern. Use this only for clear misses:
                            - a synonym ("stay focused" instead of "stick to a plan"),
                            - a paraphrase or wholly different phrase,
                            - the wrong target phrase from the active list,
                            - grammatically broken English that no longer reflects the target construct,
                            - the user explicitly asks for the answer / gives up.

                        To inspect what exercises are currently active in the session, use 'getCurrentExercises' first.
                        To mark multiple exercises, call this tool once per exercise.

                        Returns:
                            'Marked exercise with ID: <exerciseId> as failed'
                        """)
                .inputSchema(buildInputSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Incorrect attempt = objectMapper.readValue(toolInput, Incorrect.class);

            exerciseService.markIncorrect(List.of(attempt));

            return String.format("Marked exercise with ID: %s as failed", attempt.exerciseId());

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
                      "description": "The unique ID of the exercise the user answered incorrectly. Must reference an exercise that currently exists in the active session."
                    }
                  },
                  "required": ["exerciseId"]
                }
                """;
    }
}
