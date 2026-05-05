package com.edulingo.dto;

import java.util.List;

public record ChatReply(String reply, List<String> suggestions) {}
