'use client';

import { startTransition, useState, useEffect, useCallback } from 'react';
import { api } from '@/lib/api';
import { formatCurrency, formatDate } from '@/lib/utils';
import {
  Calendar,
  Download,
  Loader2,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';

type DailyCollection = {
  date: string;
  totalCents: string;
  transactionCount: number;
};

type TopPayer = {
  studentId: string;
  fullName: string;
  studentNumber: string;
  totalCents: string;
  transactionCount: number;
};

type DebtStudent = {
  id: string;
  studentNumber: string;
  fullName: string;
  className: string | null;
  debtCents: string;
  creditCents: string;
  guardianPhone: string | null;
  status: string;
};

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<'collections' | 'debts'>('collections');
  const [startDate, setStartDate] = useState(() => {
    const d = new Date();
    d.setDate(1);
    return d.toISOString().split('T')[0];
  });
  const [endDate, setEndDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [loading, setLoading] = useState(true);
  const [collections, setCollections] = useState<any>(null);
  const [debtStudents, setDebtStudents] = useState<DebtStudent[]>([]);
  const [debtLoading, setDebtLoading] = useState(false);

  const fetchCollections = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get('/dashboard/reports/collections', {
        params: { startDate, endDate },
      });
      setCollections(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate]);

  const fetchDebts = useCallback(async () => {
    setDebtLoading(true);
    try {
      const res = await api.get('/dashboard/reports/students-debt');
      setDebtStudents(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setDebtLoading(false);
    }
  }, []);

  useEffect(() => {
    startTransition(() => {
      if (activeTab === 'collections') void fetchCollections();
      else void fetchDebts();
    });
  }, [activeTab, fetchCollections, fetchDebts]);

  const exportCSV = () => {
    if (!collections) return;

    let csv = 'Date,Montant (FCFA),Transactions\n';
    collections.dailyCollections.forEach((d: DailyCollection) => {
      csv += `${d.date},${Number(d.totalCents) / 100},${d.transactionCount}\n`;
    });

    csv += '\nTop payeurs\nÉlève,Matricule,Montant total (FCFA),Transactions\n';
    collections.topPayers.forEach((p: TopPayer) => {
      csv += `${p.fullName},${p.studentNumber},${Number(p.totalCents) / 100},${p.transactionCount}\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `rapport-encaissements-${startDate}-${endDate}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const exportDebtsCSV = () => {
    let csv = 'Matricule,Nom complet,Classe,Dette (FCFA),Crédit (FCFA),Téléphone,Statut\n';
    debtStudents.forEach((s) => {
      csv += `${s.studentNumber},${s.fullName},${s.className || ''},${Number(s.debtCents) / 100},${Number(s.creditCents) / 100},${s.guardianPhone || ''},${s.status}\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `rapport-dettes-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Rapports</h1>
          <p className="text-muted-foreground mt-1">Analyses et export des données financières</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={activeTab === 'collections' ? exportCSV : exportDebtsCSV}
            disabled={activeTab === 'collections' ? !collections : debtStudents.length === 0}
            className="px-4 py-2.5 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm flex items-center gap-2 disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            Exporter CSV
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-secondary/50 rounded-lg p-1 w-fit">
        <button
          onClick={() => setActiveTab('collections')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'collections'
              ? 'bg-card text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <TrendingUp className="w-4 h-4 inline mr-2" />
          Encaissements
        </button>
        <button
          onClick={() => setActiveTab('debts')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'debts'
              ? 'bg-card text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          <TrendingDown className="w-4 h-4 inline mr-2" />
          Dettes élèves
        </button>
      </div>

      {activeTab === 'collections' && (
        <>
          {/* Date filter */}
          <div className="flex items-center gap-3 mb-4">
            <Calendar className="w-4 h-4 text-muted-foreground" />
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            />
            <span className="text-muted-foreground text-sm">→</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
          ) : collections ? (
            <div className="space-y-6">
              {/* Summary cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-card border rounded-xl p-5">
                  <p className="text-sm text-muted-foreground">Total encaissé</p>
                  <p className="text-2xl font-bold text-emerald-600 mt-1">
                    {formatCurrency(collections.summary.totalCollectedCents)}
                  </p>
                </div>
                <div className="bg-card border rounded-xl p-5">
                  <p className="text-sm text-muted-foreground">Transactions</p>
                  <p className="text-2xl font-bold mt-1">
                    {collections.summary.transactionCount}
                  </p>
                </div>
                <div className="bg-card border rounded-xl p-5">
                  <p className="text-sm text-muted-foreground">Moyenne par transaction</p>
                  <p className="text-2xl font-bold mt-1">
                    {collections.summary.transactionCount > 0
                      ? formatCurrency(
                          BigInt(collections.summary.totalCollectedCents) /
                            BigInt(collections.summary.transactionCount),
                        )
                      : '0 XAF'}
                  </p>
                </div>
              </div>

              {/* Daily collections */}
              <div className="bg-card border rounded-xl overflow-hidden">
                <div className="px-6 py-4 border-b">
                  <h2 className="text-lg font-semibold">Encaissements quotidiens</h2>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b bg-secondary/50">
                        <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Date</th>
                        <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Montant</th>
                        <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Transactions</th>
                        <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Moyenne</th>
                      </tr>
                    </thead>
                    <tbody>
                      {collections.dailyCollections.length === 0 ? (
                        <tr><td colSpan={4} className="text-center py-8 text-muted-foreground">Aucune donnée pour cette période</td></tr>
                      ) : collections.dailyCollections.map((d: DailyCollection) => (
                        <tr key={d.date} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                          <td className="px-4 py-3 text-sm">{formatDate(d.date)}</td>
                          <td className="px-4 py-3 text-sm text-right font-semibold text-emerald-600">
                            {formatCurrency(d.totalCents)}
                          </td>
                          <td className="px-4 py-3 text-sm text-right">{d.transactionCount}</td>
                          <td className="px-4 py-3 text-sm text-right">
                            {d.transactionCount > 0
                              ? formatCurrency(BigInt(d.totalCents) / BigInt(d.transactionCount))
                              : '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Top payers */}
              <div className="bg-card border rounded-xl overflow-hidden">
                <div className="px-6 py-4 border-b">
                  <h2 className="text-lg font-semibold">Top payeurs</h2>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b bg-secondary/50">
                        <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Élève</th>
                        <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Matricule</th>
                        <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Total versé</th>
                        <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Transactions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {collections.topPayers.map((p: TopPayer, i: number) => (
                        <tr key={p.studentId} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                          <td className="px-4 py-3 text-sm font-medium">{p.fullName}</td>
                          <td className="px-4 py-3 text-sm text-muted-foreground font-mono">{p.studentNumber}</td>
                          <td className="px-4 py-3 text-sm text-right font-semibold text-emerald-600">
                            {formatCurrency(p.totalCents)}
                          </td>
                          <td className="px-4 py-3 text-sm text-right">{p.transactionCount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ) : null}
        </>
      )}

      {activeTab === 'debts' && (
        <div className="bg-card border rounded-xl overflow-hidden">
          {debtLoading ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
          ) : (
            <>
              {/* Summary */}
              <div className="px-6 py-4 border-b flex items-center justify-between">
                <h2 className="text-lg font-semibold">État des créances</h2>
                <span className="text-sm text-muted-foreground">
                  {debtStudents.filter((s) => Number(s.debtCents) > 0).length} élèves en dette
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b bg-secondary/50">
                      <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Matricule</th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Nom complet</th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Classe</th>
                      <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Dette</th>
                      <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Crédit</th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Contact</th>
                      <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Statut</th>
                    </tr>
                  </thead>
                  <tbody>
                    {debtStudents.length === 0 ? (
                      <tr><td colSpan={7} className="text-center py-8 text-muted-foreground">Aucun élève</td></tr>
                    ) : debtStudents.map((s) => (
                      <tr key={s.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                        <td className="px-4 py-3 text-sm font-mono">{s.studentNumber}</td>
                        <td className="px-4 py-3 text-sm font-medium">{s.fullName}</td>
                        <td className="px-4 py-3 text-sm text-muted-foreground">{s.className || '-'}</td>
                        <td className={`px-4 py-3 text-sm text-right font-semibold ${Number(s.debtCents) > 0 ? 'text-amber-600' : 'text-emerald-600'}`}>
                          {formatCurrency(s.debtCents)}
                        </td>
                        <td className="px-4 py-3 text-sm text-right">{formatCurrency(s.creditCents)}</td>
                        <td className="px-4 py-3 text-sm text-muted-foreground">{s.guardianPhone || '-'}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium ${
                            s.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
                          }`}>
                            {s.status === 'ACTIVE' ? 'Actif' : 'Suspendu'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
