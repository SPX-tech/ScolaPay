'use client';

import { useState, useCallback, createContext, useContext, type ReactNode } from 'react';

type ToastVariant = 'default' | 'success' | 'error' | 'warning';

type Toast = {
  id: string;
  title: string;
  description?: string;
  variant?: ToastVariant;
};

type ToastContextType = {
  toasts: Toast[];
  toast: (t: Omit<Toast, 'id'>) => void;
  dismiss: (id: string) => void;
};

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const toast = useCallback((t: Omit<Toast, 'id'>) => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { ...t, id }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, 5000);
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, toast, dismiss }}>
      {children}
      {/* Toast container */}
      <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`animate-in rounded-xl p-4 shadow-lg border backdrop-blur-sm transition-all cursor-pointer ${
              t.variant === 'error'
                ? 'bg-destructive text-destructive-foreground border-destructive/30'
                : t.variant === 'success'
                ? 'bg-emerald-600 text-white border-emerald-500'
                : t.variant === 'warning'
                ? 'bg-amber-500 text-white border-amber-400'
                : 'bg-card text-card-foreground border'
            }`}
            onClick={() => dismiss(t.id)}
          >
            <p className="font-semibold text-sm">{t.title}</p>
            {t.description && <p className="text-xs mt-1 opacity-90">{t.description}</p>}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    return {
      toasts: [],
      toast: (t: Omit<Toast, 'id'>) => console.log('[Toast]', t),
      dismiss: (id: string) => {},
    };
  }
  return context;
}
