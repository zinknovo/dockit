package com.javaee.aiservice.aiops;

import com.javaee.aiservice.aiops.model.TimerStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

/**
 * 把自研 AIOps 指标（MonitoringService）导出为 Prometheus 文本格式。
 * 由 Prometheus 抓取 /api/ai/aiops/prometheus（5 分钟窗口内的统计摘要）。
 */
@RestController
@RequestMapping("/api/ai/aiops")
public class PrometheusMetricsExporter {

    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final MonitoringService monitoringService;

    @Autowired
    public PrometheusMetricsExporter(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping(value = "/prometheus", produces = "text/plain; version=0.0.4")
    public String export() {
        StringBuilder sb = new StringBuilder();

        // 计数器
        sb.append("# HELP dockit_ai_counter_total AIOps 计数器（5 分钟窗口）\n");
        sb.append("# TYPE dockit_ai_counter_total counter\n");
        Map<String, Long> counters = new TreeMap<>();
        for (String name : monitoringService.getCounterNames()) {
            counters.put(name, monitoringService.getCounter(name, WINDOW_MS));
        }
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            sb.append("dockit_ai_counter_total{name=\"").append(entry.getKey()).append("\"} ")
                    .append(entry.getValue()).append('\n');
        }

        // 计时器：count/avg/p95/p99/min/max 摘要
        sb.append("# HELP dockit_ai_timer AIOps 计时器摘要（5 分钟窗口，单位 ms）\n");
        sb.append("# TYPE dockit_ai_timer gauge\n");
        for (String name : monitoringService.getTimerNames().stream().sorted().toList()) {
            TimerStats stats = monitoringService.getTimerStatsObject(name, WINDOW_MS);
            if (stats.getCount() == 0) {
                continue;
            }
            emitGauge(sb, "dockit_ai_timer_count", name, stats.getCount());
            emitGauge(sb, "dockit_ai_timer_avg_ms", name, Math.round(stats.getAvg()));
            emitGauge(sb, "dockit_ai_timer_p95_ms", name, stats.getP95());
            emitGauge(sb, "dockit_ai_timer_p99_ms", name, stats.getP99());
            emitGauge(sb, "dockit_ai_timer_min_ms", name, stats.getMin());
            emitGauge(sb, "dockit_ai_timer_max_ms", name, stats.getMax());
        }

        return sb.toString();
    }

    private void emitGauge(StringBuilder sb, String metricName, String labelValue, long value) {
        sb.append(metricName).append("{name=\"").append(labelValue).append("\"} ")
                .append(value).append('\n');
    }
}
