<h1 align="center">CompLog Client</h1>
<p align="center">Frontend for the CompLog microservice system</p>
<div align="center">English | <a href="./README.zh-CN.md">简体中文</a></div>

## Overview

This is the frontend for CompLog, adapted to the current backend APIs.

Core features:

- Login (auth-service)
- User management (base-service)
- Department management (base-service)
- Policy management + revisions (policy-service)
- Dashboard statistics

## Tech Stack

Next.js 15 + React 19 + TypeScript + Tailwind CSS + Axios

## Local Development

### 1. Install dependencies

```bash
pnpm install
# or
npm install
```

### 2. Environment variables

Copy `.env.example` to `.env.local` and configure as needed:

```
# API base URL; leave empty for same-origin (use next.config rewrites to proxy to backends)
NEXT_PUBLIC_API_URL=
# Whether to send cookies with requests
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

For direct backend access in dev, set `NEXT_PUBLIC_API_URL=http://localhost:8080` (base-service). To route multiple services (auth 8081, policy 8082), configure `rewrites` in `next.config.mjs` for `/api/auth`, `/policies`, etc., and keep `NEXT_PUBLIC_API_URL` empty.

### 3. Start backend services

- `base-service`: http://localhost:8080
- `auth-service`: http://localhost:8081
- `policy-service`: http://localhost:8082

### 4. Start frontend

```bash
pnpm dev
# or
npm run dev
```

Open: http://localhost:3000

## API and requests

- Request layer is in `src/lib/http.ts`, using `NEXT_PUBLIC_API_URL` as baseURL.
- User/department APIs: `/users`, `/departments` (base-service)
- Auth APIs: `/api/auth/login`, etc. (auth-service)
- Policy APIs: `/policies`, etc. (policy-service)

With multiple backends, use a reverse proxy or Next.js `rewrites` to forward these paths to the correct ports.

## Test Accounts

Default accounts (phone / password):

- 13800138000 / 123456
- 13800138001 / 123456
- 13800138002 / 123456

## License Notice

This project is based on `art-design-pro` (MIT License).

- Original author: Daymychen (art-design-pro).
- The original copyright and license text are retained in `LICENSE`.
- MIT License allows commercial use with proper attribution.
