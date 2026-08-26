package com.javaee.aiservice.rag;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.PromptEngineeringService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库问答模块：检索 → 拼上下文 → 提示词 → 模型生成，整条链只存在一份。
 * RagController /query 与 Agent Tool（rag-answer / rag-search）都经此模块，
 * 不再各自拼装；检索策略解析、topK 上限、知识库ID默认值也在此收敛。
 */
@Service
public class KnowledgeQueryService {

    private final KnowledgeBase knowledgeBase;
    private final PromptEngineeringService promptEngineeringService;
    private final ChatService chatService;

    public KnowledgeQueryService(KnowledgeBase knowledgeBase, PromptEngineeringService promptEngineeringService,
                                 ChatService chatService) {
        this.knowledgeBase = knowledgeBase;
        this.promptEngineeringService = promptEngineeringService;
        this.chatService = chatService;
    }

    /**
     * 混合检索 + 重排，返回候选片段（不生成答案）。
     */
    public List<Map<String, Object>> search(String query, int topK, String strategy, String userId,
                                            String knowledgeBaseId) {
        Reranker.RerankStrategy rerankStrategy = parseRerankStrategy(strategy);
        int limitedTopK = Math.clamp(topK, 1, 20);
        return knowledgeBase.hybridSearchWithRerank(query, limitedTopK, rerankStrategy, userId,
                firstNonBlank(knowledgeBaseId, "default"));
    }

    /**
     * 知识库问答：检索 → 拼上下文 → 生成答案。
     * 返回 question / answer / context / sources / retrieved；modelCode 为空时走默认模型。
     */
    public Map<String, Object> ask(String question, int topK, String strategy, String userId,
                                   String knowledgeBaseId, String modelCode) {
        List<Map<String, Object>> results = search(question, topK, strategy, userId, knowledgeBaseId);
        String context = buildKnowledgeContext(results);
        String prompt = promptEngineeringService.createRagAnswerPrompt(question, context);
        String answer = chatService.callChatApiWithModelCode(prompt, modelCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", answer);
        result.put("context", context);
        result.put("sources", results.stream().map(r -> r.get("id")).toList());
        result.put("retrieved", results);
        return result;
    }

    private static String buildKnowledgeContext(List<Map<String, Object>> results) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            builder.append("来源").append(i + 1).append(" ID=").append(result.get("id")).append("\n")
                    .append(result.getOrDefault("content", "")).append("\n\n");
        }
        return builder.toString();
    }

    private static Reranker.RerankStrategy parseRerankStrategy(String strategy) {
        try {
            return Reranker.RerankStrategy.valueOf(firstNonBlank(strategy, "HYBRID").toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Reranker.RerankStrategy.HYBRID;
        }
    }

    private static String firstNonBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
