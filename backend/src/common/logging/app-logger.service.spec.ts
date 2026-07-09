import { ConsoleLogger } from '@nestjs/common';
import { AppLogger } from './app-logger.service';
import { runWithRequestId } from './request-context.store';

describe('AppLogger', () => {
  let printed: jest.SpyInstance;

  beforeEach(() => {
    // On intercepte l'implémentation parente : c'est l'argument `context`
    // transmis à ConsoleLogger qui nous intéresse, pas la sortie console.
    printed = jest
      .spyOn(ConsoleLogger.prototype as never, 'printMessages')
      .mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  /** Contexte réellement transmis au ConsoleLogger sous-jacent. */
  function contextOfLastLog(): string {
    return printed.mock.calls[0][1] as string;
  }

  it('préfixe le contexte avec le requestId pendant une requête', () => {
    const logger = new AppLogger('PaymentsService');

    runWithRequestId('req-42-abcdef', () => logger.log('Encaissement enregistré'));

    expect(contextOfLastLog()).toBe('PaymentsService req=req-42-abcdef');
  });

  it('laisse le contexte intact hors requête (bootstrap, cron, outbox)', () => {
    const logger = new AppLogger('OutboxDispatcher');

    logger.log('Cycle terminé');

    expect(contextOfLastLog()).toBe('OutboxDispatcher');
  });

  it('retombe sur « App » quand aucun contexte n’est fourni', () => {
    const logger = new AppLogger();

    runWithRequestId('req-sans-contexte', () => logger.log('message nu'));

    expect(contextOfLastLog()).toBe('App req=req-sans-contexte');
  });

  it.each(['error', 'warn', 'debug', 'verbose', 'fatal'] as const)(
    'enrichit aussi le niveau %s',
    (level) => {
      const logger = new AppLogger('Ctx');
      logger.setLogLevels(['error', 'warn', 'debug', 'verbose', 'fatal', 'log']);

      runWithRequestId('req-tous-niveaux', () => logger[level]('boom'));

      expect(contextOfLastLog()).toBe('Ctx req=req-tous-niveaux');
    },
  );

  it('conserve le niveau et le flux de sortie transmis au parent', () => {
    const logger = new AppLogger('Ctx');

    runWithRequestId('req-flux', () => logger.error('échec'));

    expect(printed).toHaveBeenCalledWith(
      ['échec'],
      'Ctx req=req-flux',
      'error',
      'stderr',
      undefined,
    );
  });
});
