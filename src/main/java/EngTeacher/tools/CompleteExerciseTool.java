package EngTeacher.tools;

import EngTeacher.dto.agent.tools.ExerciseAttempt.*;
import EngTeacher.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompleteExerciseTool implements ToolCallback {

    private final ExerciseService exerciseService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("markExercisesCorrect")
                .description("""
                        Mark exercises as CORRECT when user successfully used the target phrases. Expects a list "attempts"
                        Returns in the following format: "Marked exercise with ID: %s as done"
                        """)
                .inputSchema(buildInputSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> input = objectMapper.readValue(toolInput, new TypeReference<>() {
            });

            List<Correct> attempts = objectMapper.convertValue(
                    input.get("attempts"),
                    new TypeReference<>() {
                    }
            );

            exerciseService.markCorrect(attempts);

            return formatAttempts(attempts);

        } catch (JacksonException e) {
            throw new RuntimeException("Failed to parse tool input: " + toolInput, e);
        }
    }

    private String buildInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "attempts": {
                      "type": "array",
                      "description": "List of exercise attempts to mark as correct",
                      "items": {
                        "type": "object",
                        "properties": {
                          "exerciseId": {
                            "type": "string",
                            "description": "ID of the exercise"
                          }
                        },
                        "required": ["exerciseId"]
                      }
                    }
                  },
                  "required": ["attempts"]
                }
                """;
    }

    private String formatAttempts(final List<Correct> attempts) {
        return attempts.stream()
                .map(a -> String.format(
                        "Marked exercise with ID: %s as done",
                        a.exerciseId()
                ))
                .collect(Collectors.joining("\n"));
    }
}
