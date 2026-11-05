'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { DemoButton } from './demo-button';
import { Logo } from './logo';
import { Menu, X } from 'lucide-react';

const NAV = [
  { href: '#fonctionnalites', label: 'Fonctionnalités' },
  { href: '#securite', label: 'Sécurité' },
  { href: '#tarifs', label: 'Tarifs' },
  { href: '#faq', label: 'FAQ' },
];

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={cn(
        'sticky top-0 z-40 w-full transition-colors duration-300',
        scrolled ? 'border-b border-ink/10 bg-paper/90 backdrop-blur' : 'bg-transparent',
      )}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link href="/" aria-label="ScolaPay — accueil">
          <Logo />
        </Link>

        <nav className="hidden items-center gap-8 lg:flex">
          {NAV.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="text-sm font-medium text-slatey transition-colors hover:text-ink"
            >
              {item.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2 sm:gap-3">
          <Button asChild variant="ghost" size="sm" className="text-ink">
            <Link href="/login">Espace école</Link>
          </Button>
          <DemoButton size="sm" className="hidden sm:inline-flex" />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="lg:hidden text-ink"
            aria-label={mobileOpen ? 'Fermer la navigation' : 'Ouvrir la navigation'}
            aria-controls="mobile-navigation"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen((open) => !open)}
            onKeyDown={(event) => { if (event.key === 'Escape') setMobileOpen(false); }}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        </div>
      </div>
      <nav id="mobile-navigation" hidden={!mobileOpen} aria-label="Navigation mobile" className="border-t border-ink/10 bg-paper px-6 py-4 lg:hidden">
        {NAV.map((item) => (
          <a key={item.href} href={item.href} onClick={() => setMobileOpen(false)} className="block rounded-lg px-3 py-3 text-sm font-medium text-ink hover:bg-ink/5">
            {item.label}
          </a>
        ))}
      </nav>
    </header>
  );
}
