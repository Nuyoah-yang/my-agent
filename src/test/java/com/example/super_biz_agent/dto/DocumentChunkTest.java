package com.example.super_biz_agent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentChunk")
class DocumentChunkTest {

    @Nested
    @DisplayName("4-arg constructor")
    class FourArgConstructor {

        @Test
        @DisplayName("sets all fields correctly, title is null")
        void setsAllFields() {
            DocumentChunk chunk = new DocumentChunk("hello world", 0, 11, 0);

            assertThat(chunk.getContent()).isEqualTo("hello world");
            assertThat(chunk.getStartIndex()).isZero();
            assertThat(chunk.getEndIndex()).isEqualTo(11);
            assertThat(chunk.getChunkIndex()).isZero();
            assertThat(chunk.getTitle()).isNull();
        }

        @Test
        @DisplayName("chunkIndex starts at arbitrary value")
        void arbitraryChunkIndex() {
            DocumentChunk chunk = new DocumentChunk("text", 100, 104, 5);
            assertThat(chunk.getChunkIndex()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("no-args constructor + setters")
    class NoArgsAndSetters {

        @Test
        @DisplayName("no-args constructor creates empty object")
        void noArgsConstructor() {
            DocumentChunk chunk = new DocumentChunk();
            assertThat(chunk.getContent()).isNull();
            assertThat(chunk.getStartIndex()).isZero();
            assertThat(chunk.getEndIndex()).isZero();
            assertThat(chunk.getChunkIndex()).isZero();
            assertThat(chunk.getTitle()).isNull();
        }

        @Test
        @DisplayName("setters work correctly")
        void setters() {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setContent("sample");
            chunk.setStartIndex(10);
            chunk.setEndIndex(16);
            chunk.setChunkIndex(3);

            assertThat(chunk.getContent()).isEqualTo("sample");
            assertThat(chunk.getStartIndex()).isEqualTo(10);
            assertThat(chunk.getEndIndex()).isEqualTo(16);
            assertThat(chunk.getChunkIndex()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("setTitle")
    class SetTitle {

        @Test
        @DisplayName("normal title")
        void normalTitle() {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            chunk.setTitle("Introduction");
            assertThat(chunk.getTitle()).isEqualTo("Introduction");
        }

        @Test
        @DisplayName("null title")
        void nullTitle() {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            chunk.setTitle(null);
            assertThat(chunk.getTitle()).isNull();
        }

        @Test
        @DisplayName("empty title")
        void emptyTitle() {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            chunk.setTitle("");
            assertThat(chunk.getTitle()).isEmpty();
        }

        @Test
        @DisplayName("long title")
        void longTitle() {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            String longTitle = "A".repeat(500);
            chunk.setTitle(longTitle);
            assertThat(chunk.getTitle()).hasSize(500);
        }
    }
}
