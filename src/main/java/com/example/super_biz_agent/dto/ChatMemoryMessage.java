package com.example.super_biz_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryMessage {
    private String role;    // user / assistant
    private String content;
    private long ts;
}
