import '@/styles/globals.css'
import { Providers } from '@/components/Providers'

export const metadata = {
  title: 'Dockit',
  description: 'Dockit 企业级 AI 文档处理协作平台'
}

export default function RootLayout({
  children
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body suppressHydrationWarning>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
