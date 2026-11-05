'use client';

import { Suspense, useState, useEffect, useRef } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { ledgerApi, studentsApi } from '@/lib/api';
import { formatCurrency, formatDate, cn } from '@/lib/utils';
import { parseAmountCents } from '@/lib/money';
import { Loader2, Search, CheckCircle, Printer, Banknote, Receipt as ReceiptIcon } from 'lucide-react';

function LedgerContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [step, setStep] = useState<'select' | 'confirm' | 'done'>('select');
  const [students, setStudents] = useState<any[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedStudent, setSelectedStudent] = useState<any>(null);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [receiptData, setReceiptData] = useState<any>(null);
  const [error, setError] = useState('');
  const paymentIntent = useRef<{ payload: string; key: string } | null>(null);

  useEffect(() => {
    const preSelectedId = searchParams.get('studentId');

    studentsApi.list({ limit: 200 }).then((res) => {
      const allStudents = res.data.data || [];

      // Also check if studentId is passed as a search param
      setStudents(allStudents);

      if (preSelectedId) {
        const found = allStudents.find((s: any) => s.id === preSelectedId);
        if (found) {
          setSelectedStudent(found);
          setStep('confirm');
        }
      }
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [searchParams]);

  const filteredStudents = students.filter((s: any) =>
    s.status === 'ACTIVE' &&
    (s.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
     s.studentNumber.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  const handleSelectStudent = (student: any) => {
    setSelectedStudent(student);
    setStep('confirm');
    setError('');
  };

  const handleSubmit = async () => {
    const amountCents = parseAmountCents(amount);
    if (!amountCents) {
      setError('Montant invalide');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      const payload = JSON.stringify({ studentId: selectedStudent.id, amountCents, note });
      if (paymentIntent.current?.payload !== payload) {
        paymentIntent.current = { payload, key: crypto.randomUUID() };
      }
      const res = await ledgerApi.recordCashPayment({
        studentId: selectedStudent.id,
        amountCents,
        note: note || undefined,
        idempotencyKey: paymentIntent.current.key,
      });
      setResult(res.data);

      // Fetch full receipt with QR code
      try {
        const receiptRes = await ledgerApi.getReceipt(res.data.transactionId);
        setReceiptData(receiptRes.data);
      } catch {
        // Receipt fetch is optional
      }

      setStep('done');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Erreur lors de l\'encaissement');
    } finally {
      setSubmitting(false);
    }
  };

  const resetForm = () => {
    setStep('select');
    setSelectedStudent(null);
    setAmount('');
    setNote('');
    setResult(null);
    setReceiptData(null);
    setError('');
    router.push('/dashboard/ledger');
  };

  const handlePrint = () => {
    window.print();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <>
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-foreground">Encaissement</h1>
          <p className="text-muted-foreground mt-1">Enregistrer un paiement en cash</p>
        </div>

        <div className="max-w-2xl">
          {/* Step indicators */}
          <div className="flex items-center gap-2 mb-6">
            {['Élève', 'Montant', 'Reçu'].map((label, i) => {
              const steps = ['select', 'confirm', 'done'];
              const currentIdx = steps.indexOf(step);
              const isActive = i <= currentIdx;
              const StepIcon = [Search, Banknote, ReceiptIcon][i];
              return (
                <div key={label} className="flex items-center gap-2">
                  <div className={cn(
                    'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors',
                    isActive ? 'bg-primary text-white' : 'bg-secondary text-muted-foreground',
                  )}>
                    {isActive && i < currentIdx ? <CheckCircle className="w-4 h-4" /> : <StepIcon className="w-4 h-4" aria-hidden="true" />}
                  </div>
                  <span className={cn('text-sm', isActive ? 'text-foreground font-medium' : 'text-muted-foreground')}>
                    {label}
                  </span>
                  {i < 2 && <div className={cn('w-3 sm:w-8 h-0.5', i < currentIdx ? 'bg-primary' : 'bg-secondary')} />}
                </div>
              );
            })}
          </div>

          {step === 'select' && (
            <div className="bg-card border rounded-xl p-6">
              <div className="relative mb-4">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Rechercher un élève par nom ou matricule..."
                  className="w-full pl-10 pr-4 py-3 rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  autoFocus
                />
              </div>

              <div className="space-y-2 max-h-96 overflow-y-auto">
                {filteredStudents.length === 0 ? (
                  <p className="text-center text-muted-foreground py-8">
                    {searchQuery ? 'Aucun élève trouvé' : 'Aucun élève actif'}
                  </p>
                ) : filteredStudents.map((student: any) => (
                  <button
                    key={student.id}
                    onClick={() => handleSelectStudent(student)}
                    className="w-full flex items-center justify-between p-4 rounded-lg bg-secondary/30 hover:bg-secondary border border-transparent hover:border-border transition-all text-left"
                  >
                    <div>
                      <p className="font-medium">{student.fullName}</p>
                      <p className="text-sm text-muted-foreground">{student.studentNumber}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm text-muted-foreground">Dette</p>
                      <p className={cn(
                        'font-semibold',
                        Number(student.debtCents) > 0 ? 'text-amber-600' : 'text-emerald-600',
                      )}>
                        {formatCurrency(student.debtCents)}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}

          {step === 'confirm' && selectedStudent && (
            <div className="space-y-4">
              <div className="bg-card border rounded-xl p-6">
                <h2 className="font-semibold mb-4">Élève sélectionné</h2>
                <div className="flex items-center justify-between p-3 rounded-lg bg-secondary/50">
                  <div>
                    <p className="font-medium">{selectedStudent.fullName}</p>
                    <p className="text-sm text-muted-foreground">{selectedStudent.studentNumber}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-muted-foreground">Dette actuelle</p>
                    <p className="font-semibold text-amber-600">{formatCurrency(selectedStudent.debtCents)}</p>
                  </div>
                </div>
              </div>

              <div className="bg-card border rounded-xl p-6">
                <label className="block text-sm font-medium mb-2">
                  Montant encaissé (en FCFA)
                </label>
                <div className="relative">
                  <Banknote className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                  <input
                    type="number"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0"
                    min="0"
                    step="100"
                    className="w-full pl-10 pr-16 py-3 text-xl font-bold rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                    autoFocus
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-muted-foreground font-medium">
                    XAF
                  </span>
                </div>

                <div className="mt-4">
                  <label className="block text-sm font-medium mb-2">
                    Note (optionnelle)
                  </label>
                  <input
                    type="text"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Ex: Paiement du 1er trimestre"
                    className="w-full px-4 py-2.5 rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>

                {error && (
                  <p className="mt-3 text-sm text-destructive">{error}</p>
                )}

                <div className="flex gap-3 mt-6">
                  <button
                    onClick={() => setStep('select')}
                    className="px-4 py-2.5 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors"
                  >
                    Retour
                  </button>
                  <button
                    onClick={handleSubmit}
                    disabled={submitting || !amount || Number(amount) <= 0}
                    className={cn(
                      'flex-1 py-2.5 rounded-lg font-semibold text-white transition-all',
                      'bg-primary hover:bg-primary/90',
                      'disabled:opacity-50 disabled:cursor-not-allowed',
                      'flex items-center justify-center gap-2',
                    )}
                  >
                    {submitting ? (
                      <><Loader2 className="w-5 h-5 animate-spin" /> Traitement...</>
                    ) : (
                      'Valider l\'encaissement'
                    )}
                  </button>
                </div>
              </div>
            </div>
          )}

          {step === 'done' && result && (
            <div className="space-y-4">
              <div className="bg-card border rounded-xl p-8 text-center">
                <div className="w-16 h-16 rounded-full bg-emerald-100 flex items-center justify-center mx-auto mb-4">
                  <CheckCircle className="w-8 h-8 text-emerald-600" />
                </div>
                <h2 className="text-xl font-bold mb-2">Encaissement réussi !</h2>
                <p className="text-muted-foreground mb-6">
                  Le paiement de {formatCurrency(result.amountCents)} a été enregistré.
                </p>

                <div className="bg-secondary/50 rounded-lg p-4 mb-6 text-left space-y-2">
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Reçu n°</span>
                    <span className="text-sm font-mono font-medium">{result.receiptNumber}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Élève</span>
                    <span className="text-sm font-medium">{selectedStudent?.fullName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Montant</span>
                    <span className="text-sm font-semibold text-emerald-600">{formatCurrency(result.amountCents)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Nouvelle dette</span>
                    <span className="text-sm font-semibold">{formatCurrency(result.newDebtCents)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Hash</span>
                    <span className="text-xs font-mono text-muted-foreground truncate max-w-[200px]">
                      {result.receiptHash}
                    </span>
                  </div>
                </div>

                {/* QR Code */}
                {receiptData?.qrCode && (
                  <div className="mb-6">
                    <div className="bg-white rounded-lg p-4 inline-block border">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={receiptData.qrCode} alt="QR Code du reçu" className="w-48 h-48 mx-auto" />
                    </div>
                    <p className="text-xs text-muted-foreground mt-2">Scannez pour vérifier l&apos;authenticité du reçu</p>
                  </div>
                )}

                <div className="flex gap-3">
                  <button
                    onClick={handlePrint}
                    className="flex-1 py-2.5 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors font-semibold flex items-center justify-center gap-2"
                  >
                    <Printer className="w-4 h-4" />
                    Imprimer le reçu
                  </button>
                  <button
                    onClick={resetForm}
                    className="flex-1 py-2.5 rounded-lg bg-primary text-white font-semibold hover:bg-primary/90 transition-colors"
                  >
                    Nouvel encaissement
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Printable receipt (hidden on screen, visible on print) */}
      {result && (
        <div className="hidden print:block print:fixed print:inset-0 print:bg-white print:z-50">
          <div className="flex flex-col items-center justify-center min-h-screen p-8">
            <ReceiptIcon className="w-12 h-12 mb-4 text-blue-600" />
            <h1 className="text-2xl font-bold mb-1">ScolaPay Tchad</h1>
            <p className="text-sm text-gray-500 mb-6">Reçu de paiement</p>

            <div className="w-full max-w-sm border-t border-b border-dashed py-4 mb-4 space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Reçu n°</span>
                <span className="font-mono font-bold">{result.receiptNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Élève</span>
                <span>{selectedStudent?.fullName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Matricule</span>
                <span>{selectedStudent?.studentNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Montant</span>
                <span className="font-bold text-lg text-green-600">{formatCurrency(result.amountCents)}</span>
              </div>
              {note && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Note</span>
                  <span>{note}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-gray-500">Date</span>
                <span>{formatDate(result.recordedAt)}</span>
              </div>
            </div>

            {receiptData?.qrCode && (
              <div className="mb-4">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={receiptData.qrCode} alt="QR Code" className="w-32 h-32 mx-auto" />
              </div>
            )}

            <p className="text-xs text-gray-400 mt-6">
              Hash: {result.receiptHash?.substring(0, 16)}...
            </p>
            <p className="text-xs text-gray-400 mt-2">
              Document authentique généré par ScolaPay
            </p>
          </div>
        </div>
      )}
    </>
  );
}

export default function LedgerPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    }>
      <LedgerContent />
    </Suspense>
  );
}
