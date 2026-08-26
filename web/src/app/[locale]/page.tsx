'use client'

import { useEffect } from 'react'
import { useRouter } from '@/i18n/navigation'
import { useAuth } from '@/lib/auth'
import { useTranslations } from 'next-intl'

export default function HomePage() {
  const router = useRouter()
  const { token, ready } = useAuth()
  const t = useTranslations('common')

  useEffect(() => {
    if (!ready) return
    router.replace(token ? '/agent' : '/auth/login')
  }, [ready, token, router])

  return (
    <div className="d-flex vh-100 align-items-center justify-content-center text-sm text-g-600">
      {t('redirecting')}
    </div>
  )
}
