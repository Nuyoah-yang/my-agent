package com.example.super_biz_agent.dto;

import lombok.Data;

@Data
public class SearchRequest {

    /** Search query text (required). */
    private String query;

    /** Number of results to return. Falls back to chat.rag.top-k if not specified. */
    private Integer topK;
}
