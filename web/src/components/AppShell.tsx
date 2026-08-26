'use client'

import { Link, usePathname, useRouter } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/lib/auth'
import { LanguageSwitcher } from './LanguageSwitcher'
import { SettingsPanel } from './SettingsPanel'

const NAV_HREFS = [
  '/agent',
  '/dashboard',
  '/system/user-center',
  '/settings'
] as const

export function AppShell({ children }: { children: React.ReactNode }) {
  const { token, ready, user, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const t = useTranslations('nav')
  const tCommon = useTranslations('common')
  const [settingsOpen, setSettingsOpen] = useState(false)

  const navItems = [
    { label: t('agent'), href: NAV_HREFS[0] },
    { label: t('dashboard'), href: NAV_HREFS[1] },
    { label: t('userCenter'), href: NAV_HREFS[2] },
    { label: t('settings'), href: NAV_HREFS[3] }
  ]

  useEffect(() => {
    if (!ready) return
    if (!token) router.replace('/auth/login')
  }, [ready, token, router])

  const activeHref = useMemo(() => {
    if (!pathname) return ''
    if (pathname.startsWith('/agent')) return '/agent'
    if (pathname.startsWith('/system/user-center')) return '/system/user-center'
    if (pathname.startsWith('/settings')) return '/settings'
    return '/dashboard'
  }, [pathname])

  if (!ready) {
    return (
      <div className="d-flex vh-100 align-items-center justify-content-center text-sm text-g-600">
        {tCommon('loading')}
      </div>
    )
  }

  if (!token) {
    return null
  }

  return (
    <div className="d-flex min-vh-100 bg-page">
      <aside className="d-none w-56 flex-column border-end border-border bg-box p-4 d-md-flex">
        <div className="mb-4">
          <p className="text-xs text-uppercase tracking-02 text-g-500">Dockit</p>
          <h1 className="mt-2 text-lg fw-semibold text-g-900">{t('console')}</h1>
        </div>
        <nav className="d-flex flex-column gap-1">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`rounded-2 px-3 py-2 text-sm transition ${
                activeHref === item.href
                  ? 'bg-theme-10 text-theme fw-medium'
                  : 'text-g-700 hover-bg-hover-color'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>

      <div className="d-flex flex-grow-1 flex-column">
        <header className="d-flex h-14 align-items-center justify-content-between border-bottom border-border bg-box px-4">
          <div className="d-flex align-items-center gap-3">
            <LanguageSwitcher />
            <button
              onClick={() => setSettingsOpen(true)}
              className="rounded-2 border border-border px-3 py-1 text-xs text-g-700 hover-bg-hover-color"
            >
              {t('settingsButton')}
            </button>
          </div>
          <div className="d-flex align-items-center gap-3 text-sm text-g-700">
            <span>{user?.name || user?.phone || t('currentUser')}</span>
            <button
              onClick={() => { logout(); router.replace('/auth/login') }}
              className="rounded-2 border border-border px-3 py-1 text-xs hover-bg-hover-color"
            >
              {t('logout')}
            </button>
          </div>
        </header>

        <main className="flex-grow-1 p-4">{children}</main>
      </div>

      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  )
}
