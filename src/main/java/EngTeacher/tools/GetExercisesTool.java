package EngTeacher.tools;

import EngTeacher.model.Exercise;
import EngTeacher.model.User;
import EngTeacher.security.AuthUtils;
import EngTeacher.service.SessionService;
import EngTeacher.service.UserService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetExercisesTool implements ToolCallback {

    private final SessionService sessionService;
    private final UserService userService;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("getCurrentExercises")
                .description("""
                        Retrieve the full list of exercises that are currently active in the user's session, including
                        their IDs, target phrases, fill-in-the-blank questions, and completion status.

                        Use this tool when you need to:
                            - Look up an exercise's ID before calling 'markExercisesCorrect', 'markExercisesIncorrect', or 'regenerateExerciseQuestions'.
                            - Check which target phrase belongs to which question.
                            - Verify the current state of an exercise (not yet attempted, completed, or failed).
                            - Recover the current exercise state if it is missing or stale in the conversation context.

                        This tool takes NO arguments — the active session is resolved automatically from the chat context.

                        Returns:
                            A newline-separated string with one line per exercise in the format:
                            'ID: <exerciseId> | Phrase: "<phrase>" | Fill-in-the-blank question: "<question>" | State: <NOT_ATTEMPTED|COMPLETED|FAILED>'
                            Returns the string "No exercises in the current session." if the session has no exercises.
                        """)
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """)
                .build();
    }

    @Override
    public String call(@NonNull String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(@NonNull String toolInput, @Nullable ToolContext toolContext) {
        if (toolContext == null || !(toolContext.getContext().get("sessionId") instanceof String sessionId)) {
            throw new IllegalStateException("getCurrentExercises requires a sessionId in the tool context");
        }
        User user = userService.getUser(AuthUtils.currentUserId());

        List<Exercise> exercises = sessionService.getSession(user, sessionId).getExercises();;
        if (exercises.isEmpty()) {
            return "No exercises in the current session.";
        }
        return exercises.stream()
                .map(ex -> String.format(
                        "ID: %s | Phrase: \"%s\" | Fill-in-the-blank question: \"%s\" | State: %s",
                        ex.getId(),
                        ex.getPhrase().getContent(),
                        ex.getQuestion(),
                        ex.getState()
                ))
                .collect(Collectors.joining("\n"));
    }
}
