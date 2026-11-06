import {
  createHash,
  randomBytes,
  scryptSync,
  timingSafeEqual,
} from 'node:crypto';

const SCRYPT_PREFIX = 'scrypt';
const SCRYPT_KEY_LENGTH = 64;

export function hashPassword(password: string): string {
  const salt = randomBytes(16).toString('hex');
  const key = scryptSync(password, salt, SCRYPT_KEY_LENGTH).toString('hex');

  return `${SCRYPT_PREFIX}$${salt}$${key}`;
}

export function verifyPassword(password: string, storedHash: string): boolean {
  if (storedHash.startsWith(`${SCRYPT_PREFIX}$`)) {
    return verifyScryptPassword(password, storedHash);
  }

  return safeEqual(legacySha256(password), storedHash);
}

function verifyScryptPassword(password: string, storedHash: string): boolean {
  const [, salt, key] = storedHash.split('$');
  if (!salt || !key) {
    return false;
  }

  const candidate = scryptSync(password, salt, SCRYPT_KEY_LENGTH).toString(
    'hex',
  );
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
