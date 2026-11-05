import { BadRequestException } from '@nestjs/common';

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;
const DATE_TIME =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})$/;

function timeZoneOffsetMs(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
  }).formatToParts(instant);
  const part = (type: string) =>
    Number(parts.find((entry) => entry.type === type)?.value);
  const wallClock = Date.UTC(
    part('year'),
    part('month') - 1,
    part('day'),
    part('hour'),
    part('minute'),
    part('second'),
  );
  return wallClock - Math.floor(instant / 1000) * 1000;
}

function startOfLocalDay(
  year: number,
  month: number,
  day: number,
  timeZone: string,
): Date {
  const wallClock = Date.UTC(year, month - 1, day);
  // Second passage : le décalage peut différer de part et d'autre d'un changement d'heure.
  const firstGuess = wallClock - timeZoneOffsetMs(wallClock, timeZone);
  return new Date(wallClock - timeZoneOffsetMs(firstGuess, timeZone));
}

function parseBound(
  value: string,
  bound: 'start' | 'end',
  timeZone: string,
): Date {
  const dateOnly = DATE_ONLY.exec(value);
  if (dateOnly) {
    const [year, month, day] = dateOnly.slice(1).map(Number);
    const check = new Date(Date.UTC(year, month - 1, day));
    if (
      check.getUTCFullYear() !== year ||
      check.getUTCMonth() !== month - 1 ||
      check.getUTCDate() !== day
    ) {
      throw new BadRequestException(`Date invalide : "${value}".`);
    }
    return bound === 'start'
      ? startOfLocalDay(year, month, day, timeZone)
      : new Date(startOfLocalDay(year, month, day + 1, timeZone).getTime() - 1);
  }

  const instant = DATE_TIME.test(value) ? new Date(value) : null;
  if (!instant || Number.isNaN(instant.getTime())) {
    throw new BadRequestException(`Date invalide : "${value}".`);
  }
  return instant;
}

function assertOrdered(start: Date, end: Date): void {
  if (start > end) {
    throw new BadRequestException(
      'La date de début doit précéder la date de fin.',
    );
  }
}

/**
 * Une date sans heure désigne la journée entière dans le fuseau de
 * l'établissement. Une date avec heure doit porter son décalage.
 */
export function parseDatePeriod(
  range: { startDate?: string; endDate?: string },
  timeZone: string,
): { start?: Date; end?: Date } {
  const start = range.startDate
    ? parseBound(range.startDate, 'start', timeZone)
    : undefined;
  const end = range.endDate
    ? parseBound(range.endDate, 'end', timeZone)
    : undefined;
  if (start && end) {
    assertOrdered(start, end);
  }
  return { start, end };
}

/** Sans borne fournie : du début du mois courant de l'établissement à maintenant. */
export function resolveReportPeriod(
  range: { startDate?: string; endDate?: string },
  timeZone: string,
  now: Date = new Date(),
): { start: Date; end: Date } {
  const parsed = parseDatePeriod(range, timeZone);
  const end = parsed.end ?? now;
  const local = new Date(
    now.getTime() + timeZoneOffsetMs(now.getTime(), timeZone),
  );
  const start =
    parsed.start ??
    startOfLocalDay(
      local.getUTCFullYear(),
      local.getUTCMonth() + 1,
      1,
      timeZone,
    );
  assertOrdered(start, end);
  return { start, end };
}
