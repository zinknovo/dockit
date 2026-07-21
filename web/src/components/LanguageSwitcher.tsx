'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useLocale } from 'next-intl'
import { useTranslations } from 'next-intl'

export function LanguageSwitcher() {
  const locale = useLocale()
  const router = useRouter()
  const fullPathname = usePathname()
  const t = useTranslations('language')

  const targetLocale = locale === 'zh' ? 'en' : 'zh'

  const handleSwitch = () => {
    const pathWithoutLocale = fullPathname?.replace(/^\/zh|\/en/, '') || ''
    const path = pathWithoutLocale && pathWithoutLocale !== '/' ? pathWithoutLocale : '/dashboard'
    router.push(`/${targetLocale}${path}`)
  }

  return (
    <button
      type="button"
      onClick={handleSwitch}
      className="rounded-lg border border-[var(--default-border)] px-3 py-1 text-xs text-g-700 hover:bg-[var(--art-hover-color)]"
    >
      {targetLocale === 'zh' ? t('zh') : t('en')}
    </button>
  )
}
