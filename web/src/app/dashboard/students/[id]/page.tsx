'use client';

import { startTransition, useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { studentsApi, transactionsApi } from '@/lib/api';
import { formatCurrency, formatDate, getStatusColor, cn, transactionStatusLabel, transactionTypeLabel } from '@/lib/utils';
import {
  ArrowLeft,
  BookOpen,
  CreditCard,
  GraduationCap,
  Loader2,
  Phone,
  User,
  Banknote,
  TrendingDown,
  TrendingUp,
  ChevronLeft,
  ChevronRight,
  XCircle,
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

export default function StudentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const studentId = params.id as string;

  const [student, setStudent] = useState<any>(null);
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [txLoading, setTxLoading] = useState(true);
  const [txPage, setTxPage] = useState(1);
  const [txTotalPages, setTxTotalPages] = useState(1);
  const [voidingId, setVoidingId] = useState<string | null>(null);
  const { toast } = useToast();

  const fetchStudent = useCallback(async () => {
    try {
      const res = await studentsApi.getById(studentId);
      setStudent(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [studentId]);

  const fetchTransactions = useCallback(async () => {
    setTxLoading(true);
    try {
      const res = await transactionsApi.listByStudent(studentId, { page: txPage, limit: 10 });
      setTransactions(res.data.data);
      setTxTotalPages(res.data.meta.totalPages);
    } catch (err) {
      console.error(err);
    } finally {
      setTxLoading(false);
    }
  }, [studentId, txPage]);

  useEffect(() => { startTransition(() => { void fetchStudent(); }); }, [fetchStudent]);
  useEffect(() => { startTransition(() => { void fetchTransactions(); }); }, [fetchTransactions]);

  const handleVoid = async (txId: string) => {
    const reason = prompt('Raison de l\'annulation :');
    if (!reason || reason.length < 5) return;
    setVoidingId(txId);
    try {
      await transactionsApi.void(txId, reason);
      fetchTransactions();
      fetchStudent();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur lors de l\'annulation', variant: 'error' });
    } finally {
      setVoidingId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!student) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        Étudiant non trouvé.
      </div>
    );
  }

  const totalPaid = transactions
    .filter((t: any) => t.status === 'POSTED' && t.type === 'CASH_PAYMENT')
    .reduce((sum: number, t: any) => sum + Number(t.amountCents), 0);

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-4 mb-6">
        <button onClick={() => router.push('/dashboard/students')} className="p-2 rounded-lg hover:bg-secondary transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-foreground">{student.fullName}</h1>
          <p className="text-muted-foreground mt-1">Fiche élève</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
        {/* Student info card */}
        <div className="lg:col-span-1">
          <div className="bg-card border rounded-xl p-6 space-y-4">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-full bg-primary/10 flex items-center justify-center">
                <GraduationCap className="w-7 h-7 text-primary" />
              </div>
              <div>
                <h2 className="font-semibold text-lg">{student.fullName}</h2>
                <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium mt-1', getStatusColor(student.status))}>
                  {student.status === 'ACTIVE' ? 'Actif' : student.status === 'SUSPENDED' ? 'Suspendu' : 'Diplômé'}
                </span>
              </div>
            </div>

            <div className="space-y-3 pt-2 border-t">
              <div className="flex items-center gap-3 text-sm">
                <BookOpen className="w-4 h-4 text-muted-foreground" />
                <span className="text-muted-foreground">Matricule :</span>
                <span className="font-mono font-medium">{student.studentNumber}</span>
              </div>
              {student.class && (
                <div className="flex items-center gap-3 text-sm">
                  <User className="w-4 h-4 text-muted-foreground" />
                  <span className="text-muted-foreground">Classe :</span>
                  <span className="font-medium">{student.class.name} ({student.class.academicYear})</span>
                </div>
              )}
              {student.guardianName && (
                <div className="flex items-center gap-3 text-sm">
                  <User className="w-4 h-4 text-muted-foreground" />
                  <span className="text-muted-foreground">Parent :</span>
                  <span className="font-medium">{student.guardianName}</span>
                </div>
              )}
              {student.guardianPhone && (
                <div className="flex items-center gap-3 text-sm">
                  <Phone className="w-4 h-4 text-muted-foreground" />
                  <span className="text-muted-foreground">Contact :</span>
                  <span className="font-medium">{student.guardianPhone}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Financial summary */}
        <div className="lg:col-span-2">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-card border rounded-xl p-5">
              <div className="flex items-center gap-2 mb-2">
                <div className="p-2 rounded-lg bg-amber-100">
                  <TrendingDown className="w-4 h-4 text-amber-600" />
                </div>
              </div>
              <p className="text-sm text-muted-foreground">Dette actuelle</p>
              <p className={cn('text-2xl font-bold mt-1', Number(student.debtCents) > 0 ? 'text-amber-600' : 'text-emerald-600')}>
                {formatCurrency(student.debtCents)}
              </p>
            </div>
            <div className="bg-card border rounded-xl p-5">
              <div className="flex items-center gap-2 mb-2">
                <div className="p-2 rounded-lg bg-emerald-100">
                  <TrendingUp className="w-4 h-4 text-emerald-600" />
                </div>
              </div>
              <p className="text-sm text-muted-foreground">Crédit disponible</p>
              <p className="text-2xl font-bold mt-1">{formatCurrency(student.creditCents)}</p>
            </div>
            <div className="bg-card border rounded-xl p-5">
              <div className="flex items-center gap-2 mb-2">
                <div className="p-2 rounded-lg bg-blue-100">
                  <Banknote className="w-4 h-4 text-blue-600" />
                </div>
              </div>
              <p className="text-sm text-muted-foreground">Total versé</p>
              <p className="text-2xl font-bold mt-1">{formatCurrency(totalPaid)}</p>
            </div>
          </div>

          {/* Quick action */}
          <div className="mt-4">
            <button
              onClick={() => router.push(`/dashboard/ledger?studentId=${student.id}`)}
              className="w-full py-3 rounded-lg bg-primary text-white font-semibold hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
            >
              <CreditCard className="w-5 h-5" />
              Enregistrer un encaissement
            </button>
          </div>
        </div>
      </div>

      {/* Transaction History */}
      <div className="bg-card border rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b">
          <h2 className="text-lg font-semibold">Historique des transactions</h2>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-secondary/50">
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Reçu</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Type</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Montant</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Avant</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Après</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Statut</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Date</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Actions</th>
              </tr>
            </thead>
            <tbody>
              {txLoading ? (
                <tr><td colSpan={8} className="text-center py-12"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" /></td></tr>
              ) : transactions.length === 0 ? (
                <tr><td colSpan={8} className="text-center py-12 text-muted-foreground">Aucune transaction</td></tr>
              ) : transactions.map((tx: any) => (
                <tr key={tx.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                  <td className="px-4 py-3 text-sm font-mono">{tx.receiptNumber}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">
                    {transactionTypeLabel(tx.type)}
                  </td>
                  <td className="px-4 py-3 text-sm text-right font-semibold text-emerald-600">{formatCurrency(tx.amountCents)}</td>
                  <td className="px-4 py-3 text-sm text-right">{formatCurrency(tx.previousDebtCents)}</td>
                  <td className="px-4 py-3 text-sm text-right">{formatCurrency(tx.newDebtCents)}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium', getStatusColor(tx.status))}>
                      {transactionStatusLabel(tx.status)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-muted-foreground text-right">{formatDate(tx.recordedAt)}</td>
                  <td className="px-4 py-3 text-center">
                    {tx.status === 'POSTED' && tx.type === 'CASH_PAYMENT' && (
                      <button onClick={() => handleVoid(tx.id)} disabled={voidingId === tx.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors disabled:opacity-30"
                        title="Annuler cette transaction">
                        {voidingId === tx.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <XCircle className="w-4 h-4" />}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {txTotalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <p className="text-sm text-muted-foreground">Page {txPage} sur {txTotalPages}</p>
            <div className="flex gap-1">
              <button onClick={() => setTxPage(Math.max(1, txPage - 1))} disabled={txPage === 1}
                className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button onClick={() => setTxPage(Math.min(txTotalPages, txPage + 1))} disabled={txPage === txTotalPages}
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
