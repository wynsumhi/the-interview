package com.theinterview.webhook.dto.response;

import com.theinterview.webhook.dto.response.status.SuccessStatus;

public record ApiResponse<T>(
        Boolean isSuccess,
        String code,
        String message,
        T result) {
    
    public static final ApiResponse<Void> OK = new ApiResponse<>(
            true,
            SuccessStatus.OK.getCode(),
            SuccessStatus.OK.getMessage(),
            null);
}
