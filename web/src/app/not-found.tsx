import Link from 'next/link';
import { FileSearch, ArrowLeft } from 'lucide-react';
import { Logo } from '@/components/landing/logo';

export default function NotFound() {
  return (
    <main className="min-h-screen bg-paper flex items-center justify-center px-6">
      <div className="max-w-md text-center">
        <Link href="/" className="inline-flex mb-10"><Logo /></Link>
        <FileSearch className="mx-auto h-12 w-12 text-slatey" aria-hidden="true" />
        <p className="mt-6 font-mono text-sm text-slatey">Erreur 404</p>
        <h1 className="mt-3 font-display text-3xl font-semibold text-ink">Cette page est introuvable</h1>
        <p className="mt-4 text-slatey">Le lien a peut-être changé. Retrouvez votre chemin depuis l’accueil.</p>
        <Link href="/" className="mt-8 inline-flex items-center gap-2 rounded-lg bg-ink px-5 py-3 text-white">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Retour à l’accueil
        </Link>
      </div>
    </main>
  );
}
