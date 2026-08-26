'use client'

import { useSettings } from '@/lib/settings'
import { useTranslations } from 'next-intl'

const FONT_OPTIONS = [
  {
    label: 'Noto Sans SC',
    value: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  {
    label: 'Source Han Sans',
    value: "'Source Han Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  {
    label: 'Songti Serif',
    value: "'Songti SC', 'STSong', serif"
  }
]

export function SettingsForm() {
  const t = useTranslations('settings')
  const { settings, setThemeMode, setPrimaryColor, setFontFamily, setFontSize, setRadius, reset } =
    useSettings()

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <h3 className="text-sm fw-semibold text-g-800">{t('themeMode')}</h3>
        <div className="d-flex gap-2">
          {(['light', 'dark', 'system'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setThemeMode(mode)}
              className={`rounded-pill border px-3 py-1 text-xs fw-medium transition ${
                settings.themeMode === mode
                  ? 'border-theme bg-theme-10 text-theme'
                  : 'border-border text-g-700'
              }`}
            >
              {mode === 'light' ? t('light') : mode === 'dark' ? t('dark') : t('system')}
            </button>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm fw-semibold text-g-800">{t('themeColor')}</h3>
        <div className="d-flex align-items-center gap-3">
          <input
            type="color"
            value={settings.primaryColor}
            onChange={(event) => setPrimaryColor(event.target.value)}
            className="h-8 w-12 cursor-pointer rounded-1 border border-border bg-transparent"
          />
          <span className="text-xs text-g-600">{settings.primaryColor}</span>
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm fw-semibold text-g-800">{t('font')}</h3>
        <div className="space-y-2">
          {FONT_OPTIONS.map((option) => (
            <label
              key={option.label}
              className={`d-flex cursor-pointer align-items-center justify-content-between rounded-2 border px-3 py-2 text-sm transition ${
                settings.fontFamily === option.value
                  ? 'border-theme bg-theme-10 text-theme'
                  : 'border-border text-g-700'
              }`}
              style={{ fontFamily: option.value }}
            >
              <span>{option.label}</span>
              <input
                type="radio"
                name="font"
                value={option.value}
                checked={settings.fontFamily === option.value}
                onChange={() => setFontFamily(option.value)}
              />
            </label>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm fw-semibold text-g-800">{t('fontSize')}</h3>
        <div className="d-flex align-items-center gap-3">
          <input
            type="range"
            min={12}
            max={18}
            step={1}
            value={settings.fontSize}
            onChange={(event) => setFontSize(Number(event.target.value))}
            className="w-100"
          />
          <span className="text-xs text-g-600">{settings.fontSize}px</span>
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm fw-semibold text-g-800">{t('radius')}</h3>
        <div className="d-flex align-items-center gap-3">
          <input
            type="range"
            min={0.25}
            max={1}
            step={0.05}
            value={settings.radius}
            onChange={(event) => setRadius(Number(event.target.value))}
            className="w-100"
          />
          <span className="text-xs text-g-600">{settings.radius.toFixed(2)}rem</span>
        </div>
      </section>

      <button
        type="button"
        onClick={reset}
        className="w-100 rounded-2 border border-border px-4 py-2 text-sm fw-medium text-g-700 hover-bg-hover-color"
      >
        {t('reset')}
      </button>
    </div>
  )
}
