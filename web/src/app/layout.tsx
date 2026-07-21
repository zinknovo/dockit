import '@/styles/globals.css'
import { Providers } from '@/components/Providers'

export const metadata = {
  title: 'CompLog',
  description: 'CompLog 管理后台'
}

export default function RootLayout({
  children
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
