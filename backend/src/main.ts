import 'dotenv/config';
import { configureApp } from './configure-app';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap() {
  // rawBody: true expose request.rawBody (Buffer) pour la vérification de
  // signature des webhooks entrants (ex. Kadryza), qui doit s'effectuer sur
  // les octets exacts reçus, pas sur le JSON reparsé.
  const app = await NestFactory.create(AppModule, { rawBody: true });
  configureApp(app);
  const port = Number(process.env.PORT ?? process.env.APP_PORT ?? 3000);

  app.enableShutdownHooks();

  await app.listen(port, '0.0.0.0');
}
void bootstrap();
