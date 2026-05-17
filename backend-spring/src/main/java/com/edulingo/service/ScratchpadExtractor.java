package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.TopicDto;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Extracts the structured scratchpad JSON from a completed chat turn.
 * The shape of the output JSON depends on the topic's
 * {@link com.edulingo.dto.TopicType}:
 * <ul>
 *   <li>TRANSACTIONAL — slot map keyed by the persona's declared slot names</li>
 *   <li>ASYMMETRIC — phase + facts bag + silentErrors</li>
 *   <li>FREE_FORM — rhythm beat counter + learner habits + ai shared facts</li>
 * </ul>
 *
 * Per chat-session-scratchpad spec. v1 default uses a second-pass AI call to
 * a focused extraction prompt (Option B). Extraction is async w.r.t. the
 * user-visible reply.
 */
public interface ScratchpadExtractor {

    /**
     * Produce the next scratchpad JSON for this session.
     *
     * @param sessionId      The chat session id (for logging only).
     * @param topic          The topic — its type determines the extraction schema.
     * @param messages       The full multi-turn message list INCLUDING the assistant's
     *                       just-emitted reply (chat is over for this turn).
     * @param previousJson   The previous turn's scratchpad JSON, or null on first turn.
     * @return A Mono of the new scratchpad JSON string. Emits {@link Mono#empty()}
     *         (or an error) if extraction fails; callers retain the previous value.
     */
    Mono<String> extract(UUID sessionId,
                         TopicDto.Topic topic,
                         List<ChatMessage> messages,
                         String previousJson);
}
