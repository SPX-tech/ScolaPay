import { createHash, randomBytes, scryptSync } from 'node:crypto';
import { hashPassword, needsRehash, verifyPassword } from './password.util';

// bcryptjs est du JS pur : au coût 12, un hash coûte ~1 s et une comparaison
// ~0,95 s (soit ~4x le bcrypt natif). Sous les workers Jest parallèles, le
// timeout par défaut de 5 s est franchi. C'est le prix assumé du facteur de
// travail, pas une lenteur anormale : on donne à ces tests le temps qu'il faut.
jest.setTimeout(60_000);

describe('password.util (bcrypt + rétro-compatibilité)', () => {
  describe('bcrypt (schéma courant)', () => {
    it('hache en bcrypt et vérifie le bon mot de passe', async () => {
      const hash = await hashPassword('S3cret!Passw0rd');

      expect(hash).toMatch(/^\$2[aby]\$/);
      await expect(verifyPassword('S3cret!Passw0rd', hash)).resolves.toBe(true);
    });

    it('rejette un mot de passe erroné', async () => {
      const hash = await hashPassword('S3cret!Passw0rd');
      await expect(verifyPassword('mauvais', hash)).resolves.toBe(false);
    });

    it('produit un hash différent à chaque appel (salt aléatoire)', async () => {
      const a = await hashPassword('same-input');
      const b = await hashPassword('same-input');
      expect(a).not.toEqual(b);
    });

    it('ne nécessite pas de re-hachage', async () => {
      const hash = await hashPassword('abc12345');
      expect(needsRehash(hash)).toBe(false);
    });
  });

  describe('scrypt (hérité)', () => {
    // Reproduit le format `scrypt$salt$key` de l'ancien password.util.
    function legacyScryptHash(password: string): string {
      const salt = randomBytes(16).toString('hex');
      const key = scryptSync(password, salt, 64).toString('hex');
      return `scrypt$${salt}$${key}`;
    }

    it('vérifie un ancien hash scrypt valide', async () => {
      const legacy = legacyScryptHash('legacyScryptPass');
      await expect(verifyPassword('legacyScryptPass', legacy)).resolves.toBe(true);
    });

    it('rejette un mauvais mot de passe sur un hash scrypt', async () => {
      const legacy = legacyScryptHash('legacyScryptPass');
      await expect(verifyPassword('nope', legacy)).resolves.toBe(false);
    });

    it('signale le besoin de re-hachage vers bcrypt', () => {
      const legacy = legacyScryptHash('legacyScryptPass');
      expect(needsRehash(legacy)).toBe(true);
    });
  });

  describe('SHA-256 nu (hérité, comptes seed d’origine)', () => {
    function legacySha256(password: string): string {
      return createHash('sha256').update(password).digest('hex');
    }

    it('vérifie un ancien hash SHA-256 valide', async () => {
      const legacy = legacySha256('oldSeedPass');
      await expect(verifyPassword('oldSeedPass', legacy)).resolves.toBe(true);
    });

    it('rejette un mauvais mot de passe sur un hash SHA-256', async () => {
      const legacy = legacySha256('oldSeedPass');
      await expect(verifyPassword('wrong', legacy)).resolves.toBe(false);
    });

    it('signale le besoin de re-hachage vers bcrypt', () => {
      const legacy = legacySha256('oldSeedPass');
      expect(needsRehash(legacy)).toBe(true);
    });
  });
});
