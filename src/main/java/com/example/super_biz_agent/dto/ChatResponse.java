package com.example.super_biz_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private boolean success;
    private String answer;
    private String errorMessage;
    private String sessionId;

    public static ChatResponse success(String answer,String sessionId) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        response.setSessionId(sessionId);
        return response;
    }

    public static ChatResponse error(String errorMessage,String sessionId) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setSessionId(sessionId);
        return response;
    }
}
