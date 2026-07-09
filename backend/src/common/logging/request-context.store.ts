import { AsyncLocalStorage } from 'node:async_hooks';

export type RequestStore = {
  requestId: string;
};

/**
 * Contexte propagé automatiquement à travers toute la chaîne asynchrone d'une
 * requête (guards, services, Prisma, filtres). C'est ce qui permet au logger de
 * préfixer *chaque* ligne de log par l'identifiant de corrélation, sans avoir à
 * faire transiter celui-ci en paramètre dans toutes les signatures.
 */
const storage = new AsyncLocalStorage<RequestStore>();

export function runWithRequestId<T>(requestId: string, callback: () => T): T {
  return storage.run({ requestId }, callback);
}

/** `undefined` hors d'une requête HTTP (bootstrap, tâches planifiées, outbox). */
export function getRequestId(): string | undefined {
  return storage.getStore()?.requestId;
}
