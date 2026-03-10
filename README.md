# Dockit — 法律文书自动归档工具 MVP

监听指定文件夹，当有新的 PDF 出现时自动识别文书类型、提取结构化信息，重命名并移动到对应案件文件夹。

## 核心流程

```
监听文件夹 → 检测到新 PDF → 提取文本 → 调 LLM 提取结构化信息 → 重命名 → 移动到案件文件夹
```

## 环境要求

- Python 3.10+
- 支持 OpenAI 兼容接口的 LLM（DeepSeek、通义千问、Kimi 等）

## 安装

```bash
cd dockit
uv sync
```

## 配置

1. 编辑 `config.yaml`，设置监听的文件夹和归档目录：

```yaml
watch_dir: "~/Downloads"      # 监听目录
archive_dir: "~/Legal/Cases"  # 归档根目录
```

2. 注册与 Token（每个用户一个 token）：

| 步骤 | 说明 |
|------|------|
| 用户下载安装包 | 安装包内已带 `api_base_url`（你的服务地址），无需用户配置 |
| 首次打开 | 弹出登录/注册界面 |
| 注册或登录 | `POST /api/auth/register` 或 `/api/auth/login`，服务端返回 `{ token, tier, limit }` |
| 客户端保存 | 将 token 写入 config，用户无需手动复制 |

即：**api_base_url 打包写死，api_token 由登录接口返回并自动保存**。当前设置窗口可手动填入 token；后续可加登录弹窗自动完成。

## 使用

```bash
uv sync
uv run dockit                    # 监听模式
uv run dockit --confirm          # 确认模式
uv run dockit --auto             # 自动模式
uv run dockit --tray             # 系统托盘（后台监听）
uv run dockit calendar           # 期限日历
uv run dockit timeline           # 案件时间线
uv run dockit prefilter-stats    # 预筛统计
uv run dockit settings           # 打开设置窗口（监听目录、API 等）
```

- **confirm 模式**：每次归档前在终端显示识别结果，输入 `y` 确认、`e` 编辑、`s` 跳过
- **auto 模式**：静默自动归档

按 Ctrl+C 优雅退出。

## 图形界面（UI）

| 入口 | 说明 |
|------|------|
| `dockit settings` | 打开设置窗口（设置、用量、期限 三个标签页） |
| `dockit --tray` | 系统托盘模式，右下角图标 |
| 托盘 → 设置 | 同 settings，配置监听目录、API Token 等 |
| 托盘 → 导出 Excel | 导出开庭记录、期限列表 |
| 托盘 → 检查期限 | 桌面通知即将到期的期限 |
| 托盘 → 退出 | 停止监听并退出 |

**首次使用**：先运行 `dockit settings`，点「登录 / 注册」完成账号登录，Token 自动填入后保存；再运行 `dockit --tray` 或 `dockit` 开始监听。

## Phase 2：数据库与导出

归档成功后自动写入 SQLite（`{archive_dir}/dockit.db`），包括案件、文书、开庭、期限。

**导出 Excel：**
```bash
uv run python -m dockit.tools.export
```
在归档目录生成 `开庭记录表.xlsx`、`关键期限.xlsx`。

**期限提醒（桌面通知）：**
```bash
uv run python -m dockit.tools.deadlines
```
可加入 crontab 或 launchd 定期运行。

## 测试用假传票

没有真实传票时，可生成假 PDF 做测试：

```bash
uv run python scripts/gen_test_pdf.py                    # 输出到 tests/fixtures/
uv run python scripts/gen_test_pdf.py ~/Downloads/xx.pdf # 指定路径
```

把生成的 PDF 复制到监听目录（如 `~/Downloads`），启动 `main.py` 即可触发识别。

**预筛**：提取文本后用关键词（传票、法院、规定、条例等）判断是否可能是法律文书，非法律文书跳过 LLM。**监控**：每条决策写入 `{archive_dir}/prefilter_log.jsonl`；**统计**：`dockit prefilter-stats` 查看通过数、跳过数、按原因分布。`config.yaml` 中 `prefilter.enabled: false` 可关闭。

**图片 OCR**：需安装 `tesseract` 及中文语言包：`brew install tesseract tesseract-lang`。Word 文档支持 .docx。

## 测试

