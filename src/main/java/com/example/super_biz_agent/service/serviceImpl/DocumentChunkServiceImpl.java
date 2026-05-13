package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.DocumentChunkConfig;
import com.example.super_biz_agent.dto.DocumentChunk;
import com.example.super_biz_agent.service.DocumentChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片实现 — 三层递进策略：标题 → 段落 → 定长截断。
 * <p>
 * 算法概要：
 * <ol>
 *   <li>用 Markdown 标题（# ~ ######）将文档切成"章节"</li>
 *   <li>每个章节按双换行（空行）切成段落</li>
 *   <li>段落拼装时若超出 max-size，则截断为一个独立分片，并从前一片末尾取 overlap 个字符作为新片开头</li>
 * </ol>
 */
@Slf4j
@Service
public class DocumentChunkServiceImpl implements DocumentChunkService {

    @Autowired
    private DocumentChunkConfig chunkConfig;

    /** 匹配 Markdown 标题行：1~6 个 # 开头，后跟空格和标题文本 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    @Override
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            log.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        // 第一步：按 Markdown 标题切分
        List<Section> sections = splitByHeadings(content);

        // 第二步：每个章节内部进一步分片
        int chunkIndex = 0;
        for (Section section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, chunkIndex);
            chunks.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }

        log.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    // ======================== 标题级切分 ========================

    /**
     * 按 Markdown 标题将文档拆分为若干章节。
     * 每个章节以标题行开始，包含该标题下的全部正文。
     */
    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();
        //用定义的正则去拆分内容
        Matcher matcher = HEADING_PATTERN.matcher(content);

        int lastStart = 0;       // 上一段在原文中的起始位置
        String currentTitle = null;

        while (matcher.find()) {
            // 保存上一个标题到当前标题之间的内容作为一个章节
            if (lastStart < matcher.start()) {
                String sectionContent = content.substring(lastStart, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    sections.add(new Section(currentTitle, sectionContent, lastStart));
                }
            }

            currentTitle = matcher.group(2).trim();   // 提取标题文本
            lastStart = matcher.start();               // 章节内容从标题行开始
        }

        // 收尾：最后一个标题之后的内容
        if (lastStart < content.length()) {
            String sectionContent = content.substring(lastStart).trim();
            if (!sectionContent.isEmpty()) {
                sections.add(new Section(currentTitle, sectionContent, lastStart));
            }
        }

        // 没有标题的纯文本，整个文档作为一个章节
        if (sections.isEmpty()) {
            sections.add(new Section(null, content, 0));
        }

        return sections;
    }

    // ======================== 段落级切分 ========================

    /**
     * 对单个章节进行分片：先按段落拆分，再根据 max-size 拼装。
     *
     * @param section       章节
     * @param startChunkIndex  起始分片编号
     * @return 该章节产生的分片列表
     */
    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content;
        String title = section.title;

        // 章节内容不超过上限，直接作为一个分片
        if (content.length() <= chunkConfig.getMaxSize()) {
            DocumentChunk chunk = new DocumentChunk(
                    content,
                    section.startIndex,
                    section.startIndex + content.length(),
                    startChunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
            return chunks;
        }

        // 超长章节：按段落拆分
        List<String> paragraphs = splitByParagraphs(content);

        StringBuilder buffer = new StringBuilder();  // 当前分片缓冲区
        int currentStart = section.startIndex;       // 当前分片在原文档中的起始位置
        int chunkIdx = startChunkIndex;

        for (String paragraph : paragraphs) {
            // 当前分片加上新段落后会超出限制 → 保存当前分片，开始新分片
            if (buffer.length() > 0 && buffer.length() + paragraph.length() > chunkConfig.getMaxSize()) {
                String chunkContent = buffer.toString().trim();
                DocumentChunk chunk = new DocumentChunk(
                        chunkContent,
                        currentStart,
                        currentStart + chunkContent.length(),
                        chunkIdx++
                );
                chunk.setTitle(title);
                chunks.add(chunk);

                // 新分片以重叠文本开头，保持跨片语义连贯
                String overlap = getOverlapText(chunkContent);
                buffer = new StringBuilder(overlap);
                currentStart = currentStart + chunkContent.length() - overlap.length();
            }

            buffer.append(paragraph).append("\n\n");
        }

        // 保存最后一个分片
        if (buffer.length() > 0) {
            String chunkContent = buffer.toString().trim();
            DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    currentStart,
                    currentStart + chunkContent.length(),
                    chunkIdx
            );
            chunk.setTitle(title);
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 按双换行（空行）拆分段落，保留 markdown 代码块等结构的完整性。
     */
    private List<String> splitByParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        String[] parts = content.split("\n\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    // ======================== 重叠文本 ========================

    /**
     * 从文本末尾截取 overlap 个字符，作为下一个分片的开头。
     * 优先在句末标点（。？！）处断开，保持语义完整。
     */
    private String getOverlapText(String text) {
        int overlapSize = Math.min(chunkConfig.getOverlap(), text.length());
        if (overlapSize <= 0) {
            return "";
        }

        String overlap = text.substring(text.length() - overlapSize);

        // 尝试在最后一个句末标点处截断
        int lastSentenceEnd = Math.max(
                overlap.lastIndexOf('。'),
                Math.max(overlap.lastIndexOf('？'), overlap.lastIndexOf('！'))
        );

        // 只有当标点位置在后半段时才采纳，避免重叠过短
        if (lastSentenceEnd > overlapSize / 2) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }

        return overlap.trim();
    }

    // ======================== 内部数据类 ========================

    /**
     * 章节 — 按标题切分后的中间产物，仅分片流程内部使用。
     */
    private static class Section {
        String title;
        String content;
        int startIndex;   // 在原文档中的起始字符位置

        Section(String title, String content, int startIndex) {
            this.title = title;
            this.content = content;
            this.startIndex = startIndex;
        }
    }


}
