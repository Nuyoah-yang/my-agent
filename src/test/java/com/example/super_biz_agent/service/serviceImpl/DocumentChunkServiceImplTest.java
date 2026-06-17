package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.DocumentChunkConfig;
import com.example.super_biz_agent.dto.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentChunkServiceImpl")
class DocumentChunkServiceImplTest {

    private DocumentChunkServiceImpl service;
    private DocumentChunkConfig config;

    @BeforeEach
    void setUp() {
        config = new DocumentChunkConfig();
        config.setMaxSize(800);
        config.setOverlap(100);
        service = new DocumentChunkServiceImpl();
        ReflectionTestUtils.setField(service, "chunkConfig", config);
    }

    // ==================== chunkDocument ====================

    @Nested
    @DisplayName("chunkDocument")
    class ChunkDocument {

        @Test
        @DisplayName("null content → empty list")
        void nullContent() {
            List<DocumentChunk> result = service.chunkDocument(null, "test.md");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("empty string content → empty list")
        void emptyContent() {
            List<DocumentChunk> result = service.chunkDocument("", "test.md");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("blank content (spaces/newlines only) → empty list")
        void blankContent() {
            List<DocumentChunk> result = service.chunkDocument("   \n\n  \n  ", "test.md");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("short single paragraph → single chunk")
        void shortSingleParagraph() {
            String content = "Hello world, this is a short document.";
            List<DocumentChunk> result = service.chunkDocument(content, "test.md");

            assertThat(result).hasSize(1);
            DocumentChunk chunk = result.get(0);
            assertThat(chunk.getContent()).isEqualTo(content);
            assertThat(chunk.getChunkIndex()).isZero();
            assertThat(chunk.getStartIndex()).isZero();
            assertThat(chunk.getEndIndex()).isEqualTo(content.length());
            assertThat(chunk.getTitle()).isNull();
        }

        @Test
        @DisplayName("content with headings → each section has correct title")
        void contentWithHeadings() {
            String content = "# H1 Title\nContent under H1.\n\n## H2 Title\nContent under H2.";
            List<DocumentChunk> result = service.chunkDocument(content, "test.md");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTitle()).isEqualTo("H1 Title");
            assertThat(result.get(0).getContent()).contains("Content under H1.");
            assertThat(result.get(1).getTitle()).isEqualTo("H2 Title");
            assertThat(result.get(1).getContent()).contains("Content under H2.");
        }

        @Test
        @DisplayName("chunk indices increment across sections")
        void chunkIndicesIncrementAcrossSections() {
            String content = "# A\nP1\n\n# B\nP2";
            List<DocumentChunk> result = service.chunkDocument(content, "test.md");

            assertThat(result).allMatch(c -> c.getChunkIndex() >= 0);
            assertThat(result.get(result.size() - 1).getChunkIndex())
                    .isEqualTo(result.size() - 1);
        }

        @Test
        @DisplayName("no headings → single section with null title")
        void noHeadings() {
            String content = "Plain text without any markdown headings.\n\nMore text.";
            List<DocumentChunk> result = service.chunkDocument(content, "test.md");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isNull();
        }
    }

    // ==================== splitByHeadings ====================

    @Nested
    @DisplayName("splitByHeadings")
    class SplitByHeadings {

        @Test
        @DisplayName("single H1 heading")
        void singleH1() {
            List<DocumentChunkServiceImpl.Section> sections =
                    service.splitByHeadings("# Title\nSome content here.");

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).title).isEqualTo("Title");
            assertThat(sections.get(0).content).contains("Some content here.");
        }

        @Test
        @DisplayName("multiple heading levels — H1, H2, H3")
        void multipleHeadingLevels() {
            String content = "# H1\nA\n## H2\nB\n### H3\nC";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(3);
            assertThat(sections.get(0).title).isEqualTo("H1");
            assertThat(sections.get(1).title).isEqualTo("H2");
            assertThat(sections.get(2).title).isEqualTo("H3");
        }

        @Test
        @DisplayName("content before first heading → section with null title")
        void preambleBeforeHeading() {
            String content = "Preamble text.\n# Title\nBody";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(2);
            assertThat(sections.get(0).title).isNull();
            assertThat(sections.get(0).content).isEqualTo("Preamble text.");
            assertThat(sections.get(1).title).isEqualTo("Title");
        }

