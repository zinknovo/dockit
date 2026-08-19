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
      <div className="flex h-screen items-center justify-center text-sm text-g-600">
        {tCommon('loading')}
      </div>
    )
  }

  if (!token) {
    return null
  }

  return (
    <div className="flex min-h-screen bg-[var(--default-bg-color)]">
      <aside className="hidden w-56 flex-col border-r border-[var(--default-border)] bg-[var(--default-box-color)] p-6 md:flex">
        <div className="mb-8">
          <p className="text-xs uppercase tracking-[0.2em] text-g-500">Dockit</p>
          <h1 className="mt-2 text-lg font-semibold text-g-900">{t('console')}</h1>
        </div>
        <nav className="flex flex-col gap-1">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`rounded-lg px-3 py-2 text-sm transition ${
                activeHref === item.href
                  ? 'bg-theme/10 text-theme font-medium'
                  : 'text-g-700 hover:bg-[var(--art-hover-color)]'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>

      <div className="flex flex-1 flex-col">
        <header className="flex h-14 items-center justify-between border-b border-[var(--default-border)] bg-[var(--default-box-color)] px-6">
          <div className="flex items-center gap-3">
            <LanguageSwitcher />
            <button
              onClick={() => setSettingsOpen(true)}
              className="rounded-lg border border-[var(--default-border)] px-3 py-1 text-xs text-g-700 hover:bg-[var(--art-hover-color)]"
            >
              {t('settingsButton')}
            </button>
          </div>
          <div className="flex items-center gap-4 text-sm text-g-700">
            <span>{user?.name || user?.phone || t('currentUser')}</span>
            <button
              onClick={() => { logout(); router.replace('/auth/login') }}
              className="rounded-lg border border-[var(--default-border)] px-3 py-1 text-xs hover:bg-[var(--art-hover-color)]"
            >
              {t('logout')}
            </button>
          </div>
        </header>

        <main className="flex-1 p-6">{children}</main>
      </div>

      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  )
}
