# Dockit 监控（Prometheus + Grafana）

## 1. 启动监控栈

```bash
cd server/monitoring
docker compose up -d
```

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000（默认账号 admin / admin）

## 2. 配置 Grafana 数据源

1. 登录 Grafana
2. 添加数据源 → Prometheus
3. URL: `http://prometheus:9090`（同 docker 网络）
4. 保存并测试

## 3. 常用 PromQL

```promql
# 各用户 classify 总次数（按 user_id、tier）
sum by (user_id, tier) (dockit_classify_total)

# 每分钟 classify 速率
rate(dockit_classify_total[1m])

# 登录/注册次数
sum by (action) (dockit_auth_total)

# 错误数
sum by (reason) (dockit_classify_errors_total)
```

## 4. 创建面板

在 Grafana 新建 Dashboard，添加 Panel，使用上述 PromQL 即可绘制曲线或表格。
