import { ValidationPipe } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { AuthGuard } from './common/guards/auth.guard';
import { TenantGuard } from './common/guards/tenant.guard';
import { RolesGuard } from './common/guards/roles.guard';
import { BigIntSerializerInterceptor } from './common/interceptors/bigint-serializer.interceptor';

async function bootstrap() {
  // rawBody: true expose request.rawBody (Buffer) pour la vérification de
  // signature des webhooks entrants (ex. Kadryza), qui doit s'effectuer sur
  // les octets exacts reçus, pas sur le JSON reparsé.
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    rawBody: true,
  });

  // Derrière nginx (docker-compose) : faire confiance au premier proxy pour
  // que req.ip et X-Forwarded-For reflètent l'IP réelle du client. Sans cela,
  // le rate-limiting (ThrottlerGuard) compterait toutes les requêtes sous
  // l'unique IP de nginx et deviendrait inopérant.
  app.set('trust proxy', 1);

  const reflector = app.get(Reflector);

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  app.useGlobalGuards(
    new AuthGuard(reflector),
    new TenantGuard(reflector),
    new RolesGuard(reflector),
  );
  app.useGlobalInterceptors(new BigIntSerializerInterceptor());

  app.setGlobalPrefix('api/v1');
  const port = Number(process.env.PORT ?? process.env.APP_PORT ?? 3000);

  app.enableShutdownHooks();

  await app.listen(port, '0.0.0.0');
}
bootstrap();
