'use client'

import { useEffect, useState } from 'react'
import { dashboardApi } from '@/lib/api'
import { useTranslations } from 'next-intl'

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const [stats, setStats] = useState({
    userCount: 0,
    departmentCount: 0,
    policyCount: 0,
    activePolicyCount: 0
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    dashboardApi.fetchDashboardStats().then((data) => {
      if (mounted) {
        setStats(data)
        setLoading(false)
      }
    })
    return () => {
      mounted = false
    }
  }, [])

  const cards = [
    { label: t('userCount'), value: stats.userCount },
    { label: t('departmentCount'), value: stats.departmentCount },
    { label: t('policyCount'), value: stats.policyCount },
    { label: t('activePolicyCount'), value: stats.activePolicyCount }
  ]

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <div
            key={card.label}
            className="rounded-2xl border border-[var(--default-border)] bg-[var(--default-box-color)] p-5"
          >
            <p className="text-sm text-g-600">{card.label}</p>
            <p className="mt-2 text-2xl font-semibold text-g-900">
              {loading ? '...' : card.value}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
