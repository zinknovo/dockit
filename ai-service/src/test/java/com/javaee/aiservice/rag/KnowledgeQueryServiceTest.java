package com.javaee.aiservice.rag;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.PromptEngineeringService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库问答模块：整条"检索→拼上下文→生成"链在此收敛，是控制器与 Agent Tool 的共用路径。
 */
class KnowledgeQueryServiceTest {

    private final KnowledgeBase knowledgeBase = mock(KnowledgeBase.class);
    private final PromptEngineeringService promptEngineeringService = mock(PromptEngineeringService.class);
    private final ChatService chatService = mock(ChatService.class);

    private KnowledgeQueryService service() {
        return new KnowledgeQueryService(knowledgeBase, promptEngineeringService, chatService);
    }

    @Test
    void searchDelegatesToHybridSearchWithRerank() {
        when(knowledgeBase.hybridSearchWithRerank("合同", 5, Reranker.RerankStrategy.HYBRID, "user-1", "kb-1"))
                .thenReturn(List.of(Map.of("id", "doc-1", "content", "合同片段")));

        List<Map<String, Object>> results = service().search("合同", 5, "HYBRID", "user-1", "kb-1");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry("id", "doc-1");
    }

    @Test
    void searchClampsTopKAndDefaultsKnowledgeBaseId() {
        service().search("q", 999, "BAD_STRATEGY", "user-1", null);

        verify(knowledgeBase).hybridSearchWithRerank(
                eq("q"), eq(20), eq(Reranker.RerankStrategy.HYBRID), eq("user-1"), eq("default"));
    }

    @Test
    void askRunsFullChainAndReturnsUnifiedResult() {
        when(knowledgeBase.hybridSearchWithRerank(eq("问题"), eq(3), eq(Reranker.RerankStrategy.HYBRID),
                eq("user-1"), eq("default")))
                .thenReturn(List.of(
                        Map.of("id", "doc-1", "content", "片段A"),
                        Map.of("id", "doc-2", "content", "片段B")));
        when(promptEngineeringService.createRagAnswerPrompt(eq("问题"), anyString())).thenReturn("拼好的提示词");
        when(chatService.callChatApiWithModelCode("拼好的提示词", "m1")).thenReturn("答案");

        Map<String, Object> result = service().ask("问题", 3, "HYBRID", "user-1", null, "m1");

        assertThat(result).containsEntry("question", "问题")
                .containsEntry("answer", "答案")
                .containsEntry("sources", List.of("doc-1", "doc-2"))
                .containsEntry("context", "来源1 ID=doc-1\n片段A\n\n来源2 ID=doc-2\n片段B\n\n");
        assertThat((List<Map<String, Object>>) result.get("retrieved")).hasSize(2);
        verify(chatService).callChatApiWithModelCode(eq("拼好的提示词"), eq("m1"));
    }

    @Test
    void askWithNullModelUsesDefaultModel() {
        when(knowledgeBase.hybridSearchWithRerank(any(), anyInt(), any(), any(), anyString()))
                .thenReturn(List.of());
        when(chatService.callChatApiWithModelCode(any(), eq(null))).thenReturn("默认模型回答");

        Map<String, Object> result = service().ask("问题", 3, "HYBRID", "user-1", "default", null);

        assertThat(result).containsEntry("answer", "默认模型回答");
    }
}
