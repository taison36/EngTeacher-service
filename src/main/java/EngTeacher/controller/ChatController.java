package EngTeacher.controller;

import EngTeacher.dto.ChatMessageRequestDto;
import EngTeacher.dto.ChatMessageResponseDto;
import EngTeacher.model.Session;
import EngTeacher.model.User;
import EngTeacher.security.AuthUtils;
import EngTeacher.service.ChatService;
import EngTeacher.service.SessionService;
import EngTeacher.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/message")
    public ChatMessageResponseDto processMessage(@RequestBody final ChatMessageRequestDto request) {
        return chatService.processMessage(AuthUtils.currentUserId(), request.getSessionId(), request.getMessage());
    }
}
