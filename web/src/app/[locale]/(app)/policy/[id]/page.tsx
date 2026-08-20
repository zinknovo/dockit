'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import { policyApi } from '@/lib/api'
import { Link } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'

export default function PolicyDetailPage() {
  const params = useParams()
  const policyId = Number(params?.id)
  const t = useTranslations('policy')
  const tCommon = useTranslations('common')
  const [detail, setDetail] = useState<Api.Policy.PolicyDetail | null>(null)
  const [versions, setVersions] = useState<Api.Policy.PolicyVersionHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!policyId) return
    let mounted = true
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const [detailData, versionsData] = await Promise.all([
          policyApi.fetchGetPolicyDetail(policyId),
          policyApi.fetchGetPolicyVersions(policyId)
        ])
        if (mounted) {
          setDetail(detailData)
          setVersions(versionsData)
        }
      } catch (err) {
        if (mounted) {
          const message = err instanceof Error ? err.message : tCommon('loadFailed')
          setError(message)
        }
      } finally {
        if (mounted) setLoading(false)
      }
    }
    void load()
    return () => {
      mounted = false
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [policyId])

  if (loading) {
    return (
      <div className="text-sm text-g-600">
        {tCommon('loading')}
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-red-500">{error}</p>
        <Link href="/policy" className="text-theme">
          {tCommon('backToList')}
        </Link>
      </div>
    )
  }

  if (!detail) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-g-600">{t('notFound')}</p>
        <Link href="/policy" className="text-theme">
          {tCommon('backToList')}
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-g-900">{detail.name}</h2>
          <p className="mt-1 text-sm text-g-600">
            {t('policyCode')}{detail.code}
          </p>
        </div>
        <Link href="/policy" className="text-sm text-theme">
          {tCommon('backToList')}
        </Link>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-2xl border border-border bg-box p-5">
          <h3 className="text-sm font-semibold text-g-800">{t('basicInfo')}</h3>
          <dl className="mt-4 space-y-2 text-sm text-g-700">
            <div className="flex justify-between">
              <dt>{t('type')}</dt>
              <dd>{detail.typeText || detail.type}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('status')}</dt>
              <dd>{detail.statusText || detail.status}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('ownerDept')}</dt>
              <dd>{detail.ownerDeptName}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('currentVersion')}</dt>
              <dd>{detail.currentVersion}</dd>
            </div>
          </dl>
        </div>
        <div className="rounded-2xl border border-border bg-box p-5">
          <h3 className="text-sm font-semibold text-g-800">{t('timeInfo')}</h3>
          <dl className="mt-4 space-y-2 text-sm text-g-700">
            <div className="flex justify-between">
              <dt>{t('effectiveDate')}</dt>
              <dd>{detail.effectiveDate || '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('expiryDate')}</dt>
              <dd>{detail.expiryDate || '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('creator')}</dt>
              <dd>{detail.creator}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{t('updatedAt')}</dt>
              <dd>{detail.updatedAt}</dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="rounded-2xl border border-border bg-box p-5">
        <h3 className="text-sm font-semibold text-g-800">{t('versionHistory')}</h3>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-g-600">
              <tr>
                <th className="py-2">{t('version')}</th>
                <th className="py-2">{t('status')}</th>
                <th className="py-2">{t('creator')}</th>
                <th className="py-2">{t('createdAt')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {versions.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-4 text-center text-g-500">
                    {t('noVersionRecord')}
                  </td>
                </tr>
              ) : (
                versions.map((item) => (
                  <tr key={item.id} className="border-t border-border">
                    <td className="py-3">{item.version}</td>
                    <td className="py-3">{item.statusText || item.status}</td>
                    <td className="py-3">{item.creator}</td>
                    <td className="py-3">{item.createdAt}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
