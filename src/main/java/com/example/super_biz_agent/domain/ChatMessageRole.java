package com.example.super_biz_agent.domain;

public enum ChatMessageRole {
    SYSTEM(0),
    USER(1),
    ASSISTANT(2);

    private final int code;

    ChatMessageRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
