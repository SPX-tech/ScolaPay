import { cn } from '@/lib/utils';

/**
 * Marque ScolaPay : un chapeau de diplômé (savoir) fusionné à un bouclier
 * (confiance / sécurité des fonds). Couleurs fixes pour un rendu stable
 * quel que soit l'arrière-plan.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      role="img"
      aria-label="ScolaPay"
      className={cn('h-9 w-9', className)}
    >
      {/* Bouclier */}
      <path
        d="M16 3 L27 7.2 V14.5 C27 21.8 22.2 26.7 16 29 C9.8 26.7 5 21.8 5 14.5 V7.2 Z"
        fill="#0B1220"
      />
      {/* Bandeau du chapeau */}
      <path
        d="M11.5 15 V17.8 C13 19.3 19 19.3 20.5 17.8 V15 L16 16.9 Z"
        fill="#C8902A"
      />
      {/* Plateau du chapeau (mortier) */}
      <path d="M16 9.4 L24 12.9 L16 16.4 L8 12.9 Z" fill="#E0A82E" />
      {/* Pompon */}
      <path d="M24 12.9 V18.2" stroke="#E0A82E" strokeWidth="1.1" strokeLinecap="round" />
      <circle cx="24" cy="18.9" r="1.1" fill="#E0A82E" />
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('flex items-center gap-2', className)}>
      <LogoMark />
      <span className="font-display text-lg font-bold tracking-tight text-ink">
        Scola<span className="text-primary">Pay</span>
      </span>
    </span>
  );
}
