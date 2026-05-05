package com.edulingo.controller;

import com.edulingo.dto.TopicDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    @GetMapping
    public Mono<List<TopicDto.Topic>> list() {
        return Mono.just(TopicDto.TOPICS);
    }
}
