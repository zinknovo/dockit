<h1 align="center">CompLog Client</h1>
<p align="center">CompLog 微服务系统的前端</p>
<div align="center">简体中文 | <a href="./README.md">English</a></div>

## 项目简介

这是 CompLog 的前端项目，已对接当前后端接口。

主要功能：

- 登录（auth-service）
- 用户管理（base-service）
- 部门管理（base-service）
- 制度管理与修订（policy-service）
- 仪表盘统计

## 技术栈

Next.js 15 + React 19 + TypeScript + Tailwind CSS + Axios

## 本地开发

### 1. 安装依赖

```bash
pnpm install
# 或
npm install
```

### 2. 环境变量

复制 `.env.example` 为 `.env.local`，按需配置：

```
# API 根地址，留空则同源（需配合 next.config 的 rewrites 代理到各后端）
NEXT_PUBLIC_API_URL=
# 是否携带 Cookie
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

开发时若直连后端，可设置 `NEXT_PUBLIC_API_URL=http://localhost:8080`（base-service）；若需多服务分流（auth 8081、policy 8082），建议在 `next.config.mjs` 中配置 `rewrites` 将 `/api/auth`、`/policies` 等代理到对应端口，并保持 `NEXT_PUBLIC_API_URL` 为空。

### 3. 启动后端服务

- `base-service`: http://localhost:8080
- `auth-service`: http://localhost:8081
- `policy-service`: http://localhost:8082

### 4. 启动前端

```bash
pnpm dev
# 或
npm run dev
```

打开：http://localhost:3000

## API 与请求

- 请求封装在 `src/lib/http.ts`，使用 `NEXT_PUBLIC_API_URL` 作为 baseURL。
- 用户/部门接口：`/users`、`/departments`（base-service）
- 认证接口：`/api/auth/login` 等（auth-service）
- 制度接口：`/policies` 等（policy-service）

多服务并存时需通过反向代理或 Next.js `rewrites` 将上述路径转发到对应端口。

## 测试账号

默认账号（手机号 / 密码）：

- 13800138000 / 123456
- 13800138001 / 123456
- 13800138002 / 123456

## 许可证声明

本项目基于 `art-design-pro`（MIT License）。

- 原作者：Daymychen（art-design-pro）。
- 已保留原作者版权与许可证文本，见 `LICENSE`
- MIT 许可证允许商用，但需保留版权与许可证声明
