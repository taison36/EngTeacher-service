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
                        core lexical pattern. This is the counterpart to `markExerciseCorrect` — apply the same
                        strictness, in reverse.

                        MARK INCORRECT when:
                            - the user uses a synonym or paraphrase that replaces the core verb/noun of the
                              target phrase (target "break the ice" → "start the conversation" is INCORRECT;
                              target "look forward to" → "anticipate" is INCORRECT),
                            - the user fills the blank with a DIFFERENT target phrase from the active session,
                            - the answer is grammatically broken English that no longer reflects the target
                              construct,
                            - the user gives up: asks for the answer, asks for a different question,
                              or says they can't do it.

                        DO NOT mark incorrect (these are CORRECT — use `markExerciseCorrect`):
                            - different tense, person, or number ("break" → "broke", "I" → "they"),
                            - different inflection (gerund vs. infinitive, singular vs. plural),
                            - obvious typos / misspellings of the target words ("acheive" instead of "achieve"),
                            - swapping a preposition or article for a near-equivalent one
                              (a/the, "on the bus" / "on a bus"),
                            - filling placeholders ("something", "someone") with concrete content
                              (target "look forward to something" → "look forward to seeing you").

                        When in doubt between "same words, different form" and "different words", lean INCORRECT.

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
