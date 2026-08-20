'use client'

import { useState } from 'react'
import { Link, useRouter } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'
import { fetchRegister } from '@/lib/api/auth'

export default function RegisterPage() {
  const t = useTranslations('auth')
  const router = useRouter()
  const [form, setForm] = useState({ username: '', password: '', email: '', phone: '' })
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.username.trim() || !form.password.trim()) return
    setLoading(true)
    setMsg(null)
    try {
      await fetchRegister(form)
      setMsg({ type: 'success', text: t('registerSuccess') })
      setTimeout(() => router.replace('/auth/login'), 1500)
    } catch (err) {
      const message = err instanceof Error ? err.message : t('registerFailed')
      setMsg({ type: 'error', text: message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-g-900">{t('register')}</h1>
        <p className="mt-2 text-sm text-g-600">{t('registerSubtitle')}</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block text-sm text-g-700">
          {t('username')}
          <input
            className="mt-2 w-full rounded-lg border border-border bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('usernamePlaceholder')}
            value={form.username}
            onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
            required
          />
        </label>

        <label className="block text-sm text-g-700">
          {t('password')}
          <input
            type="password"
            className="mt-2 w-full rounded-lg border border-border bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('passwordPlaceholder')}
            value={form.password}
            onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
            required
          />
        </label>

        <label className="block text-sm text-g-700">
          {t('email')}
          <input
            type="email"
            className="mt-2 w-full rounded-lg border border-border bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('emailPlaceholder')}
            value={form.email}
            onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
          />
        </label>

        <label className="block text-sm text-g-700">
          {t('phone')}
          <input
            className="mt-2 w-full rounded-lg border border-border bg-transparent px-3 py-2 focus:border-theme focus:outline-none"
            placeholder={t('phonePlaceholder')}
            value={form.phone}
            onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
          />
        </label>

        {msg ? (
          <p className={`text-sm ${msg.type === 'success' ? 'text-green-500' : 'text-red-500'}`}>{msg.text}</p>
        ) : null}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-theme px-4 py-2 text-sm font-medium text-white transition hover:opacity-90 disabled:opacity-50"
        >
          {loading ? t('loggingIn') : t('submitRegister')}
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
