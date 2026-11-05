'use client';

import { startTransition, useState, useEffect } from 'react';
import { transactionsApi } from '@/lib/api';
import { formatCurrency, formatDate, getStatusColor, cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight, Loader2, Search, CreditCard, XCircle, Calendar } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [voidingId, setVoidingId] = useState<string | null>(null);
  const { toast } = useToast();
  const limit = 20;

  const fetchTransactions = async () => {
    setLoading(true);
    try {
      const res = await transactionsApi.list({
        page,
        limit,
        type: typeFilter || undefined,
        status: statusFilter || undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      });
      setTransactions(res.data.data);
      setTotalPages(res.data.meta.totalPages);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { startTransition(() => { void fetchTransactions(); }); }, [page, typeFilter, statusFilter, startDate, endDate]);

  const handleVoid = async (id: string) => {
    const reason = prompt('Raison de l\'annulation :');
    if (!reason || reason.length < 5) return;
    setVoidingId(id);
    try {
      await transactionsApi.void(id, reason);
      toast({ title: 'Transaction annulée', description: 'Le solde de l\'étudiant a été mis à jour.', variant: 'success' });
      fetchTransactions();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Impossible d\'annuler la transaction.', variant: 'error' });
    } finally {
      setVoidingId(null);
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-foreground">Transactions</h1>
        <p className="text-muted-foreground mt-1">Historique des encaissements</p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <select
          value={typeFilter}
          onChange={(e) => { setTypeFilter(e.target.value); setPage(1); }}
          className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
        >
          <option value="">Tous les types</option>
          <option value="CASH_PAYMENT">Paiement cash</option>
          <option value="DEBT_ADJUSTMENT">Ajustement dette</option>
          <option value="REFUND">Remboursement</option>
        </select>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
          className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
        >
          <option value="">Tous les statuts</option>
          <option value="POSTED">Publié</option>
          <option value="VOIDED">Annulé</option>
        </select>
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-muted-foreground" />
          <input
            type="date"
            value={startDate}
            onChange={(e) => { setStartDate(e.target.value); setPage(1); }}
            className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
          <span className="text-muted-foreground text-sm">→</span>
          <input
            type="date"
            value={endDate}
            onChange={(e) => { setEndDate(e.target.value); setPage(1); }}
            className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
          {(startDate || endDate) && (
            <button
              onClick={() => { setStartDate(''); setEndDate(''); setPage(1); }}
              className="px-2 py-1 text-xs rounded-md bg-secondary hover:bg-secondary/80 transition-colors"
            >
              Effacer
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="bg-card border rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-secondary/50">
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Reçu</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Élève</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Agent</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Type</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Montant</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Nouvelle dette</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Statut</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Date</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={9} className="text-center py-12"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" /></td></tr>
              ) : transactions.length === 0 ? (
                <tr><td colSpan={9} className="text-center py-12 text-muted-foreground">Aucune transaction</td></tr>
              ) : transactions.map((tx: any) => (
                <tr key={tx.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                  <td className="px-4 py-3 text-sm font-mono">{tx.receiptNumber}</td>
                  <td className="px-4 py-3 text-sm font-medium">{tx.student?.fullName || 'N/A'}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">{tx.recordedBy?.fullName || 'N/A'}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">
                    {tx.type === 'CASH_PAYMENT' ? 'Paiement cash' : tx.type === 'DEBT_ADJUSTMENT' ? 'Ajustement' : 'Remb.'}
                  </td>
                  <td className="px-4 py-3 text-sm text-right font-semibold text-emerald-600">
                    {formatCurrency(tx.amountCents)}
                  </td>
                  <td className="px-4 py-3 text-sm text-right">{formatCurrency(tx.newDebtCents)}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium', getStatusColor(tx.status))}>
                      {tx.status === 'POSTED' ? 'Publié' : 'Annulé'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-muted-foreground text-right">{formatDate(tx.recordedAt)}</td>
                  <td className="px-4 py-3 text-center">
                    {tx.status === 'POSTED' && (
                      <button
                        onClick={() => handleVoid(tx.id)}
                        disabled={voidingId === tx.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors disabled:opacity-30"
                        title="Annuler cette transaction"
                      >
                        {voidingId === tx.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <XCircle className="w-4 h-4" />}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <p className="text-sm text-muted-foreground">Page {page} sur {totalPages}</p>
            <div className="flex gap-1">
              <button onClick={() => setPage(Math.max(1, page - 1))} disabled={page === 1}
                className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button onClick={() => setPage(Math.min(totalPages, page + 1))} disabled={page === totalPages}
                className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors">
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
