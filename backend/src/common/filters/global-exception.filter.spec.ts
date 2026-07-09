import {
  ArgumentsHost,
  BadRequestException,
  ConflictException,
  HttpException,
  HttpStatus,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { runWithRequestId } from '../logging/request-context.store';
import {
  GlobalExceptionFilter,
  type ErrorResponseBody,
} from './global-exception.filter';

function prismaKnownError(
  code: string,
  message = 'raw prisma internals',
  meta?: Record<string, unknown>,
): Prisma.PrismaClientKnownRequestError {
  return new Prisma.PrismaClientKnownRequestError(message, {
    code,
    clientVersion: '6.19.3',
    meta,
  });
}

describe('GlobalExceptionFilter', () => {
  let filter: GlobalExceptionFilter;
  let response: {
    headersSent: boolean;
    setHeader: jest.Mock;
    status: jest.Mock;
    json: jest.Mock;
  };
  let request: Record<string, unknown>;

  /** Déclenche le filtre et retourne le corps JSON effectivement écrit. */
  function run(exception: unknown): ErrorResponseBody {
    const host = {
      switchToHttp: () => ({
        getRequest: () => request,
        getResponse: () => response,
      }),
    } as unknown as ArgumentsHost;

    filter.catch(exception, host);

    return response.json.mock.calls[0]?.[0] as ErrorResponseBody;
  }

  beforeAll(() => {
    // Le filtre journalise volontairement le détail réel : on le tait ici.
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
  });

  afterAll(() => {
    jest.restoreAllMocks();
  });

  beforeEach(() => {
    filter = new GlobalExceptionFilter();
    response = {
      headersSent: false,
      setHeader: jest.fn(),
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    request = {
      method: 'POST',
      originalUrl: '/api/v1/students?token=super-secret',
      requestId: 'req-from-request-object',
    };
  });

  describe('format standardisé', () => {
    it('renvoie le contrat complet et enlève la query string du chemin', () => {
      const body = run(new NotFoundException('Élève introuvable.'));

      expect(response.status).toHaveBeenCalledWith(404);
      expect(body).toMatchObject({
        statusCode: 404,
        error: 'Not Found',
        message: 'Élève introuvable.',
        path: '/api/v1/students',
      });
      expect(body.timestamp).toEqual(expect.any(String));
      expect(Number.isNaN(Date.parse(body.timestamp))).toBe(false);
      // Un jeton passé en query ne doit jamais être recopié dans la réponse.
      expect(body.path).not.toContain('super-secret');
    });

    it('reprend le requestId du store asynchrone et le pose en header', () => {
      const body = runWithRequestId('req-from-store', () =>
        run(new NotFoundException('nope')),
      );

      expect(body.requestId).toBe('req-from-store');
      expect(response.setHeader).toHaveBeenCalledWith(
        'x-request-id',
        'req-from-store',
      );
    });

    it('retombe sur le requestId porté par la requête hors du store', () => {
      expect(run(new NotFoundException('nope')).requestId).toBe(
        'req-from-request-object',
      );
    });

    it('n’écrit rien si la réponse est déjà partie', () => {
      response.headersSent = true;

      run(new NotFoundException('nope'));

      expect(response.status).not.toHaveBeenCalled();
      expect(response.json).not.toHaveBeenCalled();
    });
  });

  describe('HttpException', () => {
    it('aplatit les messages de ValidationPipe et conserve le détail', () => {
      const body = run(
        new BadRequestException({
          statusCode: 400,
          error: 'Bad Request',
          message: ['email must be an email', 'password should not be empty'],
        }),
      );

      expect(body.statusCode).toBe(400);
      expect(body.message).toBe(
        'email must be an email ; password should not be empty',
      );
      expect(body.details).toEqual([
        'email must be an email',
        'password should not be empty',
      ]);
    });

    it('préserve le message métier des erreurs 4xx', () => {
      const body = run(new ConflictException('Ce numéro d’élève existe déjà.'));

      expect(body).toMatchObject({
        statusCode: 409,
        error: 'Conflict',
        message: 'Ce numéro d’élève existe déjà.',
      });
      expect(body.details).toBeUndefined();
    });

    it('masque le message des HttpException 5xx (fuite de configuration)', () => {
      const body = run(
        new InternalServerErrorException('ACCESS_TOKEN_SECRET is not configured.'),
      );

      expect(body.statusCode).toBe(500);
      expect(body.message).toBe('Une erreur interne est survenue.');
      expect(JSON.stringify(body)).not.toContain('ACCESS_TOKEN_SECRET');
    });

    it('traduit la 429 du throttler en message actionnable', () => {
      const body = run(
        new HttpException(
          'ThrottlerException: Too Many Requests',
          HttpStatus.TOO_MANY_REQUESTS,
        ),
      );

      expect(body).toMatchObject({
        statusCode: 429,
        error: 'Too Many Requests',
        message: 'Trop de requêtes. Veuillez réessayer dans quelques instants.',
      });
      expect(JSON.stringify(body)).not.toContain('ThrottlerException');
    });
  });

  describe('erreurs Prisma (aucune fuite de schéma)', () => {
    it('P2002 → 409 sans citer la contrainte ni la colonne', () => {
      const body = run(
        prismaKnownError(
          'P2002',
          'Unique constraint failed on the fields: (`email`)',
          { target: ['email'] },
        ),
      );

      expect(body).toMatchObject({
        statusCode: 409,
        error: 'Conflict',
        message: "Cette ressource existe déjà (contrainte d'unicité).",
      });

      const serialized = JSON.stringify(body);
      expect(serialized).not.toContain('email');
      expect(serialized).not.toContain('Unique constraint');
    });

    it('P2003 → 409 (clé étrangère)', () => {
      expect(run(prismaKnownError('P2003')).statusCode).toBe(409);
    });

    it('P2025 → 404', () => {
      expect(run(prismaKnownError('P2025')).statusCode).toBe(404);
    });

    it('P2000 → 400', () => {
      expect(run(prismaKnownError('P2000')).statusCode).toBe(400);
    });

    it('P2034 (conflit d’écriture concurrente) → 409 rejouable', () => {
      const body = run(prismaKnownError('P2034'));

      expect(body.statusCode).toBe(409);
      expect(body.message).toBe('Conflit de transaction. Veuillez réessayer.');
    });

    it('code Prisma non cartographié → 500 générique', () => {
      const body = run(prismaKnownError('P2028', 'Transaction API error'));

      expect(body.statusCode).toBe(500);
      expect(body.message).toBe('Une erreur interne est survenue.');
      expect(JSON.stringify(body)).not.toContain('Transaction API error');
    });

    it('reconnaît une erreur Prisma ayant perdu son prototype', () => {
      const detached = Object.assign(new Error('Unique constraint failed'), {
        code: 'P2002',
        clientVersion: '6.19.3',
      });

      expect(run(detached).statusCode).toBe(409);
    });

    it('ignore un objet qui imite un code Prisma sans clientVersion', () => {
      expect(run({ code: 'P2002' }).statusCode).toBe(500);
    });

    it('PrismaClientValidationError → 400 sans exposer le modèle', () => {
      const body = run(
        new Prisma.PrismaClientValidationError(
          'Argument `debtCents` is missing on model `Student`',
          { clientVersion: '6.19.3' },
        ),
      );

      expect(body).toMatchObject({ statusCode: 400, message: 'Requête invalide.' });
      expect(JSON.stringify(body)).not.toContain('Student');
    });

    it('PrismaClientInitializationError → 503', () => {
      const body = run(
        new Prisma.PrismaClientInitializationError(
          'Can’t reach database server at postgres:5432',
          '6.19.3',
        ),
      );

      expect(body.statusCode).toBe(503);
      expect(JSON.stringify(body)).not.toContain('postgres:5432');
    });
  });

  describe('erreurs non maîtrisées', () => {
    it('masque le message et la stack d’une Error brute', () => {
      const body = run(new Error('connect ECONNREFUSED 10.0.0.5:5432 user=admin'));

      expect(body).toMatchObject({
        statusCode: 500,
        error: 'Internal Server Error',
        message: 'Une erreur interne est survenue.',
      });

      const serialized = JSON.stringify(body);
      expect(serialized).not.toContain('ECONNREFUSED');
      expect(serialized).not.toContain('10.0.0.5');
      expect(body).not.toHaveProperty('stack');
    });

    it('encaisse une valeur jetée qui n’est pas une Error', () => {
      expect(run('boom').statusCode).toBe(500);
    });
  });
});
