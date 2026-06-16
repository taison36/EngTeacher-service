package EngTeacher.service;

import EngTeacher.constant.AppConstant;
import EngTeacher.dto.ChatMessageResponseDto;
import EngTeacher.dto.agent.EvaluationResult;
import EngTeacher.model.AgentRule;
import EngTeacher.model.Exercise;
import EngTeacher.model.Session;
import EngTeacher.model.User;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private final ToolCallingManager toolCallingManager;
    private final ChatMemory chatMemory;
    private final UserService userService;
    private final SessionService sessionService;
    private final AgentRuleService agentRuleService;
    private final Tracer tracer;
    private final ExerciseService exerciseService;

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
        ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .maxTokens(300)
                .build();

        List<Message> agentLoopMemory = new ArrayList<>(chatMemory.get(sessionId));
        if (agentLoopMemory.isEmpty()) {
            SystemMessage systemMessage = new SystemMessage(buildInitSystemPrompt());
            chatMemory.add(sessionId, systemMessage);
            agentLoopMemory.add(systemMessage);
        }

        User user = userService.getUser(userId);
        Session session = sessionService.getSession(user, sessionId);
        var exercisesAsMessage = new SystemMessage("[CURRENT EXERCISES]\n" + formatExercises(session.getExercises()));
        agentLoopMemory.add(exercisesAsMessage);

        UserMessage userMsg = new UserMessage(userMessage);
        chatMemory.add(sessionId, userMsg);
        agentLoopMemory.add(userMsg);


        Prompt prompt = new Prompt(agentLoopMemory, chatOptions);
        ChatResponse chatResponse = chatModel.call(prompt);

        while (chatResponse.hasToolCalls()) {
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
            if (toolResult.conversationHistory().getLast() instanceof ToolResponseMessage toolResponseMessage) {
                agentLoopMemory.add(toolResponseMessage);
            }
            // Reload exercises only after a tool call, since that is when state changes
            //user = userService.getUser(userId);
            //session = sessionService.getSession(user, sessionId);
            //var updatedExercises = new SystemMessage("[CURRENT EXERCISES]\n" + formatExercises(session.getExercises()));
            //agentLoopMemory.add(updatedExercises);

            prompt = new Prompt(agentLoopMemory, chatOptions);

            chatResponse = chatModel.call(prompt);
            agentLoopMemory.add(chatResponse.getResult().getOutput());
        }

        String unvalidatedResponse = chatResponse.getResult().getOutput().getText();

        //TODO Rule: did the agent use a question from existing exercises?
//        List<AgentRule> rules = agentRuleService.getEnabledRules();
//        if (!rules.isEmpty()) {
//            EvaluationResult eval = evaluate(unvalidatedResponse, userMessage, session, rules);
//            int step = 1;
//            while (!eval.passed() && step < AppConstant.MAX_EVALUATION_STEPS) {
//                log.debug("Evaluation failed — rule: '{}', reason: '{}'", eval.violatedRule(), eval.reason());
//                agentLoopMemory.add(chatResponse.getResult().getOutput());
//                agentLoopMemory.add(new SystemMessage(
//                        "[SYSTEM CORRECTION] Your response violated the rule '%s': %s. Please revise."
//                                .formatted(eval.violatedRule(), eval.reason())
//                ));
//                unvalidatedResponse = chatModel.call(new Prompt(agentLoopMemory, chatOptions))
//                        .getResult().getOutput().getText();
//                eval = evaluate(unvalidatedResponse, userMessage, session, rules);
//                step++;
//            }
//        }

        String finalResponse = unvalidatedResponse;

        chatMemory.add(sessionId, chatResponse.getResult().getOutput());

        user = userService.getUser(user.getId());
        session = sessionService.getSession(user, session.getId());
        List<Exercise> updatedExercises = session.getExercises();

        return ChatMessageResponseDto.builder()
                .agentResponse(finalResponse)
                .exercises(updatedExercises)
                .build();
    }

    private EvaluationResult evaluate(String agentResponse, String userMessage, Session session, List<AgentRule> rules) {
        BeanOutputConverter<EvaluationResult> converter = new BeanOutputConverter<>(EvaluationResult.class);

        String rulesFormatted = IntStream.range(0, rules.size())
                .mapToObj(i -> "%d. %s".formatted(i + 1, rules.get(i).getDescription()))
                .collect(Collectors.joining("\n"));

        String exercisesFormatted = formatExercises(session.getExercises());

        String evalPrompt = """
                You are evaluating whether an English language learning assistant followed all required rules.
                
                Active exercises (target phrases the student must discover):
                %s
                
                Student's message: "%s"
                Assistant's response: "%s"
                
                Rules:
                %s
                
                %s
                """.formatted(exercisesFormatted, userMessage, agentResponse, rulesFormatted, converter.getFormat());

        try {
            String evalResponse = chatModel.call(new Prompt(evalPrompt))
                    .getResult().getOutput().getText();
            return converter.convert(evalResponse);
        } catch (Exception e) {
            log.warn("Evaluation parsing failed — skipping. Error: {}", e.getMessage());
            return EvaluationResult.pass();
        }
    }

    private String buildInitSystemPrompt() {
        return """
                You are a language learning assistant helping the user practice English phrases through exercises. Answer users questions and conclude a dialog.
                
                ## Exercise evaluation Framework
                ### Analysis Process:
                1. Search for current exercises in chat history. Exercises are provided as system message.
                2. Identify, if the uses attempted one of the current exercises.
                3. If the user has attempted an exercise call a corresponding tool.
                4. If the user didn't attempt any exercise, just conduct a conversation.
                
                ### Key Metrics to Evaluate:
                - Mark correct ONLY if the user used the target phrase for fill-in-the-blank question. Minor tense/grammar adaptation is fine; synonyms are not.
                - If the user uses a synonym or paraphrase: do not reveal the answer. Mark incorrect, acknowledge the similarity, give a hint toward the exact phrase, and present the new generated question.
                - If the user has used wrong phrase: do not reveal the answer. Mark incorrect, give a hint toward the exact phrase and present the new generated question.
                - If the user asks for the answer: reveal it with a brief explanation, mark incorrect and present the new generated question.
                
                ## VERY IMPORTANT Rules for Tool Usage
                -SINGLE EXECUTION RULE: it is forbidden to call a tool more then once for one user message!
                
                ## Tone
                Be friendly and helpful teacher
                """;
    }

    // - SINGLE EXECUTION RULE: After you find the "[TOOL]: […]” response, your ONLY next step is to reply to the user. It is forbidden to re-run a tool for the same request once you have its output.
    // - Trust Tool Outputs: A tool’s response is FINAL. Accept the first response and DO NOT re-run the tool.”
    private String formatExercises(List<Exercise> exercises) {
        return exercises.stream()
                .map(ex -> String.format(
                        "ID: %s | Phrase: \"%s\" | Fill-in-the-blank question: \"%s\" | Done: %b",
                        ex.getId(),
                        ex.getPhrase().getContent(),
                        ex.getQuestion(),
                        ex.isDone()
                ))
                .collect(Collectors.joining("\n"));
    }
}
