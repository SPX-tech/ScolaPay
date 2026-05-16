import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Edu-Cash Tchad | Gestion Financière Scolaire',
  description: 'SaaS de gestion financière pour écoles et universités au Tchad',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" suppressHydrationWarning>
      <body className="min-h-screen">
        {children}
      </body>
    </html>
  );
}
