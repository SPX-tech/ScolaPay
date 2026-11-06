import { BadRequestException } from '@nestjs/common';
import { parseDatePeriod, resolveReportPeriod } from './date-period';

const NDJAMENA = 'Africa/Ndjamena';

describe('parseDatePeriod', () => {
  it('couvre toute la journée locale pour des dates sans heure', () => {
    const { start, end } = parseDatePeriod(
      { startDate: '2026-02-01', endDate: '2026-02-01' },
      NDJAMENA,
    );
    expect(start?.toISOString()).toBe('2026-01-31T23:00:00.000Z');
    expect(end?.toISOString()).toBe('2026-02-01T22:59:59.999Z');
  });

  it('gère une journée de changement d’heure', () => {
    const { start, end } = parseDatePeriod(
      { startDate: '2026-03-08', endDate: '2026-03-08' },
      'America/New_York',
    );
    expect(start?.toISOString()).toBe('2026-03-08T05:00:00.000Z');
    expect(end?.toISOString()).toBe('2026-03-09T03:59:59.999Z');
  });

  it('conserve un instant explicite avec décalage', () => {
    const { start } = parseDatePeriod(
      { startDate: '2026-02-01T10:00:00+01:00' },
      NDJAMENA,
    );
    expect(start?.toISOString()).toBe('2026-02-01T09:00:00.000Z');
  });

  it.each([
    'invalid',
    '2026-02-31',
    '2026-13-01',
    '2026-02-01T10:00:00',
    '01/02/2026',
  ])('refuse la valeur %s', (value) => {
    expect(() => parseDatePeriod({ startDate: value }, NDJAMENA)).toThrow(
      BadRequestException,
    );
  });

  it('refuse une période inversée', () => {
    expect(() =>
      parseDatePeriod(
        { startDate: '2026-02-01', endDate: '2026-01-01' },
        NDJAMENA,
      ),
    ).toThrow(BadRequestException);
  });
});

describe('resolveReportPeriod', () => {
  it('démarre par défaut au début du mois local', () => {
    const now = new Date('2026-01-31T23:30:00.000Z');
    const { start, end } = resolveReportPeriod({}, NDJAMENA, now);
    expect(start.toISOString()).toBe('2026-01-31T23:00:00.000Z');
    expect(end).toBe(now);
  });

  it('refuse un début futur sans fin explicite', () => {
    expect(() =>
      resolveReportPeriod(
        { startDate: '2026-03-01' },
        NDJAMENA,
        new Date('2026-02-01T00:00:00.000Z'),
      ),
    ).toThrow(BadRequestException);
  });
});
