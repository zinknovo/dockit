'use client'

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { fetchLogin } from './api/auth'

export interface AuthUser {
  id: number
  name: string
  phone: string
  email?: string
  deptId: number
  role?: string
  status: number
}

interface AuthContextValue {
  token: string | null
  user: AuthUser | null
  ready: boolean
  login: (params: Api.Auth.LoginParams) => Promise<void>
  logout: () => void
  setUser: (user: AuthUser | null) => void
}

const TOKEN_KEY = 'complog-token'
const USER_KEY = 'complog-user'

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null)
  const [user, setUserState] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const storedToken = window.localStorage.getItem(TOKEN_KEY)
    const storedUser = window.localStorage.getItem(USER_KEY)
    if (storedToken) setToken(storedToken)
    if (storedUser) {
      try {
        setUserState(JSON.parse(storedUser) as AuthUser)
      } catch {
        setUserState(null)
      }
    }
    setReady(true)
  }, [])

  const login = async (params: Api.Auth.LoginParams) => {
    const response = await fetchLogin(params)
    setToken(response.token)
    if (response.user) setUserState(response.user)
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(TOKEN_KEY, response.token)
      if (response.user) {
        window.localStorage.setItem(USER_KEY, JSON.stringify(response.user))
      }
    }
  }

  const logout = () => {
    setToken(null)
    setUserState(null)
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(TOKEN_KEY)
      window.localStorage.removeItem(USER_KEY)
    }
  }

  const setUser = (nextUser: AuthUser | null) => {
    setUserState(nextUser)
    if (typeof window !== 'undefined') {
      if (nextUser) {
        window.localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
      } else {
        window.localStorage.removeItem(USER_KEY)
      }
    }
  }

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, ready, login, logout, setUser }),
    [token, user, ready]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
