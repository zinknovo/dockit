'use client'

import { useEffect, useState } from 'react'
import { policyApi } from '@/lib/api'
import { Link as I18nLink } from '@/i18n/navigation'
import { useTranslations } from 'next-intl'

export default function PolicyListPage() {
  const t = useTranslations('policy')
  const tCommon = useTranslations('common')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [records, setRecords] = useState<Api.Policy.PolicyListItem[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [filters, setFilters] = useState({ name: '', type: '', status: '' })

  const pageSize = 20

  const loadData = async (nextPage = page) => {
    setLoading(true)
    setError(null)
    try {
      const data = await policyApi.fetchGetPolicyList({
        current: nextPage,
        size: pageSize,
        name: filters.name || undefined,
        type: filters.type ? Number(filters.type) : undefined,
        status: filters.status ? Number(filters.status) : undefined
      })
      setRecords(data.records)
      setTotal(data.total)
      setPage(data.current)
    } catch (err) {
      const message = err instanceof Error ? err.message : tCommon('loadFailed')
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="rounded-2xl border border-border bg-box p-5">
        <div className="grid gap-3 md:grid-cols-3">
          <input
            className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm"
            placeholder={t('namePlaceholder')}
            value={filters.name}
            onChange={(event) => setFilters((prev) => ({ ...prev, name: event.target.value }))}
          />
          <input
            className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm"
            placeholder={t('typePlaceholder')}
            value={filters.type}
            onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value }))}
          />
          <input
            className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm"
            placeholder={t('statusPlaceholder')}
            value={filters.status}
            onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value }))}
          />
        </div>
        <div className="mt-4 flex gap-3">
          <button
            onClick={() => void loadData(1)}
            className="rounded-lg bg-theme px-4 py-2 text-sm font-medium text-white"
          >
            {tCommon('query')}
          </button>
          <button
            onClick={() => {
              setFilters({ name: '', type: '', status: '' })
              void loadData(1)
            }}
            className="rounded-lg border border-border px-4 py-2 text-sm"
          >
            {tCommon('reset')}
          </button>
        </div>
      </div>

      <div className="rounded-2xl border border-border bg-box p-5">
        {error ? <p className="text-sm text-red-500">{error}</p> : null}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-g-600">
              <tr>
                <th className="py-2">{t('name')}</th>
                <th className="py-2">{t('code')}</th>
                <th className="py-2">{t('type')}</th>
                <th className="py-2">{t('status')}</th>
                <th className="py-2">{t('ownerDept')}</th>
                <th className="py-2">{t('action')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-g-500">
                    {tCommon('loading')}
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-g-500">
                    {tCommon('noData')}
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={item.id} className="border-t border-border">
                    <td className="py-3 font-medium">{item.name}</td>
                    <td className="py-3 text-g-600">{item.code}</td>
                    <td className="py-3 text-g-600">{item.typeText || item.type}</td>
                    <td className="py-3 text-g-600">{item.statusText || item.status}</td>
                    <td className="py-3 text-g-600">{item.ownerDeptName}</td>
                    <td className="py-3">
                      <I18nLink href={`/policy/${item.id}`} className="text-theme">
                        {tCommon('detail')}
                      </I18nLink>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex items-center justify-between text-sm text-g-600">
          <span>{tCommon('totalPage', { total, page })}</span>
          <div className="flex gap-2">
            <button
              onClick={() => loadData(page - 1)}
              disabled={page <= 1}
              className="rounded-lg border border-border px-3 py-1 disabled:opacity-50"
            >
              {tCommon('prevPage')}
            </button>
            <button
              onClick={() => loadData(page + 1)}
              disabled={page * pageSize >= total}
              className="rounded-lg border border-border px-3 py-1 disabled:opacity-50"
            >
              {tCommon('nextPage')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
