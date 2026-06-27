// Coordonnées de contact public de ScolaPay.
// ⚠️ À remplacer par les vraies valeurs avant mise en production.
export const WHATSAPP_NUMBER = '23599000000'; // format international, sans "+"
export const CONTACT_EMAIL = 'contact@scolapay.td';

/** Construit une URL wa.me, avec message pré-rempli optionnel. */
export function whatsappUrl(message?: string): string {
  const base = `https://wa.me/${WHATSAPP_NUMBER}`;
  return message ? `${base}?text=${encodeURIComponent(message)}` : base;
}