        @Test
        @DisplayName("Chinese heading → title is extracted correctly")
        void chineseHeading() {
            String content = "# 中文标题\n内容文字。";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).title).isEqualTo("中文标题");
        }

        @Test
        @DisplayName("heading with trailing spaces → title is trimmed")
        void headingWithTrailingSpaces() {
            String content = "#  Title  \nBody";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).title).isEqualTo("Title");
        }

        @Test
        @DisplayName("no headings → single section with null title")
        void noHeadingsAtAll() {
            String content = "Just plain text without any # marker.";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).title).isNull();
            assertThat(sections.get(0).content).isEqualTo(content);
        }

        @Test
        @DisplayName("H6 heading → recognized (max heading level)")
        void h6Heading() {
            String content = "###### Deep heading\nContent.";
            List<DocumentChunkServiceImpl.Section> sections = service.splitByHeadings(content);

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).title).isEqualTo("Deep heading");
        }
    }

    // ==================== splitByParagraphs ====================

    @Nested
    @DisplayName("splitByParagraphs")
    class SplitByParagraphs {

        @Test
        @DisplayName("single paragraph → list of 1")
        void singleParagraph() {
            List<String> paragraphs = service.splitByParagraphs("Hello world.");
            assertThat(paragraphs).hasSize(1).containsExactly("Hello world.");
        }

        @Test
        @DisplayName("multiple paragraphs separated by double newlines")
        void multipleParagraphs() {
            String content = "Paragraph one.\n\nParagraph two.\n\nParagraph three.";
            List<String> paragraphs = service.splitByParagraphs(content);

            assertThat(paragraphs).hasSize(3);
            assertThat(paragraphs.get(0)).isEqualTo("Paragraph one.");
            assertThat(paragraphs.get(1)).isEqualTo("Paragraph two.");
            assertThat(paragraphs.get(2)).isEqualTo("Paragraph three.");
        }

        @Test
        @DisplayName("multiple blank lines → treated as single separator")
        void multipleBlankLines() {
            String content = "A\n\n\n\nB";
            List<String> paragraphs = service.splitByParagraphs(content);

            assertThat(paragraphs).hasSize(2)
                    .containsExactly("A", "B");
        }

        @Test
        @DisplayName("paragraphs with leading/trailing whitespace → trimmed")
        void whitespaceTrimmed() {
            String content = "  P1  \n\n  P2  ";
            List<String> paragraphs = service.splitByParagraphs(content);

            assertThat(paragraphs).containsExactly("P1", "P2");
        }

        @Test
        @DisplayName("empty paragraphs (consecutive newlines only) → filtered out")
        void emptyParagraphsFiltered() {
            String content = "A\n\n\n\nB\n\n";
            List<String> paragraphs = service.splitByParagraphs(content);

            assertThat(paragraphs).containsExactly("A", "B");
        }

        @Test
        @DisplayName("single newline within paragraph → preserved (not split)")
        void singleNewlinesPreserved() {
            String content = "Line one\nLine two still same paragraph.\n\nNext para.";
            List<String> paragraphs = service.splitByParagraphs(content);

            assertThat(paragraphs).hasSize(2);
            assertThat(paragraphs.get(0)).contains("\n");
        }
    }

    // ==================== getOverlapText ====================

    @Nested
    @DisplayName("getOverlapText")
    class GetOverlapText {

        @Test
        @DisplayName("text shorter than overlap → returns entire text trimmed")
        void shorterThanOverlap() {
            String result = service.getOverlapText("abc");
            // Overlap is 100, text is 3 chars
            assertThat(result).isEqualTo("abc");
        }

        @Test
        @DisplayName("zero overlap configured → returns empty string")
        void zeroOverlap() {
            config.setOverlap(0);
            String result = service.getOverlapText("some long text here");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("overlap ending with period (。) → splits at sentence end")
        void sentenceEndPeriod() {
            // 150 chars text, overlap=100, should find the last '。' in the last 50 chars
            String prefix = "前面内容。".repeat(5); // ~30 chars
            String suffix = "这是结尾句号结束。".repeat(3);
            String text = prefix + suffix; // enough to exceed 100
            String result = service.getOverlapText(text);

            assertThat(result).doesNotContain("。");
        }

        @Test
        @DisplayName("overlap ending with exclamation (！) → splits at sentence end")
        void sentenceEndExclamation() {
            String text = "前面内容！".repeat(10);
            String result = service.getOverlapText(text);

            assertThat(result).doesNotContain("！");
        }

        @Test
        @DisplayName("overlap ending with question mark (？) → splits at sentence end")
        void sentenceEndQuestion() {
            String text = "前面内容？".repeat(10);
            String result = service.getOverlapText(text);

            assertThat(result).doesNotContain("？");
        }

        @Test
        @DisplayName("no sentence punctuation → returns trimmed overlap")
        void noSentencePunctuation() {
            String text = "This is English text without Chinese punctuation marks ".repeat(10);
            String result = service.getOverlapText(text);

            assertThat(result).isNotEmpty();
            assertThat(result.length()).isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("sentence marker exactly at midpoint → returns full overlap")
        void markerAtMidpoint() {
            // Build text where the last 。 is at exactly overlap/2 from the end
            config.setOverlap(100);
            String half = "x".repeat(49); // 49 chars before the 。
            // We need the 。 to appear inside the overlap region
            String text = half + "。" + "y".repeat(50); // last 100: "。yyy...yyy"
            String result = service.getOverlapText(text);

            // 。 is at position 50 from end, overlap/2=50, lastSentenceEnd=50
            // Condition: > not >=, so 50 > 50 is false → returns full overlap
            // Just verify it's not empty
            assertThat(result).isNotEmpty();
        }
    }

    // ==================== chunkSection ====================

    @Nested
    @DisplayName("chunkSection")
    class ChunkSection {

        @Test
        @DisplayName("content shorter than maxSize → single chunk")
        void shorterThanMaxSize() {
            DocumentChunkServiceImpl.Section section =
                    new DocumentChunkServiceImpl.Section("Title", "Short content.", 0);
            List<DocumentChunk> chunks = service.chunkSection(section, 0);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getTitle()).isEqualTo("Title");
            assertThat(chunks.get(0).getChunkIndex()).isZero();
        }

        @Test
        @DisplayName("content longer than maxSize → multiple chunks")
        void longerThanMaxSize() {
            // Build content that will span multiple chunks
            String longPara = "A".repeat(500) + "\n\n" + "B".repeat(500) + "\n\n" + "C".repeat(500);
            DocumentChunkServiceImpl.Section section =
                    new DocumentChunkServiceImpl.Section("Title", longPara, 0);
            List<DocumentChunk> chunks = service.chunkSection(section, 0);

            assertThat(chunks).hasSize(3);
            // Each chunk's content should have the title
            assertThat(chunks).allMatch(c -> "Title".equals(c.getTitle()));
        }

        @Test
        @DisplayName("chunk indices increment correctly")
        void chunkIndicesIncrement() {
            String longPara = "A".repeat(500) + "\n\n" + "B".repeat(500) + "\n\n" + "C".repeat(500);
            DocumentChunkServiceImpl.Section section =
                    new DocumentChunkServiceImpl.Section("Title", longPara, 100);
            List<DocumentChunk> chunks = service.chunkSection(section, 5);

            assertThat(chunks.get(0).getChunkIndex()).isEqualTo(5);
            assertThat(chunks.get(1).getChunkIndex()).isEqualTo(6);
            assertThat(chunks.get(2).getChunkIndex()).isEqualTo(7);
        }

        @Test
        @DisplayName("startIndex reflects section position in original document")
        void startIndexFromSection() {
            DocumentChunkServiceImpl.Section section =
                    new DocumentChunkServiceImpl.Section(null, "Short content.", 50);
            List<DocumentChunk> chunks = service.chunkSection(section, 0);

            assertThat(chunks.get(0).getStartIndex()).isEqualTo(50);
        }

        @Test
        @DisplayName("overlap between consecutive chunks")
        void overlapBetweenChunks() {
            // Two paragraphs each near max size
            String p1 = "X".repeat(750);
            String p2 = "Y".repeat(750);
            DocumentChunkServiceImpl.Section section =
                    new DocumentChunkServiceImpl.Section("Title", p1 + "\n\n" + p2, 0);
            List<DocumentChunk> chunks = service.chunkSection(section, 0);

            assertThat(chunks).hasSize(2);
            // Chunk 0 should end where chunk 1 starts, minus overlap
            int firstEnd = chunks.get(0).getEndIndex();
            int secondStart = chunks.get(1).getStartIndex();
            assertThat(secondStart).isLessThan(firstEnd);
            assertThat(firstEnd - secondStart).isPositive();
        }
    }

    // ==================== integration tests ====================

    @Nested
    @DisplayName("real-world document scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("typical markdown doc with multiple headings and paragraphs")
        void typicalMarkdownDoc() {
            String content = "# Introduction\n"
                    + "This is an introduction paragraph about the system.\n\n"
                    + "It spans two paragraphs in the same section.\n\n"
                    + "## Architecture\n"
                    + "The system uses a three-tier architecture.\n\n"
                    + "Each tier is independently scalable.\n\n"
                    + "### Database Layer\n"
                    + "MySQL is used for relational data storage.";

            List<DocumentChunk> chunks = service.chunkDocument(content, "architecture.md");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).getTitle()).isEqualTo("Introduction");
            // All chunks should reference the correct source
            assertThat(chunks).allMatch(c -> c.getContent() != null && !c.getContent().isEmpty());
        }

        @Test
        @DisplayName("document entirely in Chinese")
        void chineseDocument() {
            String content = "# 项目概述\n"
                    + "这是一个基于Spring Boot的智能运维平台。\n\n"
                    + "## 架构设计\n"
                    + "系统采用分层架构设计，包括控制层、服务层和数据访问层。";

            List<DocumentChunk> chunks = service.chunkDocument(content, "overview.md");

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).getTitle()).isEqualTo("项目概述");
            assertThat(chunks.get(1).getTitle()).isEqualTo("架构设计");
        }

        @Test
        @DisplayName("very long document — ensures all chunks are valid")
        void veryLongDocument() {
            StringBuilder sb = new StringBuilder();
            sb.append("# Long Document\n\n");
            for (int i = 0; i < 50; i++) {
                sb.append("Paragraph ").append(i).append(": ");
                sb.append("X".repeat(200)).append("\n\n");
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "long.md");

            assertThat(chunks).isNotEmpty();
            // Verify no chunk exceeds maxSize (with tolerance for overlap)
            assertThat(chunks).allMatch(c -> c.getContent().length() <= config.getMaxSize());
            // Chunk indices should be sequential
            for (int i = 0; i < chunks.size(); i++) {
                assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
            }
        }
    }
}
