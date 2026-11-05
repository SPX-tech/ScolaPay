import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from './common/guards/auth.guard';
import { TenantGuard } from './common/guards/tenant.guard';
import { RolesGuard } from './common/guards/roles.guard';
import { BigIntSerializerInterceptor } from './common/interceptors/bigint-serializer.interceptor';

/** Configuration partagée par le serveur HTTP et les tests de ses contrats. */
export function configureApp(app: INestApplication): void {
  const reflector = app.get(Reflector);
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true,
    transform: true,
  }));
  app.useGlobalGuards(new AuthGuard(reflector), new TenantGuard(reflector), new RolesGuard(reflector));
  app.useGlobalInterceptors(new BigIntSerializerInterceptor());
  app.setGlobalPrefix('api/v1');
}
