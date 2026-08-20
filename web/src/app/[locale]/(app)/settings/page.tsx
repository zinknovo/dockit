import { SettingsForm } from '@/components/SettingsForm'
import { getTranslations } from 'next-intl/server'

export default async function SettingsPage() {
  const t = await getTranslations('settings')

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-g-900">{t('title')}</h2>
        <p className="mt-1 text-sm text-g-600">{t('subtitle')}</p>
      </div>

      <div className="max-w-2xl rounded-2xl border border-border bg-box p-6">
        <SettingsForm />
      </div>
    </div>
  )
}
