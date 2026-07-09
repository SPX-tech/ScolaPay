import { randomUUID } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';
import {
  REQUEST_ID_HEADER,
  SAFE_REQUEST_ID_PATTERN,
} from '../constants/request-id.constants';
import { runWithRequestId } from '../logging/request-context.store';

export type RequestWithId = Request & { requestId?: string };

/**
 * Réutilise l'identifiant fourni en amont (nginx, passerelle, appelant) pour
 * garder une trace continue de bout en bout, mais uniquement s'il est sûr :
 * une valeur cliente arbitraire finirait dans les logs et dans un header de
 * réponse.
 */
export function resolveRequestId(headerValue: unknown): string {
  const candidate = Array.isArray(headerValue) ? headerValue[0] : headerValue;

  return typeof candidate === 'string' && SAFE_REQUEST_ID_PATTERN.test(candidate)
    ? candidate
    : randomUUID();
}

/**
 * Enregistré via `app.use()` en tout premier : le header X-Request-ID est donc
 * présent même sur les réponses produites avant les contrôleurs (401 des guards,
 * 429 du throttler, 404 de routage).
 */
export function requestIdMiddleware(
  request: RequestWithId,
  response: Response,
  next: NextFunction,
): void {
  const requestId = resolveRequestId(request.headers[REQUEST_ID_HEADER]);

  request.requestId = requestId;
  response.setHeader(REQUEST_ID_HEADER, requestId);

  // `next` est exécuté à l'intérieur du store : toute la suite de la chaîne
  // asynchrone de cette requête voit le même identifiant.
  runWithRequestId(requestId, next);
}
