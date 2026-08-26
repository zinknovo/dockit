package com.javaee.aiservice.agent.execution;

/**
 * 生成内容清洗：去掉 Markdown 标记、LaTeX 包裹符号，产出可写入文档编辑器的干净正文。
 */
public final class GeneratedContentSanitizer {

    private GeneratedContentSanitizer() {
    }

    public static String sanitize(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("(?s)^\\s*```[a-zA-Z]*\\s*", "");
        text = text.replaceAll("(?s)\\s*```\\s*$", "");
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*>\\s?", "");
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        text = text.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1");
        text = text.replaceAll("__([^_\\n]+)__", "$1");
        text = text.replaceAll("`([^`\\n]+)`", "$1");
        text = text.replaceAll("\\$([^$\\n]{1,200})\\$", "$1");
        text = text.replace("$", "");
        text = normalizeLatexMarkers(text);
        text = text.replaceAll("(?m)[ \\t]+$", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    public static String sanitizeAnchor(String text) {
        if (isBlank(text)) {
            return "";
        }
        String anchor = sanitize(text);
        return anchor.length() > 240 ? anchor.substring(0, 240) : anchor;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizeLatexMarkers(String text) {
        return text
                .replace("\\mathbb{C}", "C")
                .replace("\\mathbb{Q}", "Q")
                .replace("\\mathbb{R}", "R")
                .replace("\\mathbb{Z}", "Z")
                .replace("\\mathbb{N}", "N")
                .replace("\\in", "∈")
                .replace("\\sum", "∑")
                .replace("\\cdot", "·")
                .replace("\\times", "×")
                .replace("\\leq", "≤")
                .replace("\\geq", "≥")
                .replace("\\neq", "≠")
                .replace("\\mid", "|")
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\[", "")
                .replace("\\]", "");
    }
}