```bash
# 快速验证（排除需真实 watchdog 的 slow 测试）
uv run pytest tests/ -v -m "not slow"

# 或运行完整验证脚本
./scripts/verify.sh

# 覆盖率
uv run pytest tests/ -m "not slow" --cov=src/dockit --cov-report=term-missing
```

## 项目结构

```
dockit/
├── config.yaml       # 用户配置
├── pyproject.toml    # 项目与依赖（uv）
├── src/
│   └── dockit/       # Python 包
│       ├── main.py   # 入口
│       ├── core/     # 监听、提取、分类、归档、预筛
│       ├── db/       # 数据层：models, SQLite
│       ├── ui/       # 托盘、设置、日历/时间线
│       └── tools/    # 导出、期限、反馈
├── scripts/          # 工具脚本（如 gen_test_pdf.py）
├── tests/
│   └── fixtures/     # 测试用 PDF 等
├── server/           # 后端 API（可选部署）
└── README.md
```

## 后端 API（可选）

如需服务方统一提供 LLM、计费、账号体系，可部署 `server/`。

**数据库**：使用 Supabase（PostgreSQL），免费层可用、数据持久化。

### 本地运行

```bash
cd server
uv sync
# 设置环境变量（见下）
uv run uvicorn main:app --host 0.0.0.0 --port 8000
```

### Supabase 配置

1. [supabase.com](https://supabase.com) 创建项目，获取连接串
2. **Settings → Database → Connection string** 复制 URI（URI 格式，含密码）
3. 创建表：任选其一
   - **Supabase CLI**：配置 `DATABASE_URL` 后执行 `./scripts/supabase-push.sh`
   - **SQL Editor**：执行 `server/migrations/0001_init.sql`
4. 环境变量：

| 变量 | 说明 |
|------|------|
| `DATABASE_URL` | Supabase 连接串，如 `postgresql://postgres:xxx@db.xxx.supabase.co:5432/postgres` |
| `DOCKIT_SECRET` | JWT 密钥（生产环境务必改） |
| `OPENAI_API_KEY` | LLM API Key（分类用） |
| `DOCKIT_ADMIN_SECRET` | 管理员密钥（可选，用于 `/api/admin/upgrade-subscription`） |

### Supabase MCP（可选）

在 Cursor 中可用 [Supabase MCP](https://github.com/supabase-community/supabase-mcp) 执行 SQL、管理迁移：

1. [Supabase Dashboard → Account → Access Tokens](https://supabase.com/dashboard/account/tokens) 创建 Personal Access Token
2. Cursor Settings → MCP → 添加 Supabase 配置（token + project-ref）
3. 需要登录时按提示操作

### API 端点

- `POST /api/auth/register` / `POST /api/auth/login` — 注册/登录
- `POST /api/classify` — 文书分类（需 Bearer token）
- `GET /api/usage` — 当月用量、订阅状态
- `POST /api/admin/upgrade-subscription` — 管理员升级订阅（需 X-Admin-Secret）

订阅：free 50 次/月；monthly/annual 不限，需 subscription_ends_at 有效。

**Prometheus + Grafana 监控**：后端暴露 `GET /metrics`，可用 Prometheus 抓取。启动监控栈：`cd server/monitoring && docker compose up -d`，Grafana 配置 Prometheus 数据源，用 `dockit_classify_total`、`dockit_auth_total` 等指标建面板。详见 `server/monitoring/README.md`。

### Render 部署（生产）

1. 将代码推送到 GitHub
2. [Render Dashboard](https://dashboard.render.com) → New → Blueprint，连接仓库
3. 选择 `render.yaml`，按提示填写环境变量：`DATABASE_URL`、`DOCKIT_SECRET`、`LLM_API_KEY`
4. 部署完成后获得 `https://dockit-api-xxx.onrender.com`，健康检查路径 `/health`

客户端配置：将 `config.example.yaml` 复制为 `config.yaml`，填入 `llm.api_base_url` 为 Render 服务地址，登录后 `api_token` 会自动保存。

### 桌面客户端打包

```bash
./scripts/build-client.sh
```

输出：`dist/dockit/`（含可执行文件及依赖）。首次使用前复制 `config.example.yaml` 为 `config.yaml` 并填写 `llm.api_base_url`。

## 修正反馈

在 confirm 模式下拒绝归档并手动修正时，修正会保存到 `{archive_dir}/corrections.jsonl`，后续 LLM 调用会将这些记录作为 few-shot 示例，提升识别准确率。
