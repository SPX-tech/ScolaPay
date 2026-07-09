import {
  Body,
  Controller,
  Get,
  INestApplication,
  Logger,
  NotFoundException,
  Post,
  ValidationPipe,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { Prisma } from '@prisma/client';
import { IsEmail, IsNotEmpty } from 'class-validator';
import request from 'supertest';
import { GlobalExceptionFilter } from './../src/common/filters/global-exception.filter';
import { requestIdMiddleware } from './../src/common/middleware/request-id.middleware';

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

class CreateThingDto {
  @IsEmail()
  email!: string;

  @IsNotEmpty()
  name!: string;
}

@Controller('boom')
class BoomController {
  @Get('not-found')
  notFound(): never {
    throw new NotFoundException('Élève introuvable.');
  }

  @Get('prisma-unique')
  prismaUnique(): never {
    throw new Prisma.PrismaClientKnownRequestError(
      'Unique constraint failed on the fields: (`email`)',
      { code: 'P2002', clientVersion: '6.19.3', meta: { target: ['email'] } },
    );
  }

  @Get('crash')
  crash(): never {
    throw new Error('connect ECONNREFUSED 10.0.0.5:5432 user=admin');
  }

  @Post('validate')
  validate(@Body() dto: CreateThingDto) {
    return dto;
  }
}

/**
 * E2e hermétique : le vrai middleware requestId + le vrai GlobalExceptionFilter
 * montés sur un contrôleur jetable. Aucune base de données requise.
 */
describe('Traçabilité et format d’erreur (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    // Le filtre journalise le détail réel des erreurs : on tait la sortie.
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);

    const moduleFixture: TestingModule = await Test.createTestingModule({
      controllers: [BoomController],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.use(requestIdMiddleware);
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    app.useGlobalFilters(new GlobalExceptionFilter());
    app.setGlobalPrefix('api/v1');

    await app.init();
  });

  afterAll(async () => {
    await app.close();
    jest.restoreAllMocks();
  });

  describe('X-Request-ID', () => {
    it('renvoie un UUID cohérent entre le header et le corps', async () => {
      const res = await request(app.getHttpServer()).get('/api/v1/boom/not-found');

      expect(res.headers['x-request-id']).toMatch(UUID_PATTERN);
      expect(res.body.requestId).toBe(res.headers['x-request-id']);
    });

    it('réutilise un identifiant de corrélation entrant valide', async () => {
      const res = await request(app.getHttpServer())
        .get('/api/v1/boom/not-found')
        .set('x-request-id', 'trace-abcdef123456');

      expect(res.headers['x-request-id']).toBe('trace-abcdef123456');
      expect(res.body.requestId).toBe('trace-abcdef123456');
    });

    it('remplace un identifiant entrant non conforme', async () => {
      const res = await request(app.getHttpServer())
        .get('/api/v1/boom/not-found')
        .set('x-request-id', 'bad!id');

      expect(res.headers['x-request-id']).not.toBe('bad!id');
      expect(res.headers['x-request-id']).toMatch(UUID_PATTERN);
    });

    it('est présent aussi sur une route inexistante', async () => {
      const res = await request(app.getHttpServer()).get('/api/v1/inconnu');

      expect(res.status).toBe(404);
      expect(res.headers['x-request-id']).toMatch(UUID_PATTERN);
      expect(res.body.requestId).toBe(res.headers['x-request-id']);
    });

    it('donne un identifiant distinct à chaque requête', async () => {
      const [first, second] = await Promise.all([
        request(app.getHttpServer()).get('/api/v1/boom/not-found'),
        request(app.getHttpServer()).get('/api/v1/boom/not-found'),
      ]);

      expect(first.body.requestId).not.toBe(second.body.requestId);
    });
  });

  describe('format d’erreur standardisé', () => {
    it('expose le contrat sur une 404 métier', async () => {
      const res = await request(app.getHttpServer()).get('/api/v1/boom/not-found');

      expect(res.status).toBe(404);
      expect(res.body).toMatchObject({
        statusCode: 404,
        error: 'Not Found',
        message: 'Élève introuvable.',
        path: '/api/v1/boom/not-found',
      });
      expect(res.body.timestamp).toEqual(expect.any(String));
    });

    it('aplatit les erreurs de validation et conserve le détail', async () => {
      const res = await request(app.getHttpServer())
        .post('/api/v1/boom/validate')
        .send({ email: 'pas-un-email', name: '' });

      expect(res.status).toBe(400);
      expect(typeof res.body.message).toBe('string');
      expect(res.body.details).toEqual(expect.arrayContaining([expect.any(String)]));
      expect(res.body.message).toContain('email');
    });
  });

  describe('non-divulgation', () => {
    it('convertit une violation d’unicité Prisma en 409 muet', async () => {
      const res = await request(app.getHttpServer()).get('/api/v1/boom/prisma-unique');

      expect(res.status).toBe(409);
      expect(res.body.message).toBe(
        "Cette ressource existe déjà (contrainte d'unicité).",
      );

      const raw = JSON.stringify(res.body);
      expect(raw).not.toContain('Unique constraint');
      expect(raw).not.toContain('email');
    });

    it('masque totalement une erreur non maîtrisée', async () => {
      const res = await request(app.getHttpServer()).get('/api/v1/boom/crash');

      expect(res.status).toBe(500);
      expect(res.body).toMatchObject({
        statusCode: 500,
        error: 'Internal Server Error',
        message: 'Une erreur interne est survenue.',
      });

      const raw = JSON.stringify(res.body);
      expect(raw).not.toContain('ECONNREFUSED');
      expect(raw).not.toContain('10.0.0.5');
      expect(res.body.stack).toBeUndefined();
    });
  });
});
