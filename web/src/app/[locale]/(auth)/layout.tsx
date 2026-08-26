export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-vh-100 bg-auth-gradient">
      <div className="mx-auto d-flex min-vh-100 max-w-5xl align-items-center justify-content-center px-4">
        <div className="w-100 max-w-md rounded-3 border border-border bg-box p-4 shadow-sm">
          {children}
        </div>
      </div>
    </div>
  )
}
