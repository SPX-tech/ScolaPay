'use client';

import { useEffect, useState } from 'react';
import { dashboardApi } from '@/lib/api';
import Link from 'next/link';
import { formatCurrency, formatDate } from '@/lib/utils';
import {
  Banknote,
  BookOpen,
  CreditCard,
  GraduationCap,
  Loader2,
  TrendingDown,
  TrendingUp,
  Users,
} from 'lucide-react';

type DashboardData = {
  students: { total: number; active: number; withDebt: number; totalDebtCents: string; totalCreditCents: string };
  transactions: { total: number; today: number; thisMonth: number };
  cashCollected: { todayCents: string; thisMonthCents: string };
  recentTransactions: any[];
};

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    dashboardApi.getSummary().then((res) => {
      setData(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!data) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        Impossible de charger les données. Vérifiez que le serveur backend est en marche.
      </div>
    );
  }

  const stats = [
    {
      label: 'Élèves actifs',
      value: data.students.active,
      sub: `${data.students.total} au total`,
      icon: Users,
      color: 'text-blue-600 bg-blue-100',
    },
    {
      label: 'Encaissements du jour',
      value: formatCurrency(data.cashCollected.todayCents),
      sub: `${data.transactions.today} transactions`,
      icon: Banknote,
      color: 'text-emerald-600 bg-emerald-100',
    },
    {
      label: 'Encaissements du mois',
      value: formatCurrency(data.cashCollected.thisMonthCents),
      sub: `${data.transactions.thisMonth} transactions`,
      icon: TrendingUp,
      color: 'text-violet-600 bg-violet-100',
    },
    {
      label: 'Dette totale',
      value: formatCurrency(data.students.totalDebtCents),
      sub: `${data.students.withDebt} élèves concernés`,
      icon: TrendingDown,
      color: 'text-amber-600 bg-amber-100',
    },
  ];

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-foreground">Tableau de bord</h1>
        <p className="text-muted-foreground mt-1">Vue d&apos;ensemble de votre établissement</p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((stat, index) => (
          <div
            key={stat.label}
            className="bg-card border rounded-xl p-5 hover:shadow-md transition-shadow animate-in"
            style={{ animationDelay: `${index * 100}ms` }}
          >
            <div className="flex items-center justify-between mb-3">
              <div className={`p-2.5 rounded-lg ${stat.color}`}>
                <stat.icon className="w-5 h-5" />
              </div>
            </div>
            <p className="text-sm text-muted-foreground">{stat.label}</p>
            <p className="text-2xl font-bold text-foreground mt-0.5">{stat.value}</p>
            <p className="text-xs text-muted-foreground mt-1">{stat.sub}</p>
          </div>
        ))}
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        <div className="bg-card border rounded-xl p-6">
          <h2 className="text-lg font-semibold mb-4">Actions rapides</h2>
          <div className="grid grid-cols-2 gap-3">
            <Link
              href="/dashboard/ledger"
              className="flex flex-col items-center gap-2 p-4 rounded-xl bg-primary/5 hover:bg-primary/10 border border-primary/10 transition-all group"
            >
              <div className="p-3 rounded-full bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Banknote className="w-6 h-6 text-primary" />
              </div>
              <span className="text-sm font-medium">Nouvel encaissement</span>
            </Link>
            <Link
              href="/dashboard/students"
              className="flex flex-col items-center gap-2 p-4 rounded-xl bg-emerald-50 hover:bg-emerald-100 border border-emerald-100 transition-all group"
            >
              <div className="p-3 rounded-full bg-emerald-100 group-hover:bg-emerald-200 transition-colors">
                <BookOpen className="w-6 h-6 text-emerald-600" />
              </div>
              <span className="text-sm font-medium">Ajouter un élève</span>
            </Link>
          </div>
        </div>

        {/* Recent transactions */}
        <div className="bg-card border rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Dernières transactions</h2>
            <Link href="/dashboard/transactions" className="text-sm text-primary hover:underline">
              Voir tout
            </Link>
          </div>
          {data.recentTransactions.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-6">
              Aucune transaction récente
            </p>
          ) : (
            <div className="space-y-3">
              {data.recentTransactions.slice(0, 5).map((tx: any) => (
                <div
                  key={tx.id}
                  className="flex items-center justify-between p-3 rounded-lg bg-secondary/50 hover:bg-secondary transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-full bg-emerald-100">
                      <CreditCard className="w-4 h-4 text-emerald-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium">{tx.student?.fullName || 'N/A'}</p>
                      <p className="text-xs text-muted-foreground">{formatDate(tx.recordedAt)}</p>
                    </div>
                  </div>
                  <span className="text-sm font-semibold text-emerald-600">
                    {formatCurrency(tx.amountCents)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
