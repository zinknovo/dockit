export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(79,70,229,0.12),_transparent_55%)]">
      <div className="mx-auto flex min-h-screen max-w-5xl items-center justify-center px-6">
        <div className="w-full max-w-md rounded-2xl border border-[var(--default-border)] bg-[var(--default-box-color)] p-8 shadow-sm">
          {children}
        </div>
      </div>
    </div>
  )
}
