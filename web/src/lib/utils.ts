import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { formatMoney } from './money';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatCurrency(cents: bigint | number | string, currency = 'XAF'): string {
  return formatMoney(cents, currency);
}

export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function getStatusColor(status: string): string {
  const colors: Record<string, string> = {
    ACTIVE: 'bg-emerald-100 text-emerald-800',
    SUSPENDED: 'bg-amber-100 text-amber-800',
    GRADUATED: 'bg-blue-100 text-blue-800',
    POSTED: 'bg-emerald-100 text-emerald-800',
    VOIDED: 'bg-red-100 text-red-800',
    PENDING: 'bg-amber-100 text-amber-800',
    PROCESSING: 'bg-blue-100 text-blue-800',
    COMPLETED: 'bg-emerald-100 text-emerald-800',
    SENT: 'bg-emerald-100 text-emerald-800',
    FAILED: 'bg-red-100 text-red-800',
    TENANT_ADMIN: 'bg-purple-100 text-purple-800',
    CASHIER: 'bg-blue-100 text-blue-800',
    ACCOUNTANT: 'bg-amber-100 text-amber-800',
    AUDITOR: 'bg-slate-100 text-slate-800',
  };
  return colors[status] || 'bg-gray-100 text-gray-800';
}
