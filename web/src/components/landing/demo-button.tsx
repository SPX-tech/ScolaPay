'use client';

import { useState } from 'react';
import { MessageCircle, Send } from 'lucide-react';
import { Button, type ButtonProps } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { whatsappUrl } from '@/lib/contact';

const SIZE_OPTIONS = [
  'Moins de 200 élèves',
  '200 à 500 élèves',
  '500 à 1000 élèves',
  'Plus de 1000 élèves',
];

type DemoButtonProps = {
  label?: string;
  variant?: ButtonProps['variant'];
  size?: ButtonProps['size'];
  className?: string;
};

export function DemoButton({
  label = 'Demander une démo',
  variant = 'gold',
  size = 'lg',
  className,
}: DemoButtonProps) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    fullName: '',
    school: '',
    city: '',
    phone: '',
    studentCount: SIZE_OPTIONS[0],
  });

  const set = (key: keyof typeof form) => (value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    const message = [
      'Bonjour ScolaPay, je souhaite une démo.',
      '',
      `Nom : ${form.fullName}`,
      `École : ${form.school}`,
      `Ville : ${form.city}`,
      `WhatsApp : ${form.phone}`,
      `Taille : ${form.studentCount}`,
    ].join('\n');
    window.open(whatsappUrl(message), '_blank', 'noopener,noreferrer');
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant={variant} size={size} className={className}>
          {label}
        </Button>
      </DialogTrigger>

      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-xl">Demander une démo</DialogTitle>
          <DialogDescription>
            Laissez vos coordonnées : notre équipe vous rappelle pour une démonstration adaptée à
            votre école.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="demo-fullName">Nom complet</Label>
            <Input
              id="demo-fullName"
              required
              value={form.fullName}
              onChange={(e) => set('fullName')(e.target.value)}
              placeholder="Ex. Mahamat Abakar"
            />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="demo-school">Nom de l&apos;école</Label>
            <Input
              id="demo-school"
              required
              value={form.school}
              onChange={(e) => set('school')(e.target.value)}
              placeholder="Ex. Lycée de la Liberté"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5">
              <Label htmlFor="demo-city">Ville</Label>
              <Input
                id="demo-city"
                required
                value={form.city}
                onChange={(e) => set('city')(e.target.value)}
                placeholder="N'Djamena"
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="demo-phone">Téléphone (WhatsApp)</Label>
              <Input
                id="demo-phone"
                type="tel"
                required
                value={form.phone}
                onChange={(e) => set('phone')(e.target.value)}
                placeholder="+235 •• •• •• ••"
              />
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="demo-size">Nombre approximatif d&apos;élèves</Label>
            <select
              id="demo-size"
              value={form.studentCount}
              onChange={(e) => set('studentCount')(e.target.value)}
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1"
            >
              {SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>

          <Button type="submit" variant="gold" size="lg" className="mt-1 w-full">
            <Send /> Envoyer ma demande
          </Button>

          <a
            href={whatsappUrl('Bonjour ScolaPay, je souhaite des informations.')}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center justify-center gap-2 text-sm font-medium text-slatey transition-colors hover:text-primary"
          >
            <MessageCircle className="h-4 w-4" /> Ou écrivez-nous directement sur WhatsApp
          </a>
        </form>
      </DialogContent>
    </Dialog>
  );
}
