import type { Metadata } from 'next';
import Link from 'next/link';
import {
  BarChart3,
  Building2,
  Calculator,
  Check,
  Eye,
  MessageCircle,
  MessageSquare,
  Receipt,
  ScrollText,
  ShieldCheck,
  Users,
  Wallet,
  X,
} from 'lucide-react';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { SiteHeader } from '@/components/landing/site-header';
import { DemoButton } from '@/components/landing/demo-button';
import { ReceiptCard } from '@/components/landing/receipt-card';
import { whatsappUrl } from '@/lib/contact';

export const metadata: Metadata = {
  title: 'ScolaPay | Numérisez les paiements scolaires au Tchad',
  description:
    "ScolaPay transforme chaque paiement cash en reçu vérifié : reçu infalsifiable avec QR, SMS au tuteur et solde en temps réel. Conçu pour les écoles et universités du Tchad.",
  openGraph: {
    title: 'ScolaPay | Numérisez les paiements scolaires au Tchad',
    description:
      'Reçu infalsifiable, SMS au tuteur, solde en temps réel. La caisse de votre école, enfin sous contrôle.',
    type: 'website',
    locale: 'fr_FR',
  },
};

const FEATURES = [
  {
    icon: Receipt,
    title: 'Encaissement express',
    body: "Le caissier saisit l'élève et le montant ; le reçu imprimable avec QR est édité en quelques secondes.",
  },
  {
    icon: ShieldCheck,
    title: 'Reçu infalsifiable',
    body: 'Chaque reçu porte une empreinte SHA-256 : impossible à falsifier, à dupliquer ou à rejouer.',
  },
  {
    icon: MessageSquare,
    title: 'SMS au tuteur',
    body: 'Le parent reçoit la confirmation du paiement par SMS, instantanément, sur son téléphone.',
  },
  {
    icon: BarChart3,
    title: 'Tableau de bord temps réel',
    body: 'Encaissements du jour, dettes en cours, indicateurs clés : la direction voit tout, en direct.',
  },
  {
    icon: Wallet,
    title: 'Comptes virtuels & retraits',
    body: "Le solde numérique de l'école et les retraits mobile money / banque.",
    soon: true,
  },
  {
    icon: Users,
    title: 'Multi-rôles & audit',
    body: 'Admin, caissier, comptable, auditeur — chacun son accès, et un journal complet de chaque action.',
  },
];

const PROBLEMS = [
  'Reçus papier perdus ou illisibles',
  'Fraudes difficiles à détecter',
  'Parents jamais informés des paiements',
  'Clôtures de caisse interminables',
  'Aucune visibilité pour la direction',
];

const SOLUTIONS = [
  'Reçu numérique avec QR, archivé automatiquement',
  'Empreinte SHA-256 : preuve infalsifiable',
  'SMS de confirmation au tuteur, à chaque paiement',
  'Solde et journal de caisse en temps réel',
  "Rôles, droits et audit pour toute l'équipe",
];

const STEPS = [
  {
    n: '01',
    title: 'Le caissier encaisse',
    body: "Il sélectionne l'élève et saisit le montant reçu en espèces. Les habitudes de caisse ne changent pas.",
  },
  {
    n: '02',
    title: 'ScolaPay édite le reçu',
    body: 'Un reçu numérique avec QR et empreinte SHA-256 est généré, imprimable et vérifiable.',
  },
  {
    n: '03',
    title: 'Le tuteur est notifié',
    body: "Le parent reçoit un SMS de confirmation et le solde de l'élève est mis à jour aussitôt.",
  },
];

const ROLES = [
  { icon: Building2, title: 'Directeur', body: 'Vision globale des encaissements et des impayés, en temps réel.' },
  { icon: Calculator, title: 'Comptable', body: 'Rapports journaliers et mensuels, exports et réconciliation.' },
  { icon: Receipt, title: 'Caissier', body: 'Encaissement rapide et reçu immédiat, sans paperasse.' },
  { icon: Eye, title: 'Auditeur', body: "Accès en lecture et journal d'audit horodaté de chaque opération." },
];

const PRICING = [
  "0 FCFA de frais d'installation",
  "Tarif adapté au nombre d'élèves",
  'Formation des équipes incluse',
  'Accompagnement local de proximité',
];

