import { firstValueFrom, of } from 'rxjs';
import { ExecutionContext } from '@nestjs/common';
import { BigIntSerializerInterceptor } from './bigint-serializer.interceptor';

describe('BigIntSerializerInterceptor', () => {
  it('préserve les dates ISO et les montants au-delà de la précision JavaScript', async () => {
    const date = new Date('2026-07-01T10:00:00.000Z');
    const interceptor = new BigIntSerializerInterceptor();
    const result = await firstValueFrom(
      interceptor.intercept({} as ExecutionContext, {
        handle: () => of({ amountCents: 9007199254740993n, recordedAt: date, nested: [null, 0n] }),
      }),
    );
    expect(JSON.parse(JSON.stringify(result))).toEqual({
      amountCents: '9007199254740993',
      recordedAt: '2026-07-01T10:00:00.000Z',
      nested: [null, '0'],
    });
  });
});
