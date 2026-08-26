'use client'

import { useEffect, useState } from 'react'
import { useAuth } from '@/lib/auth'
import { useTranslations } from 'next-intl'

export default function UserCenterPage() {
  const { user, setUser } = useAuth()
  const t = useTranslations('userCenter')
  const tUsers = useTranslations('users')
  const tCommon = useTranslations('common')
  const [basicForm, setBasicForm] = useState({ name: '', email: '' })
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    setBasicForm({ name: user?.name || '', email: user?.email || '' })
  }, [user])

  const handleBasicSave = (event: React.FormEvent) => {
    event.preventDefault()
    if (!user) return
    setUser({ ...user, name: basicForm.name, email: basicForm.email })
    setMessage(t('profileUpdated'))
  }

  const handlePasswordChange = (event: React.FormEvent) => {
    event.preventDefault()
    setMessage(t('passwordApiPending'))
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl fw-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      {message ? (
        <p className="rounded-2 bg-theme-10 px-3 py-2 text-sm text-theme">{message}</p>
      ) : null}

      <div className="row g-4">
        <section className="col-12 col-lg-6 rounded-3 border border-border bg-box p-4">
          <h3 className="text-sm fw-semibold text-g-800">{t('basicSettings')}</h3>
          <form onSubmit={handleBasicSave} className="mt-3 space-y-4">
            <label className="d-block text-sm text-g-700">
              {tUsers('name')}
              <input
                className="form-control mt-2"
                value={basicForm.name}
                onChange={(event) =>
                  setBasicForm((prev) => ({ ...prev, name: event.target.value }))
                }
              />
            </label>
            <label className="d-block text-sm text-g-700">
              {tUsers('email')}
              <input
                type="email"
                className="form-control mt-2"
                value={basicForm.email}
                onChange={(event) =>
                  setBasicForm((prev) => ({ ...prev, email: event.target.value }))
                }
              />
            </label>
            <button
              type="submit"
              className="btn btn-primary fw-medium"
            >
              {tCommon('save')}
            </button>
          </form>
        </section>

        <section className="col-12 col-lg-6 rounded-3 border border-border bg-box p-4">
          <h3 className="text-sm fw-semibold text-g-800">{t('changePassword')}</h3>
          <form onSubmit={handlePasswordChange} className="mt-3 space-y-4">
            <label className="d-block text-sm text-g-700">
              {t('currentPassword')}
              <input
                type="password"
                className="form-control mt-2"
                placeholder={t('currentPasswordPlaceholder')}
              />
            </label>
            <label className="d-block text-sm text-g-700">
              {t('newPassword')}
              <input
                type="password"
                className="form-control mt-2"
                placeholder={t('newPasswordPlaceholder')}
              />
            </label>
            <button
              type="submit"
              className="btn btn-outline-secondary"
            >
              {t('submitChange')}
            </button>
          </form>
        </section>
      </div>
    </div>
  )
}
