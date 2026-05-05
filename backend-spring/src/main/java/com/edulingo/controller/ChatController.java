package com.edulingo.controller;

import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.ScenarioRequest;
import com.edulingo.dto.ScenarioResponse;
import com.edulingo.security.SecurityUtils;
import com.edulingo.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService service;
    private final SecurityUtils security;

    public ChatController(ChatService service, SecurityUtils security) {
        this.service = service;
        this.security = security;
    }

    @PostMapping("/start")
    public Mono<ScenarioResponse> start(@Valid @RequestBody ScenarioRequest req) {
        return security.currentEmail()
                .flatMap(email -> service.startScenario(email, req.topicId()));
    }

    @PostMapping(value = "/reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> reply(@Valid @RequestBody ChatRequest req) {
        return security.currentEmail()
                .flatMapMany(email -> service.reply(email, req));
    }
}
