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

                        The user MUST use the SAME core words as the target phrase. Surface-level variations
                        are accepted; lexical swaps are NOT.

                        ACCEPT (still CORRECT):
                            - different tense, person, or number ("break" → "broke", "I" → "they"),
                            - different inflection (gerund vs. infinitive, singular vs. plural),
                            - obvious typos / misspellings of the target words ("acheive" instead of "achieve"),
                            - swapping a preposition or article for a near-equivalent one
                              (a/the, "on the bus" / "on a bus"),
                            - filling placeholders ("something", "someone") with concrete content
                              (target "look forward to something" → "look forward to seeing you" is CORRECT).

                        REJECT (mark INCORRECT via markExerciseIncorrect instead):
                            - synonyms or paraphrases that replace the core verb/noun of the phrase
                              (target "break the ice" → "start the conversation" is INCORRECT;
                               target "look forward to" → "anticipate" is INCORRECT),
                            - any rephrasing that changes the target phrase's main lexical items,
                            - using a different target phrase from the session.

                        When in doubt between "same words, different form" and "different words", lean INCORRECT.

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
