'use client'

import { useEffect, useState } from 'react'
import { systemManageApi } from '@/lib/api'
import { useTranslations } from 'next-intl'

export default function DepartmentsPage() {
  const t = useTranslations('departments')
  const tCommon = useTranslations('common')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [records, setRecords] = useState<Api.SystemManage.DeptListItem[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [form, setForm] = useState({ name: '', parentId: '' })

  const pageSize = 20

  const loadDepts = async (nextPage = page) => {
    setLoading(true)
    setError(null)
    try {
      const data = await systemManageApi.fetchGetDeptList({ current: nextPage, size: pageSize })
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
    void loadDepts(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleAddDept = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await systemManageApi.fetchAddDept({
        name: form.name,
        parentId: form.parentId ? Number(form.parentId) : undefined
      })
      setForm({ name: '', parentId: '' })
      await loadDepts(1)
    } catch (err) {
      const message = err instanceof Error ? err.message : tCommon('addFailed')
      setError(message)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl fw-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="rounded-3 border border-border bg-box p-4">
        <h3 className="text-sm fw-semibold text-g-800">{t('addDept')}</h3>
        <form onSubmit={handleAddDept} className="mt-3 row g-3">
          <div className="col-12 col-md-4">
            <input
              className="form-control"
              placeholder={t('deptNamePlaceholder')}
              value={form.name}
              onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
              required
            />
          </div>
          <div className="col-12 col-md-4">
            <input
              className="form-control"
              placeholder={t('parentIdPlaceholder')}
              value={form.parentId}
              onChange={(event) => setForm((prev) => ({ ...prev, parentId: event.target.value }))}
            />
          </div>
          <div className="col-12 col-md-4">
            <button
              type="submit"
              className="btn btn-primary fw-medium"
            >
              {tCommon('submit')}
            </button>
          </div>
        </form>
        {error ? <p className="mt-3 text-sm text-danger">{error}</p> : null}
      </div>

      <div className="rounded-3 border border-border bg-box p-4">
        <div className="overflow-auto">
          <table className="table align-middle text-sm">
            <thead className="text-start text-g-600">
              <tr>
                <th>{t('deptName')}</th>
                <th>{t('parentId')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {loading ? (
                <tr>
                  <td colSpan={2} className="text-center text-g-500">
                    {tCommon('loading')}
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td colSpan={2} className="text-center text-g-500">
                    {tCommon('noData')}
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={item.id}>
                    <td className="fw-medium">{item.name}</td>
                    <td className="text-g-600">{item.parentId ?? '-'}</td>
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
              onClick={() => loadDepts(page - 1)}
              disabled={page <= 1}
              className="btn btn-outline-secondary btn-sm"
            >
              {tCommon('prevPage')}
            </button>
            <button
              onClick={() => loadDepts(page + 1)}
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