const FAQ = [
  {
    q: "L'argent transite-t-il par ScolaPay ?",
    a: "Non. La version actuelle est 100 % cash : votre école garde et gère son argent localement. ScolaPay enregistre, prouve et trace chaque encaissement — il ne détient pas les fonds.",
  },
  {
    q: 'Peut-on importer nos élèves existants ?',
    a: "Oui. L'import en masse permet de charger toute votre liste d'élèves et de classes en une seule opération, pour démarrer sans ressaisie.",
  },
  {
    q: 'Quelles devises sont gérées ?',
    a: 'Le franc CFA (FCFA / XAF) par défaut. Les montants sont stockés en entiers, au franc près, sans aucun arrondi.',
  },
  {
    q: 'Le mobile money est-il disponible ?',
    a: 'Les fondations (comptes virtuels et retraits) sont déjà posées dans la plateforme. Le déploiement se fait progressivement, après la version cash.',
  },
  {
    q: 'Combien de temps pour démarrer ?',
    a: 'Quelques jours suffisent. Nous configurons votre école, importons vos élèves et formons vos équipes avant la mise en service.',
  },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-paper font-sans text-ink antialiased">
      <SiteHeader />

      <main>
        {/* ───────────── Hero ───────────── */}
        <section className="relative overflow-hidden">
          <div className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_70%_0%,rgba(37,99,235,0.10),transparent)]" />
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 md:grid-cols-2 md:py-24">
            <div className="animate-in">
              <Badge variant="gold" className="font-mono text-[11px] uppercase tracking-wider">
                Gestion financière scolaire · Tchad 🇹🇩
              </Badge>
              <h1 className="mt-5 font-display text-4xl font-extrabold leading-[1.05] tracking-tight text-ink sm:text-5xl lg:text-6xl">
                Chaque paiement cash, transformé en{' '}
                <span className="text-primary">reçu vérifié</span>.
              </h1>
              <p className="mt-5 max-w-md text-lg leading-relaxed text-slatey">
                ScolaPay digitalise les encaissements de votre école : reçu infalsifiable, SMS au
                tuteur et solde à jour en temps réel — sans changer les habitudes de votre caisse.
              </p>
              <div className="mt-8 flex flex-wrap items-center gap-3">
                <DemoButton />
                <a
                  href={whatsappUrl('Bonjour ScolaPay, je souhaite des informations.')}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-sm font-medium text-slatey transition-colors hover:text-ink"
                >
                  <MessageCircle className="h-4 w-4 text-emerald-600" /> Contact direct WhatsApp
                </a>
              </div>
            </div>

            <div className="animate-in">
              <ReceiptCard />
            </div>
          </div>
        </section>

        {/* ───────────── Bandeau de confiance ───────────── */}
        <section className="border-y border-ink/10 bg-white">
          <div className="mx-auto grid max-w-6xl grid-cols-1 divide-y divide-ink/10 px-4 sm:grid-cols-3 sm:divide-x sm:divide-y-0 sm:px-6">
            {[
              { k: '100 %', v: 'des encaissements tracés' },
              { k: '0 FCFA', v: "de frais d'installation" },
              { k: 'SMS', v: 'instantané à chaque paiement' },
            ].map((s) => (
              <div key={s.v} className="px-2 py-6 text-center">
                <p className="font-mono text-3xl font-semibold text-ink">{s.k}</p>
                <p className="mt-1 text-sm text-slatey">{s.v}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ───────────── Problème → Solution ───────────── */}
        <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <div className="grid gap-6 md:grid-cols-2">
            <div className="rounded-2xl border border-ink/10 bg-white p-8">
              <p className="font-mono text-xs uppercase tracking-widest text-slatey">
                Aujourd&apos;hui, sans ScolaPay
              </p>
              <ul className="mt-6 space-y-4">
                {PROBLEMS.map((p) => (
                  <li key={p} className="flex items-start gap-3 text-slatey">
                    <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
                      <X className="h-3.5 w-3.5" />
                    </span>
                    {p}
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-2xl border border-primary/20 bg-primary/[0.04] p-8">
              <p className="font-mono text-xs uppercase tracking-widest text-primary">
                Avec ScolaPay
              </p>
              <ul className="mt-6 space-y-4">
                {SOLUTIONS.map((s) => (
                  <li key={s} className="flex items-start gap-3 font-medium text-ink">
                    <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                      <Check className="h-3.5 w-3.5" />
                    </span>
                    {s}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        {/* ───────────── Fonctionnalités ───────────── */}
        <section id="fonctionnalites" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-20 sm:px-6">
          <div className="max-w-2xl">
            <p className="font-mono text-xs uppercase tracking-widest text-primary">
              Fonctionnalités
            </p>
            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight text-ink sm:text-4xl">
              Tout ce qu&apos;il faut pour une caisse maîtrisée
            </h2>
          </div>

          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <div
                key={f.title}
                className="group rounded-2xl border border-ink/10 bg-white p-6 transition-shadow hover:shadow-lg hover:shadow-ink/5"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-ink text-gold transition-colors group-hover:bg-primary group-hover:text-white">
                  <f.icon className="h-5 w-5" />
                </div>
                <div className="mt-5 flex items-center gap-2">
                  <h3 className="font-display text-lg font-semibold text-ink">{f.title}</h3>
                  {f.soon && (
                    <Badge variant="gold" className="text-[10px] uppercase">
                      Bientôt
                    </Badge>
                  )}
                </div>
                <p className="mt-2 text-sm leading-relaxed text-slatey">{f.body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ───────────── Sécurité (fond encre) ───────────── */}
        <section id="securite" className="scroll-mt-20 bg-ink py-20 text-white">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 sm:px-6 md:grid-cols-2">
            <div>
              <p className="font-mono text-xs uppercase tracking-widest text-gold">
                Sécurité &amp; confiance
              </p>
              <h2 className="mt-3 font-display text-3xl font-bold tracking-tight sm:text-4xl">
                La preuve, pas la promesse
              </h2>
              <p className="mt-4 max-w-md leading-relaxed text-white/70">
                ScolaPay est bâti pour une institution financière : chaque franc est traçable et
                chaque reçu, vérifiable.
              </p>
              <div className="mt-8 grid gap-5">
                {[
                  { icon: ShieldCheck, t: 'Isolation par école', d: 'Les données de chaque établissement sont strictement cloisonnées.' },
                  { icon: Calculator, t: 'Montants en entiers', d: 'Stockés au franc près, sans virgule flottante ni arrondi.' },
                  { icon: ScrollText, t: "Journal d'audit", d: 'Chaque action est horodatée et attribuée à son auteur.' },
                ].map((item) => (
                  <div key={item.t} className="flex items-start gap-4">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/10 text-gold">
                      <item.icon className="h-5 w-5" />
                    </span>
                    <div>
                      <p className="font-display font-semibold">{item.t}</p>
                      <p className="text-sm text-white/60">{item.d}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 font-mono text-sm">
              <p className="text-white/40">// empreinte du reçu</p>
              <p className="mt-3 break-all leading-relaxed text-gold">
                SHA-256(tenant + élève + montant + n° reçu + secret)
              </p>
              <p className="mt-4 break-all leading-relaxed text-white/70">
                = a9f3c1e877b2d40e·5fa1c9b3e2d77a04·1c6e8f0b9a2d3e4f·77b21c6e8f0b9a2d
              </p>
              <p className="mt-5 flex items-center gap-2 text-emerald-400">
                <Check className="h-4 w-4" /> reçu authentique
              </p>
            </div>
          </div>
        </section>

        {/* ───────────── Comment ça marche ───────────── */}
        <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <div className="max-w-2xl">
            <p className="font-mono text-xs uppercase tracking-widest text-primary">
              Comment ça marche
            </p>
            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight text-ink sm:text-4xl">
              Trois gestes, du cash au reçu
            </h2>
          </div>

          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {STEPS.map((step) => (
              <div key={step.n} className="relative rounded-2xl border border-ink/10 bg-white p-7">
                <span className="font-mono text-4xl font-bold text-gold/60">{step.n}</span>
                <h3 className="mt-4 font-display text-xl font-semibold text-ink">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-slatey">{step.body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ───────────── Pour qui ───────────── */}
        <section className="border-y border-ink/10 bg-white py-20">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <div className="max-w-2xl">
              <p className="font-mono text-xs uppercase tracking-widest text-primary">Pour qui</p>
              <h2 className="mt-3 font-display text-3xl font-bold tracking-tight text-ink sm:text-4xl">
                Un outil par métier de l&apos;école
              </h2>
            </div>
            <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {ROLES.map((role) => (
                <div key={role.title} className="rounded-2xl border border-ink/10 bg-paper p-6">
                  <role.icon className="h-6 w-6 text-primary" />
                  <h3 className="mt-4 font-display text-lg font-semibold text-ink">{role.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-slatey">{role.body}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ───────────── Tarifs sur mesure ───────────── */}
        <section id="tarifs" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-20 sm:px-6">
          <div className="mx-auto max-w-xl overflow-hidden rounded-3xl border border-ink/10 bg-white shadow-xl shadow-ink/5">
            <div className="bg-ink px-8 py-8 text-center text-white">
              <p className="font-mono text-xs uppercase tracking-widest text-gold">Tarification</p>
              <h2 className="mt-2 font-display text-3xl font-bold tracking-tight">
                Une offre à votre taille
              </h2>
              <p className="mt-2 text-sm text-white/60">
                Pas de prix figé : un tarif construit avec vous, selon votre école.
              </p>
            </div>
            <div className="p-8">
              <ul className="space-y-4">
                {PRICING.map((p) => (
                  <li key={p} className="flex items-center gap-3 font-medium text-ink">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                      <Check className="h-3.5 w-3.5" />
                    </span>
                    {p}
                  </li>
                ))}
              </ul>
              <div className="mt-8 flex flex-col items-center gap-3">
                <DemoButton label="Demander une démo gratuite" className="w-full sm:w-auto" />
                <p className="text-xs text-slatey">Réponse sous 48 h ouvrées.</p>
              </div>
            </div>
          </div>
        </section>

        {/* ───────────── FAQ ───────────── */}
        <section id="faq" className="mx-auto max-w-3xl scroll-mt-20 px-4 py-20 sm:px-6">
          <div className="text-center">
            <p className="font-mono text-xs uppercase tracking-widest text-primary">FAQ</p>
            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight text-ink sm:text-4xl">
              Les questions des directeurs
            </h2>
          </div>
          <Accordion type="single" collapsible className="mt-10">
            {FAQ.map((item) => (
              <AccordionItem key={item.q} value={item.q}>
                <AccordionTrigger>{item.q}</AccordionTrigger>
                <AccordionContent>{item.a}</AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </section>

        {/* ───────────── CTA final ───────────── */}
        <section className="px-4 pb-20 sm:px-6">
          <div className="mx-auto max-w-6xl overflow-hidden rounded-3xl bg-gradient-to-br from-primary to-ink px-6 py-14 text-center text-white sm:px-12">
            <h2 className="mx-auto max-w-2xl font-display text-3xl font-bold tracking-tight sm:text-4xl">
              Prêt à reprendre le contrôle de votre caisse ?
            </h2>
            <p className="mx-auto mt-3 max-w-md text-white/70">
              Voyez ScolaPay en action sur vos propres chiffres, en moins de 30 minutes.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <DemoButton variant="gold" />
              <Button
                asChild
                variant="outline"
                size="lg"
                className="border-white/30 bg-transparent text-white hover:bg-white/10 hover:text-white"
              >
                <a
                  href={whatsappUrl('Bonjour ScolaPay, je souhaite des informations.')}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <MessageCircle /> WhatsApp
                </a>
              </Button>
            </div>
          </div>
        </section>
      </main>

      {/* ───────────── Footer ───────────── */}
      <footer className="border-t border-ink/10 bg-white">
        <div className="mx-auto grid max-w-6xl gap-8 px-4 py-12 sm:px-6 md:grid-cols-3">
          <div>
            <p className="font-display text-lg font-bold text-ink">
              Scola<span className="text-primary">Pay</span>
            </p>
            <p className="mt-2 max-w-xs text-sm text-slatey">
              La numérisation du cash scolaire, conçue pour les écoles et universités du Tchad.
            </p>
          </div>
          <div className="text-sm">
            <p className="font-mono text-xs uppercase tracking-widest text-slatey">Produit</p>
            <ul className="mt-4 space-y-2 text-slatey">
              <li><a href="#fonctionnalites" className="hover:text-ink">Fonctionnalités</a></li>
              <li><a href="#securite" className="hover:text-ink">Sécurité</a></li>
              <li><a href="#tarifs" className="hover:text-ink">Tarifs</a></li>
            </ul>
          </div>
          <div className="text-sm">
            <p className="font-mono text-xs uppercase tracking-widest text-slatey">Contact</p>
            <ul className="mt-4 space-y-2 text-slatey">
              <li>
                <a href={whatsappUrl()} target="_blank" rel="noopener noreferrer" className="hover:text-ink">
                  WhatsApp
                </a>
              </li>
              <li><Link href="/login" className="hover:text-ink">Espace école</Link></li>
            </ul>
          </div>
        </div>
        <div className="border-t border-ink/10">
          <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-4 py-5 text-xs text-slatey sm:flex-row sm:px-6">
            <p>© {new Date().getFullYear()} ScolaPay. Tous droits réservés.</p>
            <p className="font-mono">Conçu au Tchad 🇹🇩</p>
          </div>
        </div>
      </footer>
    </div>
  );
}
