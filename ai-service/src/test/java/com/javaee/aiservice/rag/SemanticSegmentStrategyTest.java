package com.javaee.aiservice.rag;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：组主题向量只算一次（此前每个句子都重算当前组主题，embedding 调用量接近翻倍）。
 */
class SemanticSegmentStrategyTest {

    @Test
    void allSentencesVectorizedInSingleBatchRequest() {
        DocumentVectorizer vectorizer = mock(DocumentVectorizer.class);
        when(vectorizer.vectorizeBatch(any(String[].class)))
                .thenAnswer(inv -> {
                    String[] texts = inv.getArgument(0);
                    float[][] result = new float[texts.length][];
                    for (int i = 0; i < texts.length; i++) {
                        result[i] = new float[]{1.0f, 0.0f};
                    }
                    return result;
                });
        SemanticSegmentStrategy strategy = new SemanticSegmentStrategy();
        ReflectionTestUtils.setField(strategy, "vectorizer", vectorizer);

        String content = "第一句内容。\n第二句内容。\n第三句内容。\n第四句内容。\n第五句内容。";
        List<SegmentStrategy.Segment> segments = strategy.segment("doc-1", content);

        // 5 句全部相似 → 1 个组：整批一次请求（修复前逐句调用 = 5 次）
        assertThat(segments).hasSize(1);
        verify(vectorizer, times(1)).vectorizeBatch(any(String[].class));
    }
}
