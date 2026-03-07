package com.theinterview.webhook.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MattermostResponse(
        String text,
        String channel,
        List<Attachment> attachments
) {

    public MattermostResponse(String text, List<Attachment> attachments) {
        this(text, null, attachments);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(
            String color,
            String text
    ) {

    }
}
