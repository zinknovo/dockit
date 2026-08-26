package com.javaee.aiservice.agent.execution;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentPlanStep;
import com.javaee.aiservice.agent.execution.tool.AgentToolRegistry;
import com.javaee.aiservice.agent.execution.tool.AgentToolSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规则兜底规划：模型规划不可用或上下文条件明确时，按中文关键词匹配出单步/两步计划。
 * 覆盖文档任务的主流场景：前端写回、文件删除/恢复/版本、RAG 问答、文本加工、PPT 生成。
 */
public final class FallbackPlanner {

    private FallbackPlanner() {
    }

    public static List<AgentPlanStep> fallbackPlan(AgentExecutionRequest request, Map<String, Object> context,
                                                   AgentToolRegistry toolRegistry) {
        String task = request.getTask();
        String lower = task.toLowerCase(Locale.ROOT);
        String tool = "direct-answer";
        boolean fileOperationIntent = AgentToolSupport.isDeleteIntent(task)
                || AgentToolSupport.containsAny(task, "下载", "恢复", "回收站", "版本");
        String contextDocumentId = AgentToolSupport.asString(context.get("documentId"));

        if (!AgentToolSupport.isBlank(contextDocumentId) && AgentToolSupport.isDeleteIntent(task)) {
            AgentPlanStep step = new AgentPlanStep("step-1", "根据前端documentId永久删除对应业务文档", "file-delete", new HashMap<>());
            step.getParams().put("documentId", contextDocumentId);
            step.getParams().put("requireConfirmation", false);
            return toolRegistry.normalizePlan(List.of(step), request, context);
        }

        if ((AgentToolSupport.booleanValue(context.get("frontendDocumentWrite"))
                || !AgentToolSupport.isBlank(contextDocumentId)) && !fileOperationIntent) {
            String writeMode = AgentToolSupport.firstNonBlank(
                    AgentToolSupport.asString(context.get("writeMode")), "append");
            String knowledgeBaseId = AgentToolSupport.firstNonBlank(
                    AgentToolSupport.asString(context.get("knowledgeBaseId")),
                    AgentToolSupport.valueOrDefault(request.getKnowledgeBaseId(), "default"));

            AgentPlanStep generate = new AgentPlanStep("step-1", "根据用户要求生成可写入当前前端文档的内容", "direct-answer", new HashMap<>());
            generate.getParams().put("question", frontendDocumentWritePrompt());

            AgentPlanStep write = new AgentPlanStep("step-2", "将AI生成内容返回给前端编辑器待写入: "
                    + AgentToolSupport.valueOrDefault(contextDocumentId, "current-editor"), "document-write", new HashMap<>());
            write.getParams().put("documentId", contextDocumentId);
            write.getParams().put("content", "${answer}");
            write.getParams().put("writeMode", writeMode);
            write.getParams().put("changeLog", "AI Agent根据任务生成前端文档增量内容");
            write.getParams().put("knowledgeBaseId", knowledgeBaseId);
            write.getParams().put("selectionText", AgentToolSupport.asString(context.get("selectedText")));
            write.getParams().put("insertAfterText", "");
            write.getParams().put("contentFormat", "plain_text");
            return toolRegistry.normalizePlan(List.of(generate, write), request, context);
        } else if (!fileOperationIntent && !AgentToolSupport.isBlank(AgentToolSupport.asString(context.get("objectName")))) {
            String objectName = AgentToolSupport.asString(context.get("objectName"));
            String bucketName = AgentToolSupport.firstNonBlank(
                    AgentToolSupport.asString(context.get("bucketName")),
                    AgentToolSupport.defaultUserBucketName(request, context));
            String writeMode = AgentToolSupport.firstNonBlank(
                    AgentToolSupport.asString(context.get("writeMode")), "append");

            AgentPlanStep generate = new AgentPlanStep("step-1", "生成文本内容", "direct-answer", new HashMap<>());
            generate.getParams().put("question", task);

            AgentPlanStep write = new AgentPlanStep("step-2", "将内容返回给前端编辑器待写入: " + objectName, "text-to-file", new HashMap<>());
            write.getParams().put("content", "${answer}");
            write.getParams().put("objectName", objectName);
            write.getParams().put("bucketName", bucketName);
            write.getParams().put("writeMode", writeMode);
            write.getParams().put("contentType", "text/plain");
            return toolRegistry.normalizePlan(List.of(generate, write), request, context);
        } else if (AgentToolSupport.containsAny(task, "知识库", "文档库", "问答", "查询", "检索", "根据文档")) {
            tool = Boolean.FALSE.equals(request.getRagEnabled()) ? "direct-answer" : "rag-answer";
        } else if (AgentToolSupport.containsAny(task, "总结", "摘要")) {
            tool = "text-summarize";
        } else if (AgentToolSupport.containsAny(task, "关键词", "关键字")) {
            tool = "keyword-extract";
        } else if (AgentToolSupport.containsAny(task, "统计", "分析")) {
            tool = "text-analyze";
        } else if (AgentToolSupport.containsAny(task, "纠错", "润色", "改写", "优化")) {
            tool = "text-correct";
        } else if (lower.contains("ppt") || AgentToolSupport.containsAny(task, "演示文稿", "幻灯片")) {
            tool = "html-ppt-generate";
        } else if (AgentToolSupport.containsAny(task, "下载")) {
            tool = "file-download-url";
        } else if (AgentToolSupport.containsAny(task, "删除")) {
            tool = "file-delete";
        } else if (AgentToolSupport.containsAny(task, "恢复")) {
            tool = "file-restore";
        } else if (AgentToolSupport.containsAny(task, "回收站")) {
            tool = "recycle-list";
        } else if (AgentToolSupport.containsAny(task, "版本")) {
            tool = AgentToolSupport.containsAny(task, "切换", "恢复到") ? "file-version-switch" : "file-version-list";
        }

        AgentPlanStep step = new AgentPlanStep("step-1", task, tool, new HashMap<>());
        return toolRegistry.normalizePlan(List.of(step), request, context);
    }

    private static String frontendDocumentWritePrompt() {
        return """
                请根据下面的当前前端文档内容完成用户任务，输出一个JSON对象，不要输出Markdown、代码块或额外说明。
                JSON格式:
                {"content":"要写入文档的干净正文","writeMode":"insert","insertAfterText":"从原文中复制的一小段锚点文本","changeLog":"本次修改说明","contentFormat":"plain_text"}

                正文格式要求:
                - content 里不要出现 Markdown 符号，例如 **、* 列表符号、# 标题符号、```。
                - 数学表达不要用 $ 包裹，尽量使用普通文本或 Unicode 符号，例如 P、C、Q、R、∈、∑。
                - 不要输出“以下是”“已完成”等聊天回复，只输出可以放进文档的内容。

                插入位置要求:
                - 如果用户要求扩写、补充、续写文档中已有的某一部分，请将 writeMode 设为 insert。
                - insertAfterText 必须从原文中复制一个稳定片段，优先选择该部分最后一句或最后一段；前端会把 content 插入到这段后面。
                - 如果用户选中了文本并要求润色/纠错/替换，请将 writeMode 设为 replace-selection，insertAfterText 留空。
                - 如果用户明确要求覆盖全文，writeMode 才设为 overwrite。
                - 如果无法判断插入位置，insertAfterText 留空，前端会追加到文档末尾。

                用户任务:
                ${task}

                写入模式:
                ${writeMode}

                前端选中文本:
                ${selectedText}

                业务文档原文:
                ${documentContent}
                """;
    }
}
