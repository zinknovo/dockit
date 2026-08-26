'use client'

import { useEffect, useState } from 'react'
import { systemManageApi } from '@/lib/api'
import { useTranslations } from 'next-intl'

export default function UsersPage() {
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [records, setRecords] = useState<Api.SystemManage.UserListItem[]>([])
  const [departments, setDepartments] = useState<Api.SystemManage.DeptListItem[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [form, setForm] = useState({ name: '', phone: '', email: '', deptId: '' })

  const pageSize = 20

  const loadUsers = async (nextPage = page) => {
    setLoading(true)
    setError(null)
    try {
      const data = await systemManageApi.fetchGetUserList({
        current: nextPage,
        size: pageSize
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

  const loadDepartments = async () => {
    try {
      const data = await systemManageApi.fetchGetDeptList({ current: 1, size: 200 })
      setDepartments(data.records)
    } catch {
      setDepartments([])
    }
  }

  useEffect(() => {
    void loadUsers(1)
    void loadDepartments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleAddUser = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await systemManageApi.fetchAddUser({
        name: form.name,
        phone: form.phone,
        email: form.email || undefined,
        deptId: Number(form.deptId)
      })
      setForm({ name: '', phone: '', email: '', deptId: '' })
      await loadUsers(1)
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
        <h3 className="text-sm fw-semibold text-g-800">{t('addUser')}</h3>
        <form onSubmit={handleAddUser} className="mt-3 row g-3">
          <div className="col-12 col-md-3">
            <input
              className="form-control"
              placeholder={t('namePlaceholder')}
              value={form.name}
              onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
              required
            />
          </div>
          <div className="col-12 col-md-3">
            <input
              className="form-control"
              placeholder={t('phonePlaceholder')}
              value={form.phone}
              onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
              required
            />
          </div>
          <div className="col-12 col-md-3">
            <input
              className="form-control"
              placeholder={t('emailPlaceholder')}
              value={form.email}
              onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
            />
          </div>
          <div className="col-12 col-md-3">
            <select
              className="form-select"
              value={form.deptId}
              onChange={(event) => setForm((prev) => ({ ...prev, deptId: event.target.value }))}
              required
            >
              <option value="">{t('selectDept')}</option>
              {departments.map((dept) => (
                <option key={dept.id} value={dept.id}>
                  {dept.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12">
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
                <th>{t('name')}</th>
                <th>{t('phone')}</th>
                <th>{t('email')}</th>
                <th>{t('status')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {loading ? (
                <tr>
                  <td colSpan={4} className="text-center text-g-500">
                    {tCommon('loading')}
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td colSpan={4} className="text-center text-g-500">
                    {tCommon('noData')}
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={item.id}>
                    <td className="fw-medium">{item.userName}</td>
                    <td className="text-g-600">{item.userPhone}</td>
                    <td className="text-g-600">{item.userEmail || '-'}</td>
                    <td className="text-g-600">
                      {item.status === '1' ? t('enabled') : t('disabled')}
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
              onClick={() => loadUsers(page - 1)}
              disabled={page <= 1}
              className="btn btn-outline-secondary btn-sm"
            >
              {tCommon('prevPage')}
            </button>
            <button
              onClick={() => loadUsers(page + 1)}
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
