package EngTeacher.service;

import EngTeacher.dto.agent.tools.ExerciseGenerationDto;
import EngTeacher.exceptions.AgentResponseParsingException;
import EngTeacher.exceptions.ImproperApiUsageException;
import EngTeacher.model.Exercise;
import EngTeacher.model.Phrase;
import EngTeacher.model.User;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseGenerationService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ObservationRegistry observationRegistry;

    public List<Exercise> generate(User user, final int neededExerciseQuantity) {
        Observation observation = Observation.createNotStarted("engteacher.exercise.generation", observationRegistry)
                .lowCardinalityKeyValue("langfuse.tags", "feature:exercise-generation")
                .highCardinalityKeyValue("langfuse.user.id", user.getId())
                .highCardinalityKeyValue("langfuse.observation.input",
                        "Generate %d exercises for user %s".formatted(neededExerciseQuantity, user.getId()))
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            List<Exercise> result = doGenerate(user, neededExerciseQuantity);
            String outputSummary = result.stream()
                    .map(e -> e.getPhrase().getContent())
                    .collect(Collectors.joining(", "));
            observation.highCardinalityKeyValue("langfuse.observation.output",
                    "Generated %d exercises for: %s".formatted(result.size(), outputSummary));
            return result;
        } catch (Exception e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private List<Exercise> doGenerate(User user, final int neededExerciseQuantity) {
        if (neededExerciseQuantity <= 0) {
            throw new ImproperApiUsageException("No need to create exercises. Max is already reached");
        }

        List<Phrase> phrases = choosePhrases(user, neededExerciseQuantity);

        String prompt = buildGenerateExercisesPrompt(phrases);

        ChatResponse response = chatModel.call(new Prompt(prompt));
        String llmResponse = response.getResult().getOutput().getText();

        try {
            List<ExerciseGenerationDto> exerciseDtos = objectMapper.readValue(
                    llmResponse,
                    new TypeReference<>() {
                    }
            );

            return exerciseDtos.stream()
                    .map(dto -> {
                        Phrase matchingPhrase = phrases.stream()
                                .filter(p -> p.getContent().equals(dto.getPhrase()))
                                .findFirst()
                                .orElseThrow();

                        return Exercise.builder()
                                .id(UUID.randomUUID().toString())
                                .question(dto.getQuestion())
                                .phrase(matchingPhrase)
                                .build();
                    })
                    .toList();
        } catch (JacksonException e) {
            throw new AgentResponseParsingException(String.format("Failed to parse exercise generation response: %s", e));
        }
    }


    private List<Phrase> choosePhrases(final User user, final int neededExerciseQuantity) {
        Random random = new Random();

        return user.getPhrases().stream()
                // randomness + understanding rate
                .sorted((p1, p2) -> {
                    int weight1 = 100 - p1.getUnderstandingRate() + random.nextInt(20);
                    int weight2 = 100 - p2.getUnderstandingRate() + random.nextInt(20);
                    return Integer.compare(weight2, weight1);
                })
                .limit(neededExerciseQuantity)
                .collect(Collectors.toList());
    }

    private String buildGenerateExercisesPrompt(final List<Phrase> phrases) {
        List<Map<String, String>> phrasesJson = phrases.stream()
                .map(phrase -> Map.of("phrase", phrase.getContent()))
                .collect(Collectors.toList());

        String phrasesJsonString = objectMapper.writeValueAsString(phrasesJson);

        return """
                You are a language learning assistant. Create one fill-in-the-blank exercise for each phrase.
                
                Requirements:
                
                Each exercise must be a single sentence with exactly one blank written as "..."
                The correct answer is the full target phrase, which fits naturally into the blank
                Do NOT include the phrase in the sentence itself
                Sentences should be realistic, conversational, and practical
                Ensure the sentence clearly implies the target phrase
                
                Return a valid JSON array with this exact structure:
                [
                {"phrase": "a tin of", "question": "I went to the store to buy ... tuna for dinner."},
                {"phrase": "enjoy yourself", "question": "Have a great time at the party and ...!"}
                ]
                
                Phrases as JSON:
                %s
                
                Return ONLY the JSON array. No explanations, no markdown, no extra text.
                """.formatted(phrasesJsonString);
    }
}

