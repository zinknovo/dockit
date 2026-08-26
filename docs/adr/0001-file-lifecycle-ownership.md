# ADR-0001: File 生命周期状态（版本 / 回收站）的所有权

- 状态：Accepted（现状确认）
- 日期：2026-08-26
- 领域：File / Document Version / Bucket（见 CONTEXT.md）

## Context

File 域被拆在两个服务里：

- **file-service** 拥有 `file_metadata`（File 的元数据）和内容对象（MinIO）。
- **ai-service** 拥有 File 的**生命周期状态**：版本列表（`file:versions:` Redis + `.versions/` MinIO 前缀）与回收站（`recycle:file:` Redis + `.recycle/` 前缀），并提供版本切换、回收站恢复等 HTTP 接口。

删除测试（deletion test）视角：权限校验曾以近同副本存在于 file / ai / document 三个服务（已在 ADR 前随本次重构下沉为 `common.security.BucketPermissionChecker`，各服务只剩上下文适配器）；但**生命周期状态**不是重复——它只存在于 ai-service，删除它该功能即消失。问题在于所有权分裂：一个 File 概念有两个所有者，改版本语义要跨服务脑内拼接。

## Decision

**短期（Accepted）：File 生命周期状态继续由 ai-service 拥有。**

理由（load-bearing）：

1. 版本/回收站的操作与 **Agent 工作流强耦合**：恢复文件、切换版本是 Agent Tool（`file-restore` / `file-version-switch`），且属于**危险操作需人工审批**的范畴——审批、任务快照、续接都挂在 ai-service 的 Agent 执行链路上。把生命周期迁走意味着审批链路也要跨服务。
2. 用户上下文模型不同：ai-service 用 `RequestUserContext`（JWT + 权限组），file-service 用 `SecurityContextHolder` 的 principal——回收站/版本接口如果挪到 file-service，需要 file-service 引入相同的用户上下文解析，目前只有 ai-service 有。
3. 改动面大且无独立收益：迁移涉及 Redis key 迁移、MinIO 前缀迁移、两个服务的 API 契约与前端调用方（`/api/ai/...` 回收站/版本端点）同步改。

**长期（Future ADR）：** 若未来出现以下任一前提，重新开启本 ADR 评估迁移到 file-service：

- file-service 引入与 ai-service 一致的 JWT 用户上下文解析；
- Agent 审批链路抽象为跨服务可复用（例如审批/续接不再强依赖 ai-service 的任务快照）；
- 出现第二个消费方需要直接调用版本/回收站（而不经 Agent）。

## Consequences

- 接受"元数据与生命周期状态分离"的现状：File 域的完整语义需要 file-service + ai-service 拼合，AI 导航文档（CONTEXT.md）与架构文档需注明这一点。
- 新 File 操作默认落在 ai-service（生命周期类）或 file-service（元数据/内容类），按上述所有权划分判断，不要两边各做一份。
- 回收站/版本的 Redis key 前缀（`recycle:file:` / `file:versions:`）与 MinIO 隐藏前缀（`.recycle/` / `.versions/`）是 ai-service 的内部实现细节，不对外承诺。
