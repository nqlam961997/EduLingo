package com.edulingo.controller;

import com.edulingo.dto.AnswerFeedbackResponse;
import com.edulingo.dto.AnswerRequest;
import com.edulingo.dto.CorrectionResponse;
import com.edulingo.dto.DescribeRequest;
import com.edulingo.dto.GenerateImageRequest;
import com.edulingo.dto.GeneratedImageResponse;
import com.edulingo.dto.QuestionListResponse;
import com.edulingo.security.SecurityUtils;
import com.edulingo.service.PictureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/picture")
public class PictureController {

    private final PictureService service;
    private final SecurityUtils security;

    public PictureController(PictureService service, SecurityUtils security) {
        this.service = service;
        this.security = security;
    }

    @PostMapping("/generate")
    public Mono<GeneratedImageResponse> generate(@Valid @RequestBody GenerateImageRequest req) {
        return service.generateImage(req.topicId());
    }

    @GetMapping("/{imageId}/questions")
    public Mono<QuestionListResponse> questions(@PathVariable String imageId) {
        return Mono.fromCallable(() -> service.getQuestions(imageId));
    }

    @PostMapping("/answer")
    public Mono<AnswerFeedbackResponse> answer(@Valid @RequestBody AnswerRequest req) {
        return security.currentEmail()
                .flatMap(email -> service.evaluateAnswer(
                        email, req.imageId(), req.questionIndex(), req.answer()));
    }

    @PostMapping("/describe")
    public Mono<CorrectionResponse> describe(@Valid @RequestBody DescribeRequest req) {
        return security.currentEmail()
                .flatMap(email -> service.correct(email, req.imageId(), req.description()));
    }
}
