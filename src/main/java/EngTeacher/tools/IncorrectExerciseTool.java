package EngTeacher.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import EngTeacher.dto.agent.tools.ExerciseAttempt.Incorrect;
import EngTeacher.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IncorrectExerciseTool implements ToolCallback {

    private final ExerciseService exerciseService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("markExercisesIncorrect")
                .description("""
                         Mark exercises as INCORRECT when user did not use phrases correctly. Provide new questions for failed exercises.
                         Returns in the following format: "Updated Exercise with ID: %s with new fill-in-the-blank question: \"s\" ":
                        """)
                .inputSchema(buildInputSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> input = objectMapper.readValue(toolInput, new TypeReference<>() {
            });

            List<Incorrect> attempts = objectMapper.convertValue(
                    input.get("attempts"),
                    new TypeReference<>() {
                    }
            );

            exerciseService.markIncorrect(attempts);

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
                      "description": "List of exercise attempts to mark as incorrect with new questions",
                      "items": {
                        "type": "object",
                        "properties": {
                          "exerciseId": {
                            "type": "string",
                            "description": "The unique ID of the exercise that was answered incorrectly"
                          },
                          "newQuestion": {
                            "type": "string",
                            "description": "A new fill-in-the-blank question for the same phrase/concept, providing another practice opportunity. Should be clearly formatted with the blank indicated"
                          }
                        },
                        "required": ["exerciseId", "newQuestion"]
                      }
                    }
                  },
                  "required": ["attempts"]
                }
                """;
    }

    private String formatAttempts(final List<Incorrect> attempts) {
        return attempts.stream()
                .map(a -> String.format(
                        "Updated Exercise with ID: %s with new fill-in-the-blank question: \"%s\" \n",
                        a.exerciseId(),
                        a.newQuestion()
                ))
                .collect(Collectors.joining("\n"));
    }
}
