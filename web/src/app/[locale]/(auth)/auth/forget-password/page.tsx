'use client'

import { useState } from 'react'
import { Link } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'

export default function ForgetPasswordPage() {
  const t = useTranslations('auth')
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl fw-semibold text-g-900">{t('forgetPasswordTitle')}</h1>
        <p className="mt-2 text-sm text-g-600">{t('forgetPasswordSubtitle')}</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="d-block text-sm text-g-700">
          {t('username')}
          <input
            className="form-control mt-2"
            placeholder={t('usernamePlaceholder')}
            required
          />
        </label>

        <label className="d-block text-sm text-g-700">
          {t('verifyCode')}
          <input
            className="form-control mt-2"
            placeholder={t('verifyCodePlaceholder')}
            required
          />
        </label>

        {submitted ? (
          <p className="text-sm text-g-600">{t('applySubmitted')}</p>
        ) : null}

        <button
          type="submit"
          className="btn btn-outline-primary w-100 fw-medium"
        >
          {t('submitApply')}
        </button>
      </form>

      <div className="text-sm text-g-600">
        {t('rememberPassword')}
        <Link href="/auth/login" className="ml-1 text-theme">
          {t('backToLogin')}
        </Link>
      </div>
    </div>
  )
}
