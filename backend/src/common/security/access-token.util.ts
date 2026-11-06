import { createHmac, timingSafeEqual } from 'node:crypto';

export type AccessTokenClaims = {
  sub: string;
  tenantId: string;
  role: string;
  iat: number;
  exp: number;
};

type AccessTokenPayloadInput = {
  sub: string;
  tenantId: string;
  role: string;
};

const TOKEN_SEPARATOR = '.';

export function signAccessToken(
  payload: AccessTokenPayloadInput,
  secret: string,
  expiresInSeconds: number,
): string {
  const nowInSeconds = Math.floor(Date.now() / 1000);
  const claims: AccessTokenClaims = {
    ...payload,
    iat: nowInSeconds,
    exp: nowInSeconds + expiresInSeconds,
  };

  const payloadBase64 = Buffer.from(JSON.stringify(claims)).toString(
    'base64url',
  );
  const signature = signPayload(payloadBase64, secret);

  return `${payloadBase64}${TOKEN_SEPARATOR}${signature}`;
}

export function verifyAccessToken(
  token: string,
  secret: string,
): AccessTokenClaims {
  const parts = token.split(TOKEN_SEPARATOR);
  if (parts.length !== 2) {
    throw new Error('Malformed access token.');
  }

  const [payloadBase64, signature] = parts;
  const expectedSignature = signPayload(payloadBase64, secret);

  if (!safeEqual(signature, expectedSignature)) {
    throw new Error('Invalid access token signature.');
  }

  let parsedPayload: unknown;
  try {
    const payloadRaw = Buffer.from(payloadBase64, 'base64url').toString('utf8');
    parsedPayload = JSON.parse(payloadRaw) as unknown;
  } catch {
    throw new Error('Invalid access token payload.');
  }

  if (!isAccessTokenClaims(parsedPayload)) {
    throw new Error('Access token payload shape is invalid.');
  }

  if (parsedPayload.exp <= Math.floor(Date.now() / 1000)) {
    throw new Error('Access token expired.');
  }

  return parsedPayload;
}

function signPayload(payloadBase64: string, secret: string): string {
  return createHmac('sha256', secret).update(payloadBase64).digest('base64url');
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);

  if (leftBuffer.length !== rightBuffer.length) {
    return false;
  }

  return timingSafeEqual(leftBuffer, rightBuffer);
}

function isAccessTokenClaims(value: unknown): value is AccessTokenClaims {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<AccessTokenClaims>;

  return (
    typeof candidate.sub === 'string' &&
    typeof candidate.tenantId === 'string' &&
    typeof candidate.role === 'string' &&
    typeof candidate.iat === 'number' &&
    typeof candidate.exp === 'number'
  );
}
