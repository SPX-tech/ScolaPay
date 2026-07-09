import { ConsoleLogger, Injectable, LogLevel } from '@nestjs/common';
import { getRequestId } from './request-context.store';

/**
 * Logger applicatif : identique au ConsoleLogger de Nest, mais chaque ligne
 * émise pendant une requête HTTP porte son identifiant de corrélation.
 *
 * `printMessages` est l'entonnoir unique par lequel passent tous les niveaux
 * (log/error/warn/debug/verbose/fatal) et les deux formats de sortie (texte et
 * JSON) : l'enrichir une seule fois suffit à couvrir l'ensemble des logs.
 */
@Injectable()
export class AppLogger extends ConsoleLogger {
  protected printMessages(
    messages: unknown[],
    context = '',
    logLevel: LogLevel = 'log',
    writeStreamType?: 'stdout' | 'stderr',
    errorStack?: unknown,
  ): void {
    const requestId = getRequestId();
    const enrichedContext = requestId
      ? `${context || 'App'} req=${requestId}`
      : context;

    super.printMessages(
      messages,
      enrichedContext,
      logLevel,
      writeStreamType,
      errorStack,
    );
  }
}
