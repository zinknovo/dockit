'use client'

import { useState } from 'react'
import { Link } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'

export default function RegisterPage() {
  const t = useTranslations('auth')
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-g-900">{t('register')}</h1>
        <p className="mt-2 text-sm text-g-600">{t('registerSubtitle')}</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block text-sm text-g-700">
          {t('phone')}
          <input
            className="mt-2 w-full rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('phonePlaceholder')}
            required
          />
        </label>

        <label className="block text-sm text-g-700">
          {t('email')}
          <input
            type="email"
            className="mt-2 w-full rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('emailPlaceholder')}
          />
        </label>

        <label className="block text-sm text-g-700">
          {t('password')}
          <input
            type="password"
            className="mt-2 w-full rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('passwordPlaceholderRegister')}
            required
          />
        </label>

        {submitted ? (
          <p className="text-sm text-g-600">{t('registeredHint')}</p>
        ) : null}

        <button
          type="submit"
          className="w-full rounded-lg border border-theme bg-transparent px-4 py-2 text-sm font-medium text-theme transition hover:bg-theme/10"
        >
          {t('submitRegister')}
        </button>
      </form>

      <div className="text-sm text-g-600">
        {t('hasAccount')}
        <Link href="/auth/login" className="ml-1 text-theme">
          {t('goLogin')}
        </Link>
      </div>
    </div>
  )
}
