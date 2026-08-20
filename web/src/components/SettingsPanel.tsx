'use client'

import { SettingsForm } from './SettingsForm'
import { useTranslations } from 'next-intl'

export function SettingsPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useTranslations('settings')
  const tCommon = useTranslations('common')

  return (
    <>
      <div
        className={`fixed inset-0 z-40 bg-black/30 transition ${open ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
        onClick={onClose}
      />
      <aside
        className={`fixed right-0 top-0 z-50 h-full w-full max-w-sm transform border-l border-border bg-box p-6 shadow-xl transition ${
          open ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-g-900">{t('panelTitle')}</h2>
          <button
            onClick={onClose}
            className="rounded-full border border-border px-2 py-1 text-xs text-g-600"
          >
            {tCommon('close')}
          </button>
        </div>
        <SettingsForm />
      </aside>
    </>
  )
}
