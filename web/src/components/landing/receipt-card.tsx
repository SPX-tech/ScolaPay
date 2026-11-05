import { BadgeCheck, Check, QrCode } from 'lucide-react';

/**
 * Élément signature de la vitrine : le reçu de paiement vérifié.
 * Met en scène l'artefact central de ScolaPay — la preuve infalsifiable
 * qui transforme un encaissement cash en transaction tracée.
 */
export function ReceiptCard() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      {/* halo doré derrière le reçu */}
      <div className="absolute -inset-6 -z-10 rounded-[2rem] bg-gold/20 blur-2xl" />

      <div className="overflow-hidden rounded-2xl bg-white shadow-2xl shadow-ink/20 ring-1 ring-ink/5">
        {/* liseré perforé supérieur */}
        <div className="receipt-perf h-4 bg-ink" />

        <div className="space-y-5 p-6">
          <p className="font-mono text-[11px] uppercase tracking-wide text-slatey">
            Démonstration, sans valeur de paiement
          </p>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-mono text-[11px] uppercase tracking-[0.2em] text-slatey">
                Reçu de paiement
              </p>
              <p className="font-display text-sm font-semibold text-ink">
                Lycée de Démonstration
              </p>
            </div>
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-ink text-gold">
              <BadgeCheck className="h-5 w-5" />
            </span>
          </div>

          <div className="space-y-1 border-y border-dashed border-ink/15 py-4">
            <div className="flex justify-between font-mono text-xs text-slatey">
              <span>Élève</span>
              <span className="text-ink">Marie Diallo · 6ème A</span>
            </div>
            <div className="flex items-end justify-between pt-2">
              <span className="font-mono text-xs text-slatey">Montant</span>
              <span className="font-mono text-3xl font-semibold tracking-tight text-ink">
                25 000 <span className="text-base text-slatey">FCFA</span>
              </span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-lg border border-ink/10 bg-paper text-ink/70">
              <QrCode className="h-12 w-12" />
            </div>
            <div className="min-w-0 space-y-1.5 font-mono text-[11px] text-slatey">
              <p className="truncate">
                <span className="text-ink/50">N° </span>RCP-2026-000482
              </p>
              <p className="truncate">
                <span className="text-ink/50">SHA-256 </span>a9f3·c1e8·77b2…
              </p>
              <p className="flex items-center gap-1 text-emerald-600">
                <Check className="h-3.5 w-3.5" /> SMS envoyé au tuteur
              </p>
            </div>
          </div>
        </div>

        <div className="receipt-perf h-4 bg-ink" />
      </div>

      {/* Cachet « VÉRIFIÉ » — le geste du tampon officiel */}
      <div className="animate-stamp pointer-events-none absolute -right-3 bottom-10 select-none sm:-right-6">
        <div className="flex h-24 w-24 flex-col items-center justify-center rounded-full border-4 border-gold/80 bg-paper/40 text-center backdrop-blur-[1px]">
          <BadgeCheck className="h-6 w-6 text-gold" />
          <span className="font-display text-sm font-extrabold uppercase tracking-wider text-gold">
            Vérifié
          </span>
          <span className="font-mono text-[8px] uppercase tracking-widest text-gold/80">
            ScolaPay
          </span>
        </div>
      </div>
    </div>
  );
}
