'use client'

import { useState } from 'react'
import { Link, useRouter } from '@/i18n/navigation'
import { useAuth } from '@/lib/auth'
import { useTranslations } from 'next-intl'

export default function LoginPage() {
  const router = useRouter()
  const { login } = useAuth()
  const t = useTranslations('auth')
  const [form, setForm] = useState({ userName: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login({ userName: form.userName, password: form.password })
      router.replace('/agent')
    } catch (err) {
      const message = err instanceof Error ? err.message : t('loginFailed')
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl fw-semibold text-g-900">{t('welcomeBack')}</h1>
        <p className="mt-2 text-sm text-g-600">{t('loginSubtitle')}</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="d-block text-sm text-g-700">
          {t('username')}
          <input
            className="form-control mt-2"
            value={form.userName}
            onChange={(event) => setForm((prev) => ({ ...prev, userName: event.target.value }))}
            placeholder={t('usernamePlaceholder')}
            required
          />
        </label>

        <label className="d-block text-sm text-g-700">
          {t('password')}
          <input
            type="password"
            className="form-control mt-2"
            value={form.password}
            onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
            placeholder={t('passwordPlaceholder')}
            required
          />
        </label>

        {error ? <p className="text-sm text-danger">{error}</p> : null}

        <button
          type="submit"
          disabled={loading}
          className="btn btn-primary w-100 fw-medium"
        >
          {loading ? t('loggingIn') : t('login')}
        </button>
      </form>

      <div className="d-flex align-items-center justify-content-between text-sm text-g-600">
        <Link href="/auth/forget-password" className="text-theme">
          {t('forgetPassword')}
        </Link>
        <Link href="/auth/register" className="text-theme">
          {t('goRegister')}
        </Link>
      </div>
    </div>
  )
}
