# Dockit Context

Dockit 是一个微服务化的 AI 文档知识平台：用户上传/导入文件，系统管理文档版本、支持实时协作编辑，并通过 RAG 和 Agent 提供 AI 问答与自动化操作能力。

## Language

### 用户与权限

**User**:
平台的登录账号（用户名 + 密码，JWT + refresh token 认证）。
_Avoid_: account、账号（当指人时）、member

**Bucket**:
每个用户独立的文件隔离空间（对应 MinIO bucket），文档和文件都挂在 bucket 名下，权限按 bucket 校验。
_Avoid_: folder、目录（当指隔离单元时）

**Document Access**:
文档级访问权限控制，决定谁可以读写某篇文档。
_Avoid_: share、分享（当指权限模型时）

### 文件与文档

**File**:
用户上传的原始对象文件，元数据存 `file_metadata` 表，内容存对象存储。
_Avoid_: attachment、附件

**Document**:
用户在工作台创建的文档（标题 + 内容 + 对底层文件的引用 + 版本号），是业务的核心聚合。
_Avoid_: article、文章、doc

**Document Version**:
文档的一次历史快照：原始文件字节存 MinIO（objectKey 含版本号），元数据存 `document_version` 表。
_Avoid_: revision、backup、备份

**Annotation**:
文档上的批注（附着在具体位置的标记）。
_Avoid_: highlight、高亮（当指保存的批注时）

**Comment**:
文档上的评论（讨论性质，区别于批注）。
_Avoid_: note、便签

**Contract Compare**:
两版文档的差异对比能力（由外部分析服务解析 docx，返回差异项列表）。
_Avoid_: diff（当指产品功能时）、差异报告

### 实时协作

**Collaborate Session**:
多人实时协作编辑的一次会话（WebSocket 连接组），包含成员和文档快照。
_Avoid_: 共享文档、共同编辑

**Edit Operation**:
协作会话中一次编辑动作（含光标位置），在成员间广播应用。
_Avoid_: change event、变更

### AI 领域

**Conversation**:
用户与 AI 的一次对话会话，维护上下文窗口（ConversationManager / ContextManager）。
_Avoid_: chat session（当指业务概念时）

**Skill**:
面向用户的 AI 能力单元（如：上传文件、下载文件、生成 PPT），由技能注册表统一管理。
_Avoid_: capability、能力（当指产品功能时）

**Knowledge Base**:
对文档做切分 → 向量化后形成的可检索知识集合（RAG 的基础）。
_Avoid_: corpus、语料库

**Agent Plan**:
Agent 把用户目标拆解出的多步执行计划（步骤、工具调用、状态流转）。
_Avoid_: workflow（当指 Agent 计划时）

**Agent Tool**:
Agent 在执行计划中可调用的原子能力（对应 ToolDefinition + 注册表）。
_Avoid_: function call、action

**Approval**:
Agent 执行计划中高风险步骤前的人工确认环节。
_Avoid_: confirm、确认弹窗（当指该领域机制时）

**Reflection**:
Agent 执行完计划后的自我复盘环节，用于修正下一步。
_Avoid_: review、回顾（当指 Agent 机制时）

**Async Job**:
AI 耗时任务（模型调用、文件处理）异步化的作业单元，经 RabbitMQ 驱动。
_Avoid_: task（当指异步作业时）

**Fault**:
AI 服务自身运行时故障的记录（AIOps 监控体系，FaultDetector → Alert）。
_Avoid_: error、报错（当指平台级监控时）
