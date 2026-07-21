# Complog Client 本地开发指南

简体中文 | [English](./README_LOCAL_SETUP.md)

## 🎯 项目说明

这是 Complog 项目的前端部分，基于 Next.js App Router，已适配后端接口。

---

## 🚀 快速开始

### 1. 安装依赖

```bash
cd complog-client
pnpm install
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env.local`。开发时通过 `NEXT_PUBLIC_API_URL` 指定 API 根地址，或留空并配合 Next.js `rewrites` 代理到后端：

```env
# 留空则同源，需在 next.config.mjs 中配置 rewrites 代理到各服务
NEXT_PUBLIC_API_URL=
# 是否携带 Cookie
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

直连 base-service 时可设：`NEXT_PUBLIC_API_URL=http://localhost:8080`。多服务（auth 8081、policy 8082）时建议用 rewrites 分流。

### 3. 启动后端服务

确保后端服务正在运行：

```bash
# base-service 运行在 http://localhost:8080
# auth-service 运行在 http://localhost:8081
# policy-service 运行在 http://localhost:8082
```

### 4. 启动前端

```bash
pnpm dev
```

前端会运行在 `http://localhost:3000`

---

## 📋 API 接口映射

### 已适配的接口

| 前端调用               | 后端接口               | 状态      |
| ---------------------- | ---------------------- | --------- |
| `GET /users`           | `GET /users`           | ✅ 已适配 |
| `POST /users`          | `POST /users`          | ✅ 已适配 |
| `GET /departments`     | `GET /departments`     | ✅ 已适配 |
| `POST /departments`    | `POST /departments`    | ✅ 已适配 |
| `POST /api/auth/login` | `POST /api/auth/login` | ✅ 已适配 |

### 字段映射

**用户列表字段转换**：

- 后端：`{ id, name, phone, deptId, deptName, role, status }`
- 前端：`{ id, userName, userPhone, userEmail, status, ... }`
- 转换逻辑在 `src/lib/api-adapter.ts`

**分页格式转换**：

- 后端：`{ count, pageNo, pageSize, lists }`
- 前端：`{ total, current, size, records }`
- 转换逻辑在 `src/lib/api-adapter.ts`

---

## 🔧 配置说明

### 环境变量

**开发环境**（`.env.local`）：

```env
NEXT_PUBLIC_API_URL=
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

**生产环境**（`.env.production` 或部署时配置）：

```env
NEXT_PUBLIC_API_URL=https://your-api-gateway.example.com
```

### Next.js 代理（可选）

多后端时可在 `next.config.mjs` 中配置 `rewrites`，将 `/api/auth`、`/users`、`/departments`、`/policies` 等转发到对应服务，并保持 `NEXT_PUBLIC_API_URL` 为空，请求即走同源代理。

---

## 📝 注意事项

### 1. 响应格式兼容

后端有两种响应格式：

- `base-service`: `{ code, msg, data }` ✅
- `auth-service`: `{ code, message, data }` ⚠️

HTTP 拦截器（`src/lib/http.ts`）已兼容两种格式。

### 2. 字段映射

前端期望的字段和后端返回的字段不完全一致，已通过适配器转换：

- `src/lib/api-adapter.ts` - 字段转换工具

### 3. 分页参数

- 前端发送：`{ current: 1, size: 20 }`
- 后端期望：`{ pageNum: 1, pageSize: 20 }`
- 已在 API 调用中自动转换

---

## 🐛 常见问题

### 1. CORS 错误

**问题**：前端无法访问后端 API

**解决**：

- 使用 Next.js `rewrites` 做同源代理（推荐），或
- 后端配置 CORS

### 2. 404 错误

**问题**：接口路径不匹配

**检查**：

- 后端接口路径是否正确
- 前端 API 调用路径是否正确
- 查看 `src/lib/api/system-manage.ts` 和 `src/lib/api/auth.ts`

### 3. 字段不匹配

**问题**：前端显示的数据不正确

**解决**：

- 检查 `src/lib/api-adapter.ts` 中的字段映射
- 根据实际后端返回调整映射逻辑

---

## 📚 相关文件

- `src/lib/api/system-manage.ts` - 系统管理 API（用户、部门）
- `src/lib/api/auth.ts` - 认证 API（登录）
- `src/lib/api-adapter.ts` - API 适配器（字段转换）
- `src/lib/http.ts` - HTTP 请求封装
- `.env.example` - 环境变量示例

---

## 🎯 下一步

1. ✅ 启动后端服务
2. ✅ 启动前端服务
3. ✅ 测试登录功能
4. ✅ 测试用户列表
5. ✅ 测试部门列表
