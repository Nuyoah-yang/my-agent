package com.example.super_biz_agent.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IndexingResult")
class IndexingResultTest {

    private IndexingResult result;

    @BeforeEach
    void setUp() {
        result = new IndexingResult();
    }

    @Nested
    @DisplayName("getDurationMs")
    class GetDurationMs {

        @Test
        @DisplayName("both startTime and endTime set → returns positive duration")
        void bothTimesSet() {
            result.setStartTime(LocalDateTime.of(2026, 6, 17, 10, 0, 0));
            result.setEndTime(LocalDateTime.of(2026, 6, 17, 10, 0, 5));

            assertThat(result.getDurationMs()).isEqualTo(5000);
        }

        @Test
        @DisplayName("only startTime set → returns 0")
        void onlyStartTime() {
            result.setStartTime(LocalDateTime.now());

            assertThat(result.getDurationMs()).isZero();
        }

        @Test
        @DisplayName("only endTime set → returns 0")
        void onlyEndTime() {
            result.setEndTime(LocalDateTime.now());

            assertThat(result.getDurationMs()).isZero();
        }

        @Test
        @DisplayName("both null → returns 0")
        void bothNull() {
            assertThat(result.getDurationMs()).isZero();
        }

        @Test
        @DisplayName("same instant → returns 0")
        void sameInstant() {
            LocalDateTime now = LocalDateTime.now();
            result.setStartTime(now);
            result.setEndTime(now);

            assertThat(result.getDurationMs()).isZero();
        }
    }

    @Nested
    @DisplayName("incrementSuccessCount")
    class IncrementSuccessCount {

        @Test
        @DisplayName("initial value is 0")
        void initialZero() {
            assertThat(result.getSuccessCount()).isZero();
        }

        @Test
        @DisplayName("after one call → 1")
        void singleIncrement() {
            result.incrementSuccessCount();
            assertThat(result.getSuccessCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("after five calls → 5")
        void multipleIncrements() {
            for (int i = 0; i < 5; i++) {
                result.incrementSuccessCount();
            }
            assertThat(result.getSuccessCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("incrementFailCount")
    class IncrementFailCount {

        @Test
        @DisplayName("initial value is 0")
        void initialZero() {
            assertThat(result.getFailCount()).isZero();
        }

        @Test
        @DisplayName("interleaved with successCount — each tracks independently")
        void independentCounters() {
            result.incrementSuccessCount();
            result.incrementFailCount();
            result.incrementSuccessCount();

            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getFailCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("addFailedFile")
    class AddFailedFile {

        @Test
        @DisplayName("adds entry to failedFiles map")
        void addsEntry() {
            result.addFailedFile("/path/to/file.md", "timeout");

            assertThat(result.getFailedFiles())
                    .hasSize(1)
                    .containsEntry("/path/to/file.md", "timeout");
        }

        @Test
        @DisplayName("overwrites existing entry for same key")
        void overwritesSameKey() {
            result.addFailedFile("/path/to/file.md", "timeout");
            result.addFailedFile("/path/to/file.md", "new error");

            assertThat(result.getFailedFiles())
                    .hasSize(1)
                    .containsEntry("/path/to/file.md", "new error");
        }

        @Test
        @DisplayName("adds multiple distinct entries")
        void multipleEntries() {
            result.addFailedFile("a.md", "err1");
            result.addFailedFile("b.md", "err2");
            result.addFailedFile("c.md", "err3");

            assertThat(result.getFailedFiles()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("getters/setters")
    class GettersSetters {

        @Test
        @DisplayName("success flag")
        void successFlag() {
            result.setSuccess(true);
            assertThat(result.isSuccess()).isTrue();

            result.setSuccess(false);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("directoryPath")
        void directoryPath() {
            result.setDirectoryPath("/data/uploads");
            assertThat(result.getDirectoryPath()).isEqualTo("/data/uploads");
        }

        @Test
        @DisplayName("totalFiles")
        void totalFiles() {
            result.setTotalFiles(10);
            assertThat(result.getTotalFiles()).isEqualTo(10);
        }

        @Test
        @DisplayName("errorMessage")
        void errorMessage() {
            result.setErrorMessage("Something went wrong");
            assertThat(result.getErrorMessage()).isEqualTo("Something went wrong");
        }
    }
}
