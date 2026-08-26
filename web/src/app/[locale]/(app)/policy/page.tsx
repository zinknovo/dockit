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
        <h2 className="text-xl fw-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="rounded-3 border border-border bg-box p-4">
        <div className="row g-3">
          <div className="col-12 col-md-4">
            <input
              className="form-control"
              placeholder={t('namePlaceholder')}
              value={filters.name}
              onChange={(event) => setFilters((prev) => ({ ...prev, name: event.target.value }))}
            />
          </div>
          <div className="col-12 col-md-4">
            <input
              className="form-control"
              placeholder={t('typePlaceholder')}
              value={filters.type}
              onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value }))}
            />
          </div>
          <div className="col-12 col-md-4">
            <input
              className="form-control"
              placeholder={t('statusPlaceholder')}
              value={filters.status}
              onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value }))}
            />
          </div>
        </div>
        <div className="mt-3 d-flex gap-2">
          <button
            onClick={() => void loadData(1)}
            className="btn btn-primary fw-medium"
          >
            {tCommon('query')}
          </button>
          <button
            onClick={() => {
              setFilters({ name: '', type: '', status: '' })
              void loadData(1)
            }}
            className="btn btn-outline-secondary"
          >
            {tCommon('reset')}
          </button>
        </div>
      </div>

      <div className="rounded-3 border border-border bg-box p-4">
        {error ? <p className="text-sm text-danger">{error}</p> : null}
        <div className="overflow-auto">
          <table className="table align-middle text-sm">
            <thead className="text-start text-g-600">
              <tr>
                <th>{t('name')}</th>
                <th>{t('code')}</th>
                <th>{t('type')}</th>
                <th>{t('status')}</th>
                <th>{t('ownerDept')}</th>
                <th>{t('action')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {loading ? (
                <tr>
                  <td colSpan={6} className="text-center text-g-500">
                    {tCommon('loading')}
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center text-g-500">
                    {tCommon('noData')}
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={item.id}>
                    <td className="fw-medium">{item.name}</td>
                    <td className="text-g-600">{item.code}</td>
                    <td className="text-g-600">{item.typeText || item.type}</td>
                    <td className="text-g-600">{item.statusText || item.status}</td>
                    <td className="text-g-600">{item.ownerDeptName}</td>
                    <td>
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

        <div className="mt-3 d-flex align-items-center justify-content-between text-sm text-g-600">
          <span>{tCommon('totalPage', { total, page })}</span>
          <div className="d-flex gap-2">
            <button
              onClick={() => loadData(page - 1)}
              disabled={page <= 1}
              className="btn btn-outline-secondary btn-sm"
            >
              {tCommon('prevPage')}
            </button>
            <button
              onClick={() => loadData(page + 1)}
              disabled={page * pageSize >= total}
              className="btn btn-outline-secondary btn-sm"
            >
              {tCommon('nextPage')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
