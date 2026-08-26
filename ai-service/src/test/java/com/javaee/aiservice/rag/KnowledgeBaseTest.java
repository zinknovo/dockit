package com.javaee.aiservice.rag;

import com.javaee.aiservice.aiops.MonitoringService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：文档内容从分段点按序拼接（此前直接读不存在的文档级点，恒返回 null）；
 * 索引中途失败时回滚已写入的分段点（此前残留孤儿向量）。
 */
class KnowledgeBaseTest {

    private final DocumentVectorizer vectorizer = mock(DocumentVectorizer.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final Reranker reranker = mock(Reranker.class);
    private final DocumentSegmenter segmenter = mock(DocumentSegmenter.class);
    private final MonitoringService monitoringService = mock(MonitoringService.class);

    private KnowledgeBase knowledgeBase() {
        return new KnowledgeBase(vectorizer, vectorStore, reranker, segmenter, monitoringService);
    }

    @Test
    void getDocumentContentConcatenatesSegmentsInOrder() {
        KnowledgeBase kb = knowledgeBase();
        when(vectorStore.scrollAll(anyMap())).thenReturn(List.of(
                point("doc-1_seg_0", 0.0, "片段A"),
                point("doc-1_seg_1", 1.0, "片段B")));
        when(vectorStore.getPayload("doc-1_seg_0")).thenReturn(Map.of("content", "片段A"));
        when(vectorStore.getPayload("doc-1_seg_1")).thenReturn(Map.of("content", "片段B"));

        assertThat(kb.getDocumentContent("doc-1")).isEqualTo("片段A\n\n片段B");
    }

    @Test
    void getDocumentContentFallsBackToWholeDocumentPoint() {
        KnowledgeBase kb = knowledgeBase();
        when(vectorStore.scrollAll(anyMap())).thenReturn(List.of());
        when(vectorStore.getPayload("doc-1")).thenReturn(Map.of("content", "整篇内容"));

        assertThat(kb.getDocumentContent("doc-1")).isEqualTo("整篇内容");
    }

    @Test
    void addDocumentRollsBackStoredSegmentsOnFailure() {
        KnowledgeBase kb = knowledgeBase();
        when(segmenter.segment(eq("doc-1"), anyString(), any())).thenReturn(List.of(
                new SegmentStrategy.Segment("doc-1_seg_0", "doc-1", "片段A", 0, null),
                new SegmentStrategy.Segment("doc-1_seg_1", "doc-1", "片段B", 1, null)));
        when(vectorizer.vectorizeBatch(any(String[].class)))
                .thenReturn(new float[][]{{1f}})
                .thenThrow(new RuntimeException("embedding 500"));

        assertThatThrownBy(() -> kb.addDocumentWithSegment("doc-1", "内容", Map.of(),
                DocumentSegmenter.StrategyType.FIXED_LENGTH))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("添加文档失败");

        verify(vectorStore).delete("doc-1_seg_0");
        verify(vectorStore, never()).delete("doc-1_seg_1");
    }

    private static Map<String, Object> point(String rawId, double segmentIndex, String content) {
        Map<String, Object> p = new HashMap<>();
        p.put("rawId", rawId);
        p.put("segmentIndex", segmentIndex);
        p.put("type", "segment");
        p.put("documentId", "doc-1");
        p.put("content", content);
        return p;
    }
}
