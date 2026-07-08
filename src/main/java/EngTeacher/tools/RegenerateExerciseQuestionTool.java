package EngTeacher.tools;

import EngTeacher.dto.agent.tools.ExerciseAttempt.Regenerate;
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
public class RegenerateExerciseQuestionTool implements ToolCallback {

    private final ExerciseService exerciseService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("regenerateExerciseQuestion")
                .description("""
                        Replace the fill-in-the-blank question of ONE exercise with a NEW question targeting
                        the SAME phrase, giving the user a fresh practice opportunity. This resets the exercise
                        state to NOT_ATTEMPTED.

                        Call this tool ONLY when:
                            (a) the exercise is already in state FAILED and the user attempts it again, OR
                            (b) the user gives up on the current question — asks for the answer or
                                EXPLICITLY asks for a different question for the same phrase.
                        Do NOT regenerate after a first wrong attempt (synonym, paraphrase, wrong phrase),
                        and never just to be helpful.


                        Important:
                            - BEFORE calling this tool you MUST mark the exercise as incorrect
                            - After calling this tool, you MUST present the newly generated question to the user in your reply so they can attempt it.
                            - Do NOT reveal the target phrase in the new question — it must remain a blank to be filled.
                            - The new question must clearly indicate the blank (e.g. "I __ to the store yesterday.").
                            - Generate a different sentence context than the previous question to avoid pure repetition.

                        Returns:
                            A confirmation string in the format:
                            'Regenerated question for exercise with ID: <exerciseId> -> "<newQuestion>"'
                        """)
                .inputSchema(buildInputSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Regenerate regeneration = objectMapper.readValue(toolInput, Regenerate.class);

            exerciseService.regenerateQuestions(List.of(regeneration));

            return String.format(
                    "Regenerated question for exercise with ID: %s -> \"%s\"",
                    regeneration.exerciseId(),
                    regeneration.newQuestion()
            );
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
                      "description": "The unique ID of the exercise whose question should be replaced. Must reference an exercise that currently exists in the active session."
                    },
                    "newQuestion": {
                      "type": "string",
                      "description": "A new fill-in-the-blank question for the same phrase/concept, with the blank clearly indicated. Must not reveal the target phrase."
                    }
                  },
                  "required": ["exerciseId", "newQuestion"]
                }
                """;
    }
}
