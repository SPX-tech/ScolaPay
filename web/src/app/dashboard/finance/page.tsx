'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { financeApi } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cn, formatCurrency, formatDate, getStatusColor } from '@/lib/utils';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { ChevronLeft, ChevronRight, Landmark, Loader2, Lock, Plus, Wallet } from 'lucide-react';

type Balance = {
  balanceCents: string;
  pendingWithdrawalCents: string;
  availableCents: string;
  currency: string;
};

type WithdrawalRequest = {
  id: string;
  amountCents: string;
  method: 'MOBILE_MONEY' | 'BANK_TRANSFER';
  destination: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  reference: string;
  externalReference: string | null;
  failureReason: string | null;
  requestedBy: { fullName: string };
  createdAt: string;
};

type LedgerEntry = {
  id: string;
  amountCents: string;
  balanceAfterCents: string;
  type: 'PAYMENT' | 'WITHDRAWAL' | 'FEE' | 'ADJUSTMENT';
  reference: string;
  createdAt: string;
};

type Paginated<T> = { data: T[]; meta: { total: number; page: number; limit: number; totalPages: number } };

const WITHDRAWAL_METHOD_LABELS: Record<WithdrawalRequest['method'], string> = {
  MOBILE_MONEY: 'Mobile Money',
  BANK_TRANSFER: 'Virement bancaire',
};

const WITHDRAWAL_STATUS_LABELS: Record<WithdrawalRequest['status'], string> = {
  PENDING: 'En attente',
  PROCESSING: 'En cours',
  COMPLETED: 'Terminé',
  FAILED: 'Échoué',
};

const LEDGER_TYPE_LABELS: Record<LedgerEntry['type'], string> = {
  PAYMENT: 'Paiement',
  WITHDRAWAL: 'Retrait',
  FEE: 'Frais',
  ADJUSTMENT: 'Ajustement',
};

const PAGE_SIZE = 10;

function withdrawalFormSchema(availableCents: number) {
  return z.object({
    method: z.enum(['MOBILE_MONEY', 'BANK_TRANSFER'], {
      error: 'Choisissez un mode de retrait',
    }),
    amountFcfa: z
      .number({ error: 'Montant invalide' })
      .positive('Le montant doit être positif')
      .refine((value) => Number.isSafeInteger(Math.round(value * 100)), 'Montant trop élevé')
      .refine((value) => Math.round(value * 100) <= availableCents, {
        message: 'Le montant dépasse le solde disponible',
      }),
    destination: z
      .string()
      .min(8, 'Destination invalide (numéro ou RIB)')
      .max(255, 'Destination trop longue'),
  });
}

type WithdrawalFormValues = z.infer<ReturnType<typeof withdrawalFormSchema>>;

