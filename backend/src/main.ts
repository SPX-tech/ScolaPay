import { ValidationPipe } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { AuthGuard } from './common/guards/auth.guard';
import { TenantGuard } from './common/guards/tenant.guard';
import { RolesGuard } from './common/guards/roles.guard';
import { BigIntSerializerInterceptor } from './common/interceptors/bigint-serializer.interceptor';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
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
