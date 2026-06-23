package EngTeacher.service;

import EngTeacher.dto.ChatMessageDto;
import EngTeacher.exceptions.NotFoundException;
import EngTeacher.model.Exercise;
import EngTeacher.model.ExerciseState;
import EngTeacher.model.Session;
import EngTeacher.model.User;
import EngTeacher.model.UserSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatMemory chatMemory;

    public Session getSession(final User user, final String sessionId) {
        return user.getSessions().stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(String.format("Session %s was not found in DB", sessionId)));
    }

    public int deleteDoneExercises(Session session) {
        final int initialSize = session.getExercises().size();
        List<Exercise> uncomplete = session.getExercises().stream().filter(e -> e.getState() != ExerciseState.COMPLETED).toList();
        session.setExercises(uncomplete);
        return initialSize - uncomplete.size();
    }

    public int neededExerciseQuantity(final Session session, final UserSettings userSettings) {
        return userSettings.getMaxNumberExercises() - session.getExercises().size();
    }

    public List<ChatMessageDto> getMessages(final String sessionId) {
        List<Message> chatMemoryMessages = chatMemory.get(sessionId);
        return chatMemoryMessages.stream()
                .filter(message -> message.getMessageType() == MessageType.USER ||
                        message.getMessageType() == MessageType.ASSISTANT)
                .map(message -> ChatMessageDto.builder()
                        .content(message.getText())
                        .type(message.getMessageType() == MessageType.USER ?
                                ChatMessageDto.ChatMessageType.USER :
                                ChatMessageDto.ChatMessageType.ASSISTANT)
                        .build())
                .toList();
    }
}
