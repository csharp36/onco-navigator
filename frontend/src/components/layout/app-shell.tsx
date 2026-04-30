import { type ReactNode, useState } from 'react';
import { NavSidebar } from './nav-sidebar';
import { Menu, X, LogOut } from 'lucide-react';
import { logout, getUserName } from '@/lib/auth';

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-background">
      {/* Mobile header */}
      <header className="sticky top-0 z-50 flex h-14 items-center border-b bg-background px-4 lg:hidden">
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="mr-4"
          aria-label="Toggle navigation"
        >
          {sidebarOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
        <span className="font-semibold">Onco-Navigator</span>
      </header>

      <div className="flex">
        {/* Sidebar - hidden on mobile unless toggled */}
        <aside className={`
          fixed inset-y-0 left-0 z-40 w-64 transform border-r bg-background transition-transform duration-200
          lg:relative lg:translate-x-0
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}
        `}>
          <div className="flex h-14 items-center border-b px-6">
            <span className="text-lg font-bold">Onco-Navigator</span>
          </div>
          <NavSidebar onNavigate={() => setSidebarOpen(false)} />
          <div className="absolute bottom-0 w-full border-t p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground truncate">
                {getUserName()}
              </span>
              <button
                onClick={logout}
                className="text-muted-foreground hover:text-foreground"
                aria-label="Sign out"
              >
                <LogOut size={18} />
              </button>
            </div>
          </div>
        </aside>

        {/* Mobile overlay */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-black/50 lg:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* Main content */}
        <main className="flex-1 overflow-auto">
          <div className="container mx-auto p-6 max-w-7xl">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
