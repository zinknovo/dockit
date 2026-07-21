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
    loadUsers(1)
    loadDepartments()
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
      loadUsers(1)
    } catch (err) {
      const message = err instanceof Error ? err.message : tCommon('addFailed')
      setError(message)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="rounded-2xl border border-[var(--default-border)] bg-[var(--default-box-color)] p-5">
        <h3 className="text-sm font-semibold text-g-800">{t('addUser')}</h3>
        <form onSubmit={handleAddUser} className="mt-4 grid gap-3 md:grid-cols-4">
          <input
            className="rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 text-sm"
            placeholder={t('namePlaceholder')}
            value={form.name}
            onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
            required
          />
          <input
            className="rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 text-sm"
            placeholder={t('phonePlaceholder')}
            value={form.phone}
            onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
            required
          />
          <input
            className="rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 text-sm"
            placeholder={t('emailPlaceholder')}
            value={form.email}
            onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
          />
          <select
            className="rounded-lg border border-[var(--default-border)] bg-transparent px-3 py-2 text-sm"
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
          <button
            type="submit"
            className="md:col-span-4 rounded-lg bg-theme px-4 py-2 text-sm font-medium text-white"
          >
            {tCommon('submit')}
          </button>
        </form>
        {error ? <p className="mt-3 text-sm text-red-500">{error}</p> : null}
      </div>

      <div className="rounded-2xl border border-[var(--default-border)] bg-[var(--default-box-color)] p-5">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-g-600">
              <tr>
                <th className="py-2">{t('name')}</th>
                <th className="py-2">{t('phone')}</th>
                <th className="py-2">{t('email')}</th>
                <th className="py-2">{t('status')}</th>
              </tr>
            </thead>
            <tbody className="text-g-800">
              {loading ? (
                <tr>
                  <td colSpan={4} className="py-4 text-center text-g-500">
                    {tCommon('loading')}
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-4 text-center text-g-500">
                    {tCommon('noData')}
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={item.id} className="border-t border-[var(--default-border)]">
                    <td className="py-3 font-medium">{item.userName}</td>
                    <td className="py-3 text-g-600">{item.userPhone}</td>
                    <td className="py-3 text-g-600">{item.userEmail || '-'}</td>
                    <td className="py-3 text-g-600">
                      {item.status === '1' ? t('enabled') : t('disabled')}
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
              onClick={() => loadUsers(page - 1)}
              disabled={page <= 1}
              className="rounded-lg border border-[var(--default-border)] px-3 py-1 disabled:opacity-50"
            >
              {tCommon('prevPage')}
            </button>
            <button
              onClick={() => loadUsers(page + 1)}
              disabled={page * pageSize >= total}
              className="rounded-lg border border-[var(--default-border)] px-3 py-1 disabled:opacity-50"
            >
              {tCommon('nextPage')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
