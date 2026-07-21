'use client'

import React from 'react'
import { AuthProvider } from '@/lib/auth'
import { SettingsProvider } from '@/lib/settings'

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SettingsProvider>
      <AuthProvider>{children}</AuthProvider>
    </SettingsProvider>
  )
}
