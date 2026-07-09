import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import type { Response } from 'express';
import { REQUEST_ID_HEADER } from '../constants/request-id.constants';
import { getRequestId } from '../logging/request-context.store';
import type { RequestWithId } from '../middleware/request-id.middleware';

/** Message unique renvoyé pour toute erreur serveur : aucune information interne. */
const GENERIC_SERVER_ERROR = 'Une erreur interne est survenue.';

/** Corps d'erreur unique de l'API. `message` reste toujours une chaîne. */
export type ErrorResponseBody = {
  statusCode: number;
  error: string;
  message: string;
  details?: string[];
  requestId: string;
  timestamp: string;
  path: string;
};

type ResolvedError = {
  status: number;
  message: string;
  details?: string[];
};

/**
 * Duck-typing volontaire en complément de `instanceof` : une erreur Prisma peut
 * traverser une frontière de module (ou être simulée en test) et perdre son
 * prototype. `clientVersion` évite les faux positifs sur un simple `{ code }`.
 */
function isPrismaKnownRequestError(
  exception: unknown,
): exception is Prisma.PrismaClientKnownRequestError {
  if (exception instanceof Prisma.PrismaClientKnownRequestError) {
    return true;
  }

  const candidate = exception as { code?: unknown; clientVersion?: unknown };
  return (
    typeof candidate?.code === 'string' &&
    candidate.code.startsWith('P') &&
    typeof candidate.clientVersion === 'string'
  );
}

/** 404 -> "Not Found", 429 -> "Too Many Requests". */
function httpStatusName(status: number): string {
  const key = HttpStatus[status] as string | undefined;
  if (!key) {
    return 'Error';
  }

  return key
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/** La query string peut porter des jetons : jamais recopiée dans la réponse. */
function stripQuery(url: string): string {
  return url.split('?')[0];
}

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(GlobalExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const request = http.getRequest<RequestWithId>();
    const response = http.getResponse<Response>();

    const resolved = this.resolve(exception);
    const requestId = getRequestId() ?? request?.requestId ?? 'unknown';

    // Le détail réel (message Prisma, stack, secret de config) reste côté serveur.
    this.logException(exception, resolved.status, request);

    if (response.headersSent) {
      return;
    }

    const body: ErrorResponseBody = {
      statusCode: resolved.status,
      error: httpStatusName(resolved.status),
      message: resolved.message,
      ...(resolved.details?.length ? { details: resolved.details } : {}),
      requestId,
      timestamp: new Date().toISOString(),
      path: stripQuery(request?.originalUrl ?? request?.url ?? ''),
    };

    response.setHeader(REQUEST_ID_HEADER, requestId);
    response.status(resolved.status).json(body);
  }

  private resolve(exception: unknown): ResolvedError {
    if (exception instanceof HttpException) {
      return this.fromHttpException(exception);
    }

    if (isPrismaKnownRequestError(exception)) {
      return this.fromPrismaKnownRequestError(exception);
    }

    // Le message d'une erreur de validation Prisma détaille modèles, champs et
    // arguments de la requête : il ne doit jamais sortir du serveur.
    if (exception instanceof Prisma.PrismaClientValidationError) {
      return { status: HttpStatus.BAD_REQUEST, message: 'Requête invalide.' };
    }

    if (exception instanceof Prisma.PrismaClientInitializationError) {
      return {
        status: HttpStatus.SERVICE_UNAVAILABLE,
        message: 'Service temporairement indisponible.',
      };
    }

    return {
      status: HttpStatus.INTERNAL_SERVER_ERROR,
      message: GENERIC_SERVER_ERROR,
    };
  }

  private fromHttpException(exception: HttpException): ResolvedError {
    const status = exception.getStatus();

    // Une 5xx portée par une HttpException reste une erreur interne : son
    // message peut décrire la configuration (ex. « ACCESS_TOKEN_SECRET is not
    // configured »). On conserve le code, on masque le texte.
    if (status >= HttpStatus.INTERNAL_SERVER_ERROR) {
      return { status, message: GENERIC_SERVER_ERROR };
    }

    if (status === HttpStatus.TOO_MANY_REQUESTS) {
      return {
        status,
        message: 'Trop de requêtes. Veuillez réessayer dans quelques instants.',
      };
    }

    const payload = exception.getResponse();

    if (typeof payload === 'string') {
      return { status, message: payload };
    }

    const rawMessage = (payload as { message?: unknown })?.message;

    // ValidationPipe renvoie un tableau : on l'aplatit dans `message` (le front
    // affiche cette chaîne) tout en conservant le détail ligne à ligne.
    if (Array.isArray(rawMessage)) {
      const details = rawMessage.map((entry) => String(entry));
      return { status, message: details.join(' ; '), details };
    }

    if (typeof rawMessage === 'string' && rawMessage.length > 0) {
      return { status, message: rawMessage };
    }

    return { status, message: exception.message || httpStatusName(status) };
  }

  /**
   * Ni `message` ni `meta` ne sont exposés : ils citent les noms de tables, de
   * colonnes et de contraintes du schéma.
   */
  private fromPrismaKnownRequestError(
    exception: Prisma.PrismaClientKnownRequestError,
  ): ResolvedError {
    switch (exception.code) {
      case 'P2000':
        return {
          status: HttpStatus.BAD_REQUEST,
          message: 'Une valeur dépasse la longueur autorisée.',
        };
      case 'P2002':
        return {
          status: HttpStatus.CONFLICT,
          message: "Cette ressource existe déjà (contrainte d'unicité).",
        };
      case 'P2003':
        return {
          status: HttpStatus.CONFLICT,
          message: 'Référence invalide : la ressource liée est introuvable.',
        };
      case 'P2025':
        return {
          status: HttpStatus.NOT_FOUND,
          message: 'Ressource introuvable.',
        };
      case 'P2034':
        // Écriture concurrente / deadlock : le client peut rejouer la requête.
        return {
          status: HttpStatus.CONFLICT,
          message: 'Conflit de transaction. Veuillez réessayer.',
        };
      default:
        return {
          status: HttpStatus.INTERNAL_SERVER_ERROR,
          message: GENERIC_SERVER_ERROR,
        };
    }
  }

  private logException(
    exception: unknown,
    status: number,
    request: RequestWithId | undefined,
  ): void {
    const route = `${request?.method ?? '-'} ${stripQuery(request?.originalUrl ?? request?.url ?? '-')}`;
    const name = exception instanceof Error ? exception.name : typeof exception;
    const prismaCode = isPrismaKnownRequestError(exception)
      ? ` code=${exception.code}`
      : '';
    const detail =
      exception instanceof Error ? exception.message : String(exception);

    // Le corps de la requête n'est jamais journalisé (mots de passe, PII élèves).
    const line = `${status} ${route}${prismaCode} — ${name}: ${detail}`;

    if (status >= HttpStatus.INTERNAL_SERVER_ERROR) {
      this.logger.error(
        line,
        exception instanceof Error ? exception.stack : undefined,
      );
      return;
    }

    this.logger.warn(line);
  }
}
