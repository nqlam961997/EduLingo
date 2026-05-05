package com.edulingo.mapper;

import com.edulingo.dto.ErrorItem;
import com.edulingo.entity.ErrorPattern;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ErrorPatternMapper {

    public ErrorPattern toEntity(UUID learnerId, ErrorItem item) {
        ErrorPattern e = new ErrorPattern();
        e.setLearnerId(learnerId);
        e.setErrorType(item.type());
        e.setExample(item.original());
        return e;
    }

    public void incrementWith(ErrorPattern existing, ErrorItem item) {
        existing.setCount(existing.getCount() + 1);
        existing.setLastSeen(Instant.now());
        if (item.original() != null) existing.setExample(item.original());
    }
}
