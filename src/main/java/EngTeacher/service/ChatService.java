package EngTeacher.service;

import EngTeacher.dto.ChatMessageResponseDto;
import EngTeacher.model.Exercise;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private final ChatMemory chatMemory;
    private final UserService userService;
    private final SessionService sessionService;
    private final Tracer tracer;

    public ChatMessageResponseDto processMessage(final String userId, final String sessionId, String userMessage) {
        Span span = tracer.nextSpan().name("user-interaction");
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            span.tag("langfuse.user.id", userId);
            span.tag("langfuse.session.id", sessionId);
            span.tag("langfuse.observation.input", userMessage);

            ChatMessageResponseDto result = execute(userId, sessionId, userMessage);

            span.tag("langfuse.observation.output", result.getAgentResponse());
            return result;
        } catch (Exception e) {
            span.error(e);
            span.tag("error", true);
            span.tag("error.message", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private ChatMessageResponseDto execute(final String userId, final String sessionId, String userMessage) {
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(true)
                .toolContext(Map.of("sessionId", sessionId))
                .build();

        List<Message> agentLoopMemory = new ArrayList<>(chatMemory.get(sessionId));
        if (agentLoopMemory.isEmpty()) {
            SystemMessage systemMessage = new SystemMessage(buildInitSystemPrompt());
            chatMemory.add(sessionId, systemMessage);
            agentLoopMemory.add(systemMessage);
        }

        UserMessage userMsg = new UserMessage(userMessage);
        chatMemory.add(sessionId, userMsg);
        agentLoopMemory.add(userMsg);

        Prompt prompt = new Prompt(agentLoopMemory, chatOptions);
        ChatResponse chatResponse = chatModel.call(prompt);

        chatMemory.add(sessionId, chatResponse.getResult().getOutput());

        List<Exercise> updatedExercises = sessionService.getSession(userService.getUser(userId), sessionId).getExercises();

        return ChatMessageResponseDto.builder()
                .agentResponse(chatResponse.getResult().getOutput().getText())
                .exercises(updatedExercises)
                .build();
    }

    private String buildInitSystemPrompt() {
        return """
                You are a friendly, expert English teacher. You help the user practice target English phrases
                through fill-in-the-blank exercises while holding a natural conversation.

                ## Your Process

                On every user message, follow this loop until you are ready to reply:

                1. **Retrieve** the current exercises by calling `getCurrentExercises`. Do this FIRST on every
                   turn — never reason about exercises from memory, never invent exercise IDs.
                2. **Analyze** the user's message against the retrieved exercises. Is it an attempt at one of
                   the active exercises or just conversation? Consider, that user can immediately start answering an exercise question
                3. **Decide** whether you need a tool and call it with real arguments:
                   - User correctly produced a target phrase → `markExerciseCorrect`.
                   - User got it wrong, used a synonym/paraphrase, used the wrong phrase, or gave up
                     (see Judgment Rules) → `markExerciseIncorrect`.
                   - `regenerateExerciseQuestion` to give the user a fresh question for the same phrase
                     (see that tool's description for the exact conditions under which to call it).
                   - Each of these tools acts on ONE exercise. To act on several at once, call the tool once
                     per exercise.
                   - Otherwise, no tool is needed.
                4. **Observe** the tool result, then loop back to step 2 if more work is needed.
                5. **Reply** to the user in natural language once all needed tools have run.

                ## Judgment Rules for an Exercise Attempt

                - **Exact target phrase** (MINOR tense/grammar adaptation allowed): CORRECT.
                - **Synonym or paraphrase**: INCORRECT. Acknowledge the similarity, hint toward the exact
                  phrase, do NOT reveal it.
                - **Wrong phrase**: INCORRECT. Hint toward the exact phrase, do NOT reveal it!!!
                - **User gives up on the current question** — asks for the answer, asks for a different
                  or new question, or says they can't do it: this counts as an INCORRECT attempt.
                  FIRST call `markExerciseIncorrect`, THEN call `regenerateExerciseQuestion` with a
                  fresh question for the same phrase. Reveal the answer (with a short explanation)
                  only if the user asked for it.

                ## Hard Rules

                - NEVER reveal target phrase, IF you DON'T generate a new exercise question.
                - Act, don't narrate!!! If a tool can change state, CALL the tool - DO NOT JUST SAY in chat that
                  you marked something correct/incorrect.
                - Never fabricate exercise IDs. Always retrieve them from the appropriate tool first.
                - One tool call per logical step; do not call the same tool twice with the same arguments.
                - Your final reply to the user is plain conversational text, not JSON, not tool syntax.

                ## Tone

                Warm, encouraging, concise — a patient teacher having a one-on-one chat.
                """;
    }
}
