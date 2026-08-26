package com.javaee.aiservice.rag;

import com.javaee.aiservice.aiops.MonitoringService;
import com.javaee.aiservice.aiops.model.TimerStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * RAG 索引规模 + 检索延迟 benchmark（手工运行，默认跳过）。
 * 运行：需要本地 Qdrant（:6333）与 .env 中的 ARK/DASHSCOPE key。
 *   source ../.env && mvn test -Dtest=RagBenchmarkTest -Dbenchmark=true
 * 产出：各规模档位的索引吞吐、单文档索引耗时、检索 P50/P95。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class RagBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(RagBenchmarkTest.class);

    @Autowired
    private KnowledgeBase knowledgeBase;

    @Autowired
    private MonitoringService monitoringService;

    /** 每份合成文档约 2000 字 */
    private static final String[] TEMPLATES = {
            "本合同由甲乙双方于%s年%s月签订，约定%s。其中违约责任条款规定：任何一方违反本合同约定，应承担违约责任并赔偿守约方因此遭受的全部损失，包括但不限于直接损失、可得利益损失以及为实现债权支出的律师费、诉讼费、保全费等合理费用。",
            "技术方案说明：本系统采用微服务架构，核心模块包括%s。系统通过消息队列实现异步解耦，通过对象存储保存文件，通过向量数据库实现语义检索，整体设计遵循高内聚低耦合原则。",
            "会议纪要：本次会议讨论了%s。与会人员一致认为需要建立完善的流程规范，明确各环节负责人与时间节点，确保项目按期交付。会议决定形成书面决议并跟踪落实。",
            "产品需求文档：功能模块%s需要支持并发访问，响应时间应控制在合理范围内。系统需提供完整的审计日志，记录关键操作与数据变更，满足合规要求。",
            "操作手册：使用本功能前请确认%s。系统支持批量导入与导出，支持权限分级管理，管理员可配置细粒度访问控制策略，普通用户仅可操作授权范围内的数据。",
    };
    private static final String[] FILLERS = {
            "文档管理、版本控制、实时协作",
            "智能检索、知识问答、文档分析",
            "权限校验、审批流程、消息通知",
            "上传下载、格式转换、内容解析",
            "数据备份、容灾恢复、安全审计",
    };

    @Test
    void indexScaleAndSearchLatencyBenchmark() {
        // 200 人以下公司实际纳入文档平台的活跃文档：约 500~1500 篇（合同 300 + 纪要 300 + 文档 500 + 制度 400）。
        // 档位按时间成本折中（embedding 为主）：500 篇 ≈ 3 分钟，1000 篇 ≈ 5 分钟。
        // 可用 -Dbenchmark.scales=500,1000 指定档位，默认 500 + 1000。
        int[] scales = {500, 1000};
        String scalesProp = System.getProperty("benchmark.scales");
        if (scalesProp != null && !scalesProp.isBlank()) {
            scales = Arrays.stream(scalesProp.split(",")).mapToInt(Integer::parseInt).toArray();
        }
        int maxScale = Integer.parseInt(System.getProperty("benchmark.max", "1000"));
        if (maxScale > 1000) {
            scales = new int[]{500, 1000, maxScale};
        }
        String query = "违约责任条款中守约方可以获得哪些赔偿？";
        int queryRounds = 10;

        log.info("========== RAG Benchmark 开始 ==========");
        log.info("环境: embedding=doubao-embedding-vision(火山), 向量库=Qdrant, rerank=qwen3-rerank(DashScope)");
        log.info("检索模式: hybridSearchWithRerank(HYBRID), 目标量级: 200人以下公司 500~1500 篇");

        int indexed = 0;
        List<Long> indexLatencies = new ArrayList<>();
        try {
            long totalIndexStart = System.currentTimeMillis();

            for (int scale : scales) {
                int batch = scale - indexed;
                long batchStart = System.currentTimeMillis();
                for (int i = 0; i < batch; i++) {
                    String docId = "benchmark-doc-" + (indexed + 1);
                    String content = syntheticDocument(indexed);
                    Map<String, Object> metadata = Map.of("benchmark", "true", "docIndex", String.valueOf(indexed));
                    long t0 = System.currentTimeMillis();
                    // 固定长度分段：每篇 1 次 embedding 请求，测全网吞吐；
                    // 语义分段（AUTO 用于 >2000 字无章节长文）单篇请求数随句子数增长，QPS 成本单独用
                    // documentSizeScaleBenchmark 的 50000 字档位观测。
                    knowledgeBase.addDocument(docId, content, metadata, DocumentSegmenter.StrategyType.FIXED_LENGTH);
                    indexLatencies.add(System.currentTimeMillis() - t0);
                    indexed++;
                }
                long batchCost = System.currentTimeMillis() - batchStart;

                // 检索延迟：跑 queryRounds 次，统计 P50/P95
                List<Long> searchLatencies = new ArrayList<>();
                for (int r = 0; r < queryRounds; r++) {
                    long t0 = System.currentTimeMillis();
                    knowledgeBase.hybridSearchWithRerank(query, 5, Reranker.RerankStrategy.HYBRID,
                            DocumentSegmenter.StrategyType.CHAPTER);
                    searchLatencies.add(System.currentTimeMillis() - t0);
                }
                Collections.sort(searchLatencies);
                long p50 = percentile(searchLatencies, 0.50);
                long p95 = percentile(searchLatencies, 0.95);
                long avg = (long) searchLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
                double throughput = batch * 1000.0 / batchCost;

                log.info("规模={} 份 | 本批索引耗时={}ms | 吞吐={} 份/秒 | 检索(10次): avg={}ms p50={}ms p95={}ms",
                        scale, batchCost, Math.round(throughput), avg, p50, p95);
            }

            long totalCost = System.currentTimeMillis() - totalIndexStart;
            Collections.sort(indexLatencies);
            log.info("--------- 汇总 ---------");
            log.info("总索引文档: {} 份, 总耗时 {}ms, 平均 {}ms/份, p50={}ms p95={}ms",
                    indexed, totalCost, Math.round(totalCost / (double) indexed),
                    percentile(indexLatencies, 0.50), percentile(indexLatencies, 0.95));
        } finally {
            // 兜底清理：失败中断时 benchmark 文档会残留；removeDocument 对不存在文档幂等
            int cleaned = 0;
            for (int i = 1; i <= maxScale; i++) {
                try {
                    knowledgeBase.removeDocument("benchmark-doc-" + i);
                    cleaned++;
                } catch (Exception ignored) {
                    // 清理失败不阻断
                }
            }
            log.info("已清理 {} 份 benchmark 文档", cleaned);
        }

        log.info("--------- 检索分段耗时（MonitoringService 埋点） ---------");
        printTimer("rag.embedding", "embedding(外部API)");
        printTimer("rag.vector-search", "Qdrant 向量检索");
        printTimer("rag.rerank", "rerank(外部API)");
        printTimer("rag.search", "检索总耗时");
        log.info("========== RAG Benchmark 结束 ==========");
    }

    /**
     * 单篇文档规模档位：索引耗时与单篇内容长度的关系（embedding 外部 API 主导）。
     * 每档只索引 1 篇，运行时长 ≈ 3 次 embedding + 3 次检索，秒级完成。
     */
    @Test
    void documentSizeScaleBenchmark() {
        int[] sizes = {2_000, 10_000, 50_000};
        // 用 -Dbenchmark.sizes=2000,10000 收紧档位（5 万字档语义分段分钟级，单独跑）
        String sizesProp = System.getProperty("benchmark.sizes");
        if (sizesProp != null && !sizesProp.isBlank()) {
            sizes = Arrays.stream(sizesProp.split(",")).mapToInt(Integer::parseInt).toArray();
        }
        String query = "违约责任条款中守约方可以获得哪些赔偿？";
        log.info("========== 单篇文档规模 Benchmark 开始 ==========");
        try {
            for (int size : sizes) {
                String docId = "benchmark-size-" + size;
                String content = syntheticDocumentOfSize(size, 0);
                long t0 = System.currentTimeMillis();
                knowledgeBase.addDocument(docId, content,
                        Map.of("benchmark", "true", "docSize", String.valueOf(size)),
                        DocumentSegmenter.StrategyType.AUTO);
                long indexMs = System.currentTimeMillis() - t0;

                long searchT0 = System.currentTimeMillis();
                List<Map<String, Object>> hits = knowledgeBase.hybridSearchWithRerank(query, 5,
                        Reranker.RerankStrategy.HYBRID, DocumentSegmenter.StrategyType.CHAPTER);
                long searchMs = System.currentTimeMillis() - searchT0;

                log.info("单篇 {} 字 | 索引 {}ms | 检索 {}ms | 命中 {} 段",
                        size, indexMs, searchMs, hits.size());
                knowledgeBase.removeDocument(docId);
            }
        } finally {
            for (int size : sizes) {
                try {
                    knowledgeBase.removeDocument("benchmark-size-" + size);
                } catch (Exception ignored) {
                    // 清理失败不阻断
                }
            }
        }
        log.info("========== 单篇文档规模 Benchmark 结束 ==========");
    }

    private static String syntheticDocument(int seed) {
        return syntheticDocumentOfSize(2_000, seed);
    }

    private static String syntheticDocumentOfSize(int targetChars, int seed) {
        StringBuilder sb = new StringBuilder();
        int paragraphs = 4;
        int p = 0;
        while (sb.length() < targetChars) {
            String template = TEMPLATES[(seed + p) % TEMPLATES.length];
            String filler = FILLERS[(seed + p * 2) % FILLERS.length];
            sb.append(String.format(template, 2020 + seed % 5, 1 + seed % 12, filler)).append('\n');
            // 每段约 500 字：重复填充不同业务名词
            for (int i = 0; i < 8; i++) {
                sb.append("条款").append(i + 1).append("：").append(FILLERS[(seed + i) % FILLERS.length])
                        .append("相关的流程与要求应当予以明确，确保执行一致性与可追溯性。").append('\n');
            }
            p++;
        }
        return sb.toString();
    }

    private void printTimer(String name, String label) {
        TimerStats stats = monitoringService.getTimerStatsObject(name, 60 * 60 * 1000L);
        if (stats.getCount() == 0) {
            log.info("{} ({}): 无数据", label, name);
            return;
        }
        log.info("{} ({}): count={} avg={}ms p95={}ms p99={}ms max={}ms",
                label, name, stats.getCount(), Math.round(stats.getAvg()), stats.getP95(), stats.getP99(), stats.getMax());
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(sorted.size() * p) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }
}