export default function FinancePage() {
  const { user } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const canWithdraw = user?.role === 'TENANT_ADMIN' || user?.role === 'ACCOUNTANT';

  const [dialogOpen, setDialogOpen] = useState(false);
  const [withdrawalsPage, setWithdrawalsPage] = useState(1);
  const [ledgerPage, setLedgerPage] = useState(1);

  const { data: balance, isLoading: balanceLoading } = useQuery({
    queryKey: ['finance', 'balance'],
    queryFn: async () => (await financeApi.getBalance()).data as Balance,
  });

  const { data: withdrawalsData, isLoading: withdrawalsLoading } = useQuery({
    queryKey: ['finance', 'withdrawals', withdrawalsPage],
    queryFn: async () =>
      (await financeApi.listWithdrawals({ page: withdrawalsPage, limit: PAGE_SIZE })).data as Paginated<WithdrawalRequest>,
  });

  const { data: ledgerData, isLoading: ledgerLoading } = useQuery({
    queryKey: ['finance', 'ledger', ledgerPage],
    queryFn: async () =>
      (await financeApi.listLedgerEntries({ page: ledgerPage, limit: PAGE_SIZE })).data as Paginated<LedgerEntry>,
  });

  const availableCents = balance ? Number(balance.availableCents) : 0;

  const form = useForm<WithdrawalFormValues>({
    resolver: zodResolver(withdrawalFormSchema(availableCents)),
    defaultValues: { method: 'MOBILE_MONEY', amountFcfa: 0, destination: '' },
  });

  const createWithdrawal = useMutation({
    mutationFn: (values: WithdrawalFormValues) =>
      financeApi.requestWithdrawal({
        amountCents: Math.round(values.amountFcfa * 100),
        method: values.method,
        destination: values.destination,
      }),
    onSuccess: () => {
      toast({
        title: 'Retrait initié',
        description: 'La demande a été créée et les fonds ont été bloqués.',
        variant: 'success',
      });
      queryClient.invalidateQueries({ queryKey: ['finance'] });
      setWithdrawalsPage(1);
      setDialogOpen(false);
      form.reset();
    },
    onError: (err: any) => {
      toast({
        title: 'Erreur',
        description: err.response?.data?.message || "Impossible d'initier le retrait.",
        variant: 'error',
      });
    },
  });

  const onSubmit = (values: WithdrawalFormValues) => {
    createWithdrawal.mutate(values);
  };

  const stats = balance
    ? [
        {
          label: 'Solde disponible',
          value: formatCurrency(balance.availableCents, balance.currency),
          sub: 'Retirable immédiatement',
          icon: Wallet,
          color: 'text-emerald-600 bg-emerald-100',
        },
        {
          label: 'Solde bloqué / en cours',
          value: formatCurrency(balance.pendingWithdrawalCents, balance.currency),
          sub: 'Retraits en cours de traitement',
          icon: Lock,
          color: 'text-amber-600 bg-amber-100',
        },
      ]
    : [];

  return (
    <div>
      <div className="mb-8 flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Trésorerie</h1>
          <p className="text-muted-foreground mt-1">
            Solde numérique de l&apos;école et historique des mouvements
          </p>
        </div>
        {canWithdraw && (
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <Button onClick={() => setDialogOpen(true)}>
              <Plus className="w-4 h-4" />
              Nouveau retrait
            </Button>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Initier un retrait</DialogTitle>
                <DialogDescription>
                  Les fonds sont bloqués immédiatement ; le virement est traité séparément.
                </DialogDescription>
              </DialogHeader>
              <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                  <FormField
                    control={form.control}
                    name="method"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Mode de retrait</FormLabel>
                        <Select onValueChange={field.onChange} defaultValue={field.value}>
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder="Choisir un mode" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="MOBILE_MONEY">
                              Mobile Money (Orange/Airtel/Moov)
                            </SelectItem>
                            <SelectItem value="BANK_TRANSFER">Virement bancaire</SelectItem>
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="amountFcfa"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Montant à retirer (FCFA)</FormLabel>
                        <FormControl>
                          <Input type="number" min={1} step={1} placeholder="50000" {...field} />
                        </FormControl>
                        {balance && (
                          <p className="text-xs text-muted-foreground">
                            Disponible : {formatCurrency(balance.availableCents, balance.currency)}
                          </p>
                        )}
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="destination"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Destination</FormLabel>
                        <FormControl>
                          <Input
                            placeholder={
                              form.watch('method') === 'BANK_TRANSFER'
                                ? 'RIB'
                                : 'Numéro mobile money'
                            }
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <DialogFooter>
                    <Button
                      type="submit"
                      disabled={createWithdrawal.isPending || balanceLoading}
                    >
                      {createWithdrawal.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Confirmer le retrait
                    </Button>
                  </DialogFooter>
                </form>
              </Form>
            </DialogContent>
          </Dialog>
        )}
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        {balanceLoading ? (
          <div className="col-span-2 flex items-center justify-center h-24">
            <Loader2 className="w-6 h-6 animate-spin text-primary" />
          </div>
        ) : !balance ? (
          <div className="col-span-2 text-center py-6 text-muted-foreground">
            Impossible de charger le solde.
          </div>
        ) : (
          stats.map((stat) => (
            <div key={stat.label} className="bg-card border rounded-xl p-5">
              <div className="flex items-center justify-between mb-3">
                <div className={cn('p-2.5 rounded-lg', stat.color)}>
                  <stat.icon className="w-5 h-5" />
                </div>
              </div>
              <p className="text-sm text-muted-foreground">{stat.label}</p>
              <p className="text-2xl font-bold text-foreground mt-0.5">{stat.value}</p>
              <p className="text-xs text-muted-foreground mt-1">{stat.sub}</p>
            </div>
          ))
        )}
      </div>

      {/* Withdrawal requests history */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold mb-3">Demandes de retrait</h2>
        <div className="bg-card border rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b bg-secondary/50">
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Référence</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Mode</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Destination</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Montant</th>
                  <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Statut</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Demandé par</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Date</th>
                </tr>
              </thead>
              <tbody>
                {withdrawalsLoading ? (
                  <tr>
                    <td colSpan={7} className="text-center py-12">
                      <Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" />
                    </td>
                  </tr>
                ) : !withdrawalsData || withdrawalsData.data.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center py-12 text-muted-foreground">
                      Aucune demande de retrait
                    </td>
                  </tr>
                ) : (
                  withdrawalsData.data.map((w) => (
                    <tr key={w.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                      <td className="px-4 py-3 text-sm font-mono">{w.reference}</td>
                      <td className="px-4 py-3 text-sm text-muted-foreground">
                        {WITHDRAWAL_METHOD_LABELS[w.method]}
                      </td>
                      <td className="px-4 py-3 text-sm text-muted-foreground">{w.destination}</td>
                      <td className="px-4 py-3 text-sm text-right font-semibold">
                        {formatCurrency(w.amountCents)}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <Badge className={getStatusColor(w.status)} variant="outline">
                          {WITHDRAWAL_STATUS_LABELS[w.status]}
                        </Badge>
                        {w.status === 'FAILED' && w.failureReason && (
                          <p className="text-xs text-muted-foreground mt-1">{w.failureReason}</p>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-muted-foreground">
                        {w.requestedBy?.fullName || 'N/A'}
                      </td>
                      <td className="px-4 py-3 text-sm text-muted-foreground text-right">
                        {formatDate(w.createdAt)}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {withdrawalsData && withdrawalsData.meta.totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t">
              <p className="text-sm text-muted-foreground">
                Page {withdrawalsData.meta.page} sur {withdrawalsData.meta.totalPages}
              </p>
              <div className="flex gap-1">
                <button
                  onClick={() => setWithdrawalsPage((p) => Math.max(1, p - 1))}
                  disabled={withdrawalsPage === 1}
                  className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() =>
                    setWithdrawalsPage((p) => Math.min(withdrawalsData.meta.totalPages, p + 1))
                  }
                  disabled={withdrawalsPage === withdrawalsData.meta.totalPages}
                  className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Ledger history */}
      <div>
        <h2 className="text-lg font-semibold mb-3">Grand livre (Ledger)</h2>
        <div className="bg-card border rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b bg-secondary/50">
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Référence</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Type</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Montant</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Solde après</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Date</th>
                </tr>
              </thead>
              <tbody>
                {ledgerLoading ? (
                  <tr>
                    <td colSpan={5} className="text-center py-12">
                      <Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" />
                    </td>
                  </tr>
                ) : !ledgerData || ledgerData.data.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center py-12 text-muted-foreground">
                      Aucune écriture au grand livre
                    </td>
                  </tr>
                ) : (
                  ledgerData.data.map((entry) => {
                    const isCredit = Number(entry.amountCents) >= 0;
                    return (
                      <tr key={entry.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                        <td className="px-4 py-3 text-sm font-mono">{entry.reference}</td>
                        <td className="px-4 py-3 text-sm text-muted-foreground">
                          <span className="inline-flex items-center gap-1.5">
                            {entry.type === 'WITHDRAWAL' ? (
                              <Landmark className="w-3.5 h-3.5" />
                            ) : (
                              <Wallet className="w-3.5 h-3.5" />
                            )}
                            {LEDGER_TYPE_LABELS[entry.type]}
                          </span>
                        </td>
                        <td
                          className={cn(
                            'px-4 py-3 text-sm text-right font-semibold',
                            isCredit ? 'text-emerald-600' : 'text-red-600',
                          )}
                        >
                          {isCredit ? '+' : ''}
                          {formatCurrency(entry.amountCents)}
                        </td>
                        <td className="px-4 py-3 text-sm text-right">
                          {formatCurrency(entry.balanceAfterCents)}
                        </td>
                        <td className="px-4 py-3 text-sm text-muted-foreground text-right">
                          {formatDate(entry.createdAt)}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {ledgerData && ledgerData.meta.totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t">
              <p className="text-sm text-muted-foreground">
                Page {ledgerData.meta.page} sur {ledgerData.meta.totalPages}
              </p>
              <div className="flex gap-1">
                <button
                  onClick={() => setLedgerPage((p) => Math.max(1, p - 1))}
                  disabled={ledgerPage === 1}
                  className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setLedgerPage((p) => Math.min(ledgerData.meta.totalPages, p + 1))}
                  disabled={ledgerPage === ledgerData.meta.totalPages}
                  className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
