package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.dto.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VectorIndexServiceImpl — buildMetadata")
class VectorIndexServiceImplTest {

    private VectorIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VectorIndexServiceImpl();
    }

    @Nested
    @DisplayName("buildMetadata")
    class BuildMetadata {

        @Test
        @DisplayName("normal file path with extension → all metadata keys present")
        void normalFilePath() throws Exception {
            DocumentChunk chunk = new DocumentChunk("content", 0, 7, 0);
            chunk.setTitle("Introduction");

            Map<String, Object> metadata = invokeBuildMetadata("/docs/guide.md", chunk, 3);

            assertThat(metadata)
                    .containsKeys("_source", "_file_name", "_extension", "chunkIndex", "totalChunks", "title");
            assertThat(metadata.get("_file_name")).isEqualTo("guide.md");
            assertThat(metadata.get("_extension")).isEqualTo(".md");
            assertThat(metadata.get("chunkIndex")).isEqualTo(0);
            assertThat(metadata.get("totalChunks")).isEqualTo(3);
            assertThat(metadata.get("title")).isEqualTo("Introduction");
        }

        @Test
        @DisplayName("file path without extension → _extension is empty")
        void noExtension() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);

            Map<String, Object> metadata = invokeBuildMetadata("/path/to/README", chunk, 1);

            assertThat(metadata.get("_file_name")).isEqualTo("README");
            assertThat(metadata.get("_extension")).isEqualTo("");
        }

        @Test
        @DisplayName("chunk with null title → no title key in metadata")
        void nullTitle() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            // title is null by default in 4-arg constructor

            Map<String, Object> metadata = invokeBuildMetadata("/data/file.txt", chunk, 5);

            assertThat(metadata).doesNotContainKey("title");
        }

        @Test
        @DisplayName("chunk with empty title → no title key in metadata")
        void emptyTitle() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);
            chunk.setTitle("");

            Map<String, Object> metadata = invokeBuildMetadata("/data/file.txt", chunk, 5);

            assertThat(metadata).doesNotContainKey("title");
        }

        @Test
        @DisplayName("_source uses forward slashes (normalized)")
        void sourceNormalized() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);

            Map<String, Object> metadata = invokeBuildMetadata("/some/file.txt", chunk, 1);

            assertThat((String) metadata.get("_source")).doesNotContain("\\");
            assertThat((String) metadata.get("_source")).contains("/");
        }

        @Test
        @DisplayName("chunkIndex and totalChunks correctly stored")
        void chunkPositionCorrect() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 50, 54, 7);

            Map<String, Object> metadata = invokeBuildMetadata("/data/chunked.txt", chunk, 10);

            assertThat(metadata.get("chunkIndex")).isEqualTo(7);
            assertThat(metadata.get("totalChunks")).isEqualTo(10);
        }

        @Test
        @DisplayName("file with compound extension → handled correctly")
        void compoundExtension() throws Exception {
            DocumentChunk chunk = new DocumentChunk("text", 0, 4, 0);

            Map<String, Object> metadata = invokeBuildMetadata("/data/archive.tar.gz", chunk, 1);

            assertThat(metadata.get("_extension")).isEqualTo(".gz");
        }
    }

    // ==================== reflection helpers ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildMetadata(String filePath, DocumentChunk chunk, int totalChunks)
            throws Exception {
        var method = VectorIndexServiceImpl.class.getDeclaredMethod(
                "buildMetadata", String.class, DocumentChunk.class, int.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, filePath, chunk, totalChunks);
    }
}
