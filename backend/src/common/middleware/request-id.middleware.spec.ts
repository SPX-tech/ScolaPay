import { REQUEST_ID_HEADER } from '../constants/request-id.constants';
import { getRequestId } from '../logging/request-context.store';
import {
  requestIdMiddleware,
  resolveRequestId,
  type RequestWithId,
} from './request-id.middleware';

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function fakeRequest(headers: Record<string, unknown> = {}): RequestWithId {
  return { headers, method: 'GET', originalUrl: '/api/v1/health' } as unknown as RequestWithId;
}

function fakeResponse() {
  return { setHeader: jest.fn() };
}

describe('resolveRequestId', () => {
  it('génère un UUID quand aucun identifiant n’est fourni', () => {
    expect(resolveRequestId(undefined)).toMatch(UUID_PATTERN);
  });

  it('réutilise un identifiant entrant sûr', () => {
    expect(resolveRequestId('nginx-req-0001')).toBe('nginx-req-0001');
  });

  it('accepte la première valeur quand le header est répété', () => {
    expect(resolveRequestId(['nginx-req-0001', 'autre'])).toBe('nginx-req-0001');
  });

  it.each([
    ['trop court', 'abc'],
    ['trop long', 'a'.repeat(65)],
    ['injection de log (CRLF)', 'abcdefgh\r\nFATAL faux-log'],
    ['sequence ANSI', `abcdefgh${String.fromCharCode(27)}[31m`],
    ['espaces', 'abcd efgh'],
    ['non-chaîne', 42 as unknown as string],
  ])('rejette un identifiant entrant %s et en génère un neuf', (_label, value) => {
    const resolved = resolveRequestId(value);

    expect(resolved).not.toBe(value);
    expect(resolved).toMatch(UUID_PATTERN);
  });
});

describe('requestIdMiddleware', () => {
  it('expose le requestId sur la requête, le header et le store asynchrone', () => {
    const request = fakeRequest();
    const response = fakeResponse();
    let seenInsideStore: string | undefined;

    requestIdMiddleware(request, response as never, () => {
      seenInsideStore = getRequestId();
    });

    expect(request.requestId).toMatch(UUID_PATTERN);
    expect(response.setHeader).toHaveBeenCalledWith(
      REQUEST_ID_HEADER,
      request.requestId,
    );
    expect(seenInsideStore).toBe(request.requestId);
  });

  it('propage le requestId à travers les frontières asynchrones', async () => {
    const request = fakeRequest();
    const response = fakeResponse();
    let seenAfterAwait: string | undefined;

    await new Promise<void>((resolve) => {
      requestIdMiddleware(request, response as never, async () => {
        await new Promise((tick) => setTimeout(tick, 1));
        seenAfterAwait = getRequestId();
        resolve();
      });
    });

    expect(seenAfterAwait).toBe(request.requestId);
  });

  it('conserve un identifiant de corrélation fourni en amont', () => {
    const request = fakeRequest({ [REQUEST_ID_HEADER]: 'trace-abcdef123456' });
    const response = fakeResponse();

    requestIdMiddleware(request, response as never, () => undefined);

    expect(request.requestId).toBe('trace-abcdef123456');
  });

  it('ne fuit pas hors du contexte de la requête', () => {
    expect(getRequestId()).toBeUndefined();
  });
});
