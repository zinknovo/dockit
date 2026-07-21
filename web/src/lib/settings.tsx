'use client'

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'

export type ThemeMode = 'light' | 'dark' | 'system'

export interface SettingsState {
  themeMode: ThemeMode
  primaryColor: string
  fontFamily: string
  fontSize: number
  radius: number
}

interface SettingsContextValue {
  settings: SettingsState
  setThemeMode: (mode: ThemeMode) => void
  setPrimaryColor: (color: string) => void
  setFontFamily: (font: string) => void
  setFontSize: (size: number) => void
  setRadius: (radius: number) => void
  reset: () => void
}

const DEFAULT_SETTINGS: SettingsState = {
  themeMode: 'system',
  primaryColor: '#4f46e5',
  fontFamily: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  fontSize: 14,
  radius: 0.5
}

const STORAGE_KEY = 'complog-settings'

const SettingsContext = createContext<SettingsContextValue | null>(null)

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = useState<SettingsState>(DEFAULT_SETTINGS)
  const [isHydrated, setIsHydrated] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as Partial<SettingsState>
        setSettings((prev) => ({ ...prev, ...parsed }))
      } catch {
        setSettings(DEFAULT_SETTINGS)
      }
    }
    setIsHydrated(true)
  }, [])

  useEffect(() => {
    if (!isHydrated || typeof window === 'undefined') return
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  }, [isHydrated, settings])

  useEffect(() => {
    if (typeof window === 'undefined') return
    const root = document.documentElement
    const media = window.matchMedia('(prefers-color-scheme: dark)')

    const applyTheme = () => {
      const isDark =
        settings.themeMode === 'dark' ||
        (settings.themeMode === 'system' && media.matches)
      root.classList.toggle('dark', isDark)
    }

    root.style.setProperty('--main-color', settings.primaryColor)
    root.style.setProperty('--custom-radius', `${settings.radius}rem`)
    root.style.setProperty('--app-font', settings.fontFamily)
    root.style.setProperty('--app-font-size', `${settings.fontSize}px`)

    applyTheme()

    if (settings.themeMode === 'system') {
      media.addEventListener('change', applyTheme)
      return () => media.removeEventListener('change', applyTheme)
    }
  }, [settings])

  const value = useMemo<SettingsContextValue>(
    () => ({
      settings,
      setThemeMode: (mode) => setSettings((prev) => ({ ...prev, themeMode: mode })),
      setPrimaryColor: (color) => setSettings((prev) => ({ ...prev, primaryColor: color })),
      setFontFamily: (font) => setSettings((prev) => ({ ...prev, fontFamily: font })),
      setFontSize: (size) => setSettings((prev) => ({ ...prev, fontSize: size })),
      setRadius: (radius) => setSettings((prev) => ({ ...prev, radius })),
      reset: () => setSettings(DEFAULT_SETTINGS)
    }),
    [settings]
  )

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

export function useSettings() {
  const ctx = useContext(SettingsContext)
  if (!ctx) throw new Error('useSettings must be used within SettingsProvider')
  return ctx
}
