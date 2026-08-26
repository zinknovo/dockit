package com.javaee.aiservice.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticSegmentStrategy implements SegmentStrategy {

    private static final Logger log = LoggerFactory.getLogger(SemanticSegmentStrategy.class);

    private static final int DEFAULT_TARGET_CHUNK_SIZE = 300;
    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
    private static final int DEFAULT_SIMILARITY_THRESHOLD = 75;

    @Autowired
    private DocumentVectorizer vectorizer;

    private final int targetChunkSize;
    private final int minChunkSize;
    private final int similarityThreshold;

    public SemanticSegmentStrategy() {
        this(DEFAULT_TARGET_CHUNK_SIZE, DEFAULT_MIN_CHUNK_SIZE, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public SemanticSegmentStrategy(int targetChunkSize, int minChunkSize, int similarityThreshold) {
        this.targetChunkSize = targetChunkSize;
        this.minChunkSize = minChunkSize;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<Segment> segment(String documentId, String content) {
        log.info("执行语义分段: documentId={}, targetChunkSize={}, threshold={}",
                documentId, targetChunkSize, similarityThreshold);

        List<Segment> segments = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            log.warn("文档内容为空，跳过分段");
            return segments;
        }

        List<Sentence> sentences = splitIntoSentences(content);
        log.debug("句子拆分完成: {} 个句子", sentences.size());

        if (sentences.isEmpty()) {
            return segments;
        }

        List<SentenceGroup> groups = groupSentencesBySemantic(sentences);
        log.debug("语义分组完成: {} 个分组", groups.size());

        int segmentIndex = 0;
        for (SentenceGroup group : groups) {
            String segmentContent = buildSegmentContent(group);

            if (segmentContent.length() < minChunkSize && segmentIndex > 0) {
                if (!segments.isEmpty()) {
                    Segment lastSegment = segments.getLast();
                    String mergedContent = lastSegment.getContent() + "\n" + segmentContent;
                    lastSegment.setContent(mergedContent);
                    lastSegment.setCharCount(mergedContent.length());
                    log.debug("合并到上一个分段: segmentId={}, newCharCount={}",
                            lastSegment.getSegmentId(), mergedContent.length());
                }
            } else {
                Segment segment = new Segment(
                        generateSegmentId(documentId, segmentIndex),
                        documentId,
                        segmentContent,
                        segmentIndex,
                        group.getTheme()
                );
                segments.add(segment);
                log.debug("生成分段: segmentId={}, theme={}, charCount={}",
                        segment.getSegmentId(), group.getTheme(), segmentContent.length());
                segmentIndex++;
            }
        }

        log.info("语义分段完成: documentId={}, 生成{}个分段", documentId, segments.size());
        return segments;
    }

    private List<Sentence> splitIntoSentences(String content) {
        List<Sentence> sentences = new ArrayList<>();

        String[] lines = content.split("\n");
        StringBuilder currentParagraph = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (!currentParagraph.isEmpty()) {
                    currentParagraph.setLength(0);
                }
                continue;
            }

            if (!currentParagraph.isEmpty()) {
                currentParagraph.append(" ");
            }
            currentParagraph.append(line);

            if (currentParagraph.length() >= targetChunkSize / 2 ||
                    line.endsWith("。") || line.endsWith("！") || line.endsWith("？") ||
                    line.endsWith(".") || line.endsWith("!") || line.endsWith("?")) {

                sentences.add(new Sentence(currentParagraph.toString()));
                currentParagraph.setLength(0);
            }
        }

        if (!currentParagraph.isEmpty()) {
            sentences.add(new Sentence(currentParagraph.toString()));
        }

        return sentences;
    }

    private List<SentenceGroup> groupSentencesBySemantic(List<Sentence> sentences) {
        List<SentenceGroup> groups = new ArrayList<>();

        if (sentences.isEmpty()) {
            return groups;
        }

        // 一次批量请求向量化全部句子，避免逐句调用触发账户 QPS 限流；
        // 组主题向量复用该组首句的句子向量（theme 由首句提取，语义一致）。
        String[] sentenceTexts = new String[sentences.size()];
        for (int i = 0; i < sentences.size(); i++) {
            sentenceTexts[i] = sentences.get(i).getText();
        }
        float[][] sentenceVectors = vectorizer.vectorizeBatch(sentenceTexts);

        SentenceGroup currentGroup = new SentenceGroup(sentences.getFirst());
        groups.add(currentGroup);
        float[] currentVector = sentenceVectors[0];

        for (int i = 1; i < sentences.size(); i++) {
            Sentence sentence = sentences.get(i);
            float[] sentenceVector = sentenceVectors[i];

            float similarity = cosineSimilarity(currentVector, sentenceVector);
            int similarityPercent = (int)(similarity * 100);

            if (similarityPercent >= similarityThreshold) {
                currentGroup.addSentence(sentence);
                log.debug("句子加入当前分组: similarity={}%, groupSize={}",
                        similarityPercent, currentGroup.getSentenceCount());
            } else {
                currentGroup = new SentenceGroup(sentence);
                groups.add(currentGroup);
                currentVector = sentenceVector;
                log.debug("创建新分组: theme={}", currentGroup.getTheme());
            }
        }

        return groups;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0f;
        }

        return dotProduct / (float)(Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String buildSegmentContent(SentenceGroup group) {
        StringBuilder sb = new StringBuilder();
        for (Sentence sentence : group.getSentences()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(sentence.getText());
        }
        return sb.toString();
    }

    private String generateSegmentId(String documentId, int index) {
        return documentId + "_semantic_" + index;
    }

    @Override
    public String getStrategyName() {
        return "SEMANTIC";
    }

    private static class Sentence {
        private final String text;

        Sentence(String text) {
            this.text = text;
        }

        String getText() { return text; }
    }

    private static class SentenceGroup {
        private final List<Sentence> sentences = new ArrayList<>();
        private final String theme;

        SentenceGroup(Sentence firstSentence) {
            this.theme = extractTheme(firstSentence.getText());
            this.sentences.add(firstSentence);
        }

        void addSentence(Sentence sentence) {
            this.sentences.add(sentence);
        }

        private String extractTheme(String text) {
            int length = Math.min(50, text.length());
            return text.substring(0, length);
        }

        List<Sentence> getSentences() { return sentences; }
        String getTheme() { return theme; }
        int getSentenceCount() { return sentences.size(); }
    }
}