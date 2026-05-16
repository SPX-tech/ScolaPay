'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth';
import {
  BarChart3,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  DollarSign,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  Receipt,
  Settings,
  Users,
  X,
} from 'lucide-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

const sidebarLinks = [
  { href: '/dashboard', label: 'Tableau de bord', icon: LayoutDashboard },
  { href: '/dashboard/students', label: 'Étudiants', icon: BookOpen },
  { href: '/dashboard/ledger', label: 'Encaissement', icon: DollarSign },
  { href: '/dashboard/transactions', label: 'Transactions', icon: Receipt },
  { href: '/dashboard/users', label: 'Utilisateurs', icon: Users },
  { href: '/dashboard/settings', label: 'Paramètres', icon: Settings },
];

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <QueryClientProvider client={queryClient}>
      <div className="min-h-screen flex">
        {/* Mobile overlay */}
        {mobileSidebarOpen && (
          <div
            className="fixed inset-0 bg-black/50 z-40 lg:hidden"
            onClick={() => setMobileSidebarOpen(false)}
          />
        )}

        {/* Sidebar */}
        <aside
          className={cn(
            'fixed lg:static inset-y-0 left-0 z-50 flex flex-col bg-sidebar text-sidebar-foreground transition-all duration-300',
            sidebarOpen ? 'w-64' : 'w-16',
            mobileSidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
          )}
        >
          {/* Logo */}
          <div className={cn(
            'flex items-center h-16 px-4 border-b border-sidebar-muted',
            sidebarOpen ? 'justify-between' : 'justify-center',
          )}>
            {sidebarOpen && (
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
                  <GraduationCap className="w-5 h-5 text-white" />
                </div>
                <span className="font-bold text-sm">Edu-Cash</span>
              </div>
            )}
            <button
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="hidden lg:flex p-1.5 rounded-lg hover:bg-sidebar-muted transition-colors"
            >
              {sidebarOpen ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
            </button>
            <button
              onClick={() => setMobileSidebarOpen(false)}
              className="lg:hidden p-1.5 rounded-lg hover:bg-sidebar-muted transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Navigation */}
          <nav className="flex-1 px-2 py-4 space-y-1 overflow-y-auto">
            {sidebarLinks.map((link) => {
              const isActive = pathname === link.href;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() => setMobileSidebarOpen(false)}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200',
                    isActive
                      ? 'bg-sidebar-accent text-white font-medium'
                      : 'text-sidebar-foreground/70 hover:text-sidebar-foreground hover:bg-sidebar-muted',
                    !sidebarOpen && 'lg:justify-center',
                  )}
                  title={link.label}
                >
                  <link.icon className={cn('w-5 h-5 shrink-0', isActive && 'text-white')} />
                  {sidebarOpen && <span className="text-sm">{link.label}</span>}
                </Link>
              );
            })}
          </nav>

          {/* User info */}
          <div className={cn(
            'p-3 border-t border-sidebar-muted',
            !sidebarOpen && 'lg:text-center',
          )}>
            {sidebarOpen && user && (
              <div className="mb-3 px-3">
                <p className="text-sm font-medium truncate">{user.fullName}</p>
                <p className="text-xs text-sidebar-foreground/50 truncate">{user.email}</p>
              </div>
            )}
            <button
              onClick={handleLogout}
              className={cn(
                'flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sidebar-foreground/70 hover:text-red-400 hover:bg-sidebar-muted transition-all duration-200',
                !sidebarOpen && 'lg:justify-center',
              )}
              title="Déconnexion"
            >
              <LogOut className="w-5 h-5 shrink-0" />
              {sidebarOpen && <span className="text-sm">Déconnexion</span>}
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main className="flex-1 min-w-0">
          {/* Top bar (mobile) */}
          <div className="lg:hidden flex items-center h-14 px-4 border-b bg-background sticky top-0 z-30">
            <button
              onClick={() => setMobileSidebarOpen(true)}
              className="p-2 rounded-lg hover:bg-secondary transition-colors"
            >
              <Menu className="w-5 h-5" />
            </button>
            <div className="ml-3 flex items-center gap-2">
              <div className="w-7 h-7 rounded-md bg-primary flex items-center justify-center">
                <GraduationCap className="w-4 h-4 text-white" />
              </div>
              <span className="font-semibold text-sm">Edu-Cash</span>
            </div>
          </div>

          {/* Page content */}
          <div className="p-4 md:p-6 lg:p-8 animate-in">
            {children}
          </div>
        </main>
      </div>
    </QueryClientProvider>
  );
}
