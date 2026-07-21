# Complog Client Local Development Guide

English | [简体中文](./README_LOCAL_SETUP.zh-CN.md)

## 🎯 Overview

This is the frontend of the Complog project, built with Next.js App Router and adapted to the backend APIs.

---

## 🚀 Quick Start

### 1. Install dependencies

```bash
cd complog-client
pnpm install
```

### 2. Configure environment variables

Copy `.env.example` to `.env.local`. In development, set `NEXT_PUBLIC_API_URL` to your API base URL, or leave it empty and use Next.js `rewrites` to proxy to backends:

```env
# Leave empty for same-origin; configure rewrites in next.config.mjs to proxy to each service
NEXT_PUBLIC_API_URL=
# Whether to send cookies with requests
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

For direct base-service access, set `NEXT_PUBLIC_API_URL=http://localhost:8080`. For multiple backends (auth 8081, policy 8082), use rewrites to route by path.

### 3. Start backend services

Make sure backend services are running:

```bash
# base-service at http://localhost:8080
# auth-service at http://localhost:8081
# policy-service at http://localhost:8082
```

### 4. Start frontend

```bash
pnpm dev
```

The frontend runs at `http://localhost:3000`.

---

## 📋 API Mapping

### Integrated endpoints

| Frontend Call          | Backend Endpoint       | Status   |
| ---------------------- | ---------------------- | -------- |
| `GET /users`           | `GET /users`           | ✅ Ready |
| `POST /users`          | `POST /users`          | ✅ Ready |
| `GET /departments`     | `GET /departments`     | ✅ Ready |
| `POST /departments`    | `POST /departments`    | ✅ Ready |
| `POST /api/auth/login` | `POST /api/auth/login` | ✅ Ready |

### Field mapping

**User list mapping**:

- Backend: `{ id, name, phone, deptId, deptName, role, status }`
- Frontend: `{ id, userName, userPhone, userEmail, status, ... }`
- Mapping in `src/lib/api-adapter.ts`

**Pagination mapping**:

- Backend: `{ count, pageNo, pageSize, lists }`
- Frontend: `{ total, current, size, records }`
- Mapping in `src/lib/api-adapter.ts`

---

## 🔧 Configuration

### Environment variables

**Development** (`.env.local`):

```env
NEXT_PUBLIC_API_URL=
NEXT_PUBLIC_WITH_CREDENTIALS=false
```

**Production** (`.env.production` or deployment config):

```env
NEXT_PUBLIC_API_URL=https://your-api-gateway.example.com
```

### Next.js proxy (optional)

With multiple backends, add `rewrites` in `next.config.mjs` to forward `/api/auth`, `/users`, `/departments`, `/policies` to the correct services. Keep `NEXT_PUBLIC_API_URL` empty so requests go through the same-origin proxy.

---

## 📝 Notes

### 1. Response format compatibility

The backend has two response formats:

- `base-service`: `{ code, msg, data }` ✅
- `auth-service`: `{ code, message, data }` ⚠️

The HTTP layer (`src/lib/http.ts`) supports both.

### 2. Field mapping

Frontend fields differ from backend responses and are adapted via:

- `src/lib/api-adapter.ts` - field mapping

### 3. Pagination parameters

- Frontend sends: `{ current: 1, size: 20 }`
- Backend expects: `{ pageNum: 1, pageSize: 20 }`
- Mapping is done in API calls

---

## 🐛 Common Issues

### 1. CORS error

**Problem**: Frontend cannot access backend APIs

**Fix**:

- Use Next.js `rewrites` for same-origin proxy (recommended), or
- Configure CORS on the backend

### 2. 404 error

**Problem**: API path mismatch

**Check**:

- Backend endpoint paths
- Frontend API paths
- Files: `src/lib/api/system-manage.ts`, `src/lib/api/auth.ts`

### 3. Field mismatch

**Problem**: Frontend data looks incorrect

**Fix**:

- Check mappings in `src/lib/api-adapter.ts`
- Adjust mapping based on actual backend response

---

## 📚 Related Files

- `src/lib/api/system-manage.ts` - system management APIs (users, departments)
- `src/lib/api/auth.ts` - auth APIs (login)
- `src/lib/api-adapter.ts` - API adapter (field mapping)
- `src/lib/http.ts` - HTTP request wrapper
- `.env.example` - environment variable example

---

## 🎯 Next Steps

1. ✅ Start backend services
2. ✅ Start frontend
3. ✅ Test login
4. ✅ Test user list
5. ✅ Test department list
