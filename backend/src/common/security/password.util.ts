import { createHash, scryptSync, timingSafeEqual } from 'node:crypto';
import * as bcrypt from 'bcryptjs';

/**
 * Facteur de coût bcrypt. 12 ≈ 250 ms/hash sur un CPU serveur courant :
 * assez lent pour rendre un brute-force hors-ligne coûteux, assez rapide
 * pour ne pas dégrader une connexion légitime.
 */
const BCRYPT_COST = 12;

/** Un hash bcrypt commence toujours par $2a$, $2b$ ou $2y$. */
const BCRYPT_PREFIX = /^\$2[aby]\$/;

/** Ancien schéma scrypt (`scrypt$salt$key`), migré de façon transparente. */
const SCRYPT_PREFIX = 'scrypt$';
const SCRYPT_KEY_LENGTH = 64;

/**
 * Hache un mot de passe en bcrypt (schéma courant).
 *
 * `bcryptjs` est une implémentation 100 % JavaScript (aucune dépendance
 * native) : le build Docker Alpine reste stable sans python/make/g++. La
 * variante asynchrone cède la main à l'event loop entre les rounds, évitant
 * de bloquer le serveur ~250 ms à chaque hachage — ce qui compte précisément
 * lors d'un pic de connexions.
 */
export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, BCRYPT_COST);
}

/**
 * Vérifie un mot de passe contre le hash stocké, quel qu'en soit le schéma :
 * bcrypt (courant), scrypt ou SHA-256 (hérités). Permet une migration sans
 * rupture : les anciens hashs restent vérifiables jusqu'à leur re-hachage
 * en bcrypt au prochain login réussi (voir `needsRehash`).
 */
export async function verifyPassword(
  password: string,
  storedHash: string,
): Promise<boolean> {
  if (BCRYPT_PREFIX.test(storedHash)) {
    return bcrypt.compare(password, storedHash);
  }

  if (storedHash.startsWith(SCRYPT_PREFIX)) {
    return verifyScryptPassword(password, storedHash);
  }

  return safeEqual(legacySha256(password), storedHash);
}

/**
 * True si le hash stocké n'est pas déjà en bcrypt : un login réussi doit
 * alors le re-hacher en bcrypt (upgrade transparent, sans reset côté
 * utilisateur).
 */
export function needsRehash(storedHash: string): boolean {
  return !BCRYPT_PREFIX.test(storedHash);
}

function verifyScryptPassword(password: string, storedHash: string): boolean {
  const [, salt, key] = storedHash.split('$');
  if (!salt || !key) {
    return false;
  }

  const candidate = scryptSync(password, salt, SCRYPT_KEY_LENGTH).toString('hex');
  return safeEqual(candidate, key);
}

function legacySha256(password: string): string {
  return createHash('sha256').update(password).digest('hex');
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);

  if (leftBuffer.length !== rightBuffer.length) {
    return false;
  }

  return timingSafeEqual(leftBuffer, rightBuffer);
}
