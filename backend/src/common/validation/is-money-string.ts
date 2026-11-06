import { registerDecorator, ValidationOptions } from 'class-validator';

const MAX_DATABASE_AMOUNT = '9223372036854775807';

/** BIGINT PostgreSQL signé, sans conversion par un nombre IEEE-754. */
export function IsMoneyString(
  allowZero = false,
  options?: ValidationOptions,
): PropertyDecorator {
  return (target, propertyKey) =>
    registerDecorator({
      name: 'isMoneyString',
      target: target.constructor,
      propertyName: String(propertyKey),
      options,
      validator: {
        validate(value: unknown): boolean {
          if (
            typeof value !== 'string' ||
            value.length > MAX_DATABASE_AMOUNT.length
          )
            return false;
          if (!(allowZero ? /^(0|[1-9]\d*)$/ : /^[1-9]\d*$/).test(value))
            return false;
          return (
            value.length < MAX_DATABASE_AMOUNT.length ||
            value <= MAX_DATABASE_AMOUNT
          );
        },
        defaultMessage: () =>
          'Le montant doit être une chaîne entière en unités mineures dans la plage BIGINT.',
      },
    });
}
