import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable()
export class BigIntSerializerInterceptor implements NestInterceptor {
  intercept(_context: ExecutionContext, next: CallHandler): Observable<unknown> {
    return next.handle().pipe(map((value: unknown) => serializeBigInt(value)));
  }
}

function serializeBigInt(value: unknown): unknown {
  if (typeof value === 'bigint') {
    return value.toString();
  }

  if (Array.isArray(value)) {
    return value.map((entry) => serializeBigInt(entry));
  }

  if (value && typeof value === 'object') {
    const serializedObject: Record<string, unknown> = {};
    for (const [key, nestedValue] of Object.entries(value)) {
      serializedObject[key] = serializeBigInt(nestedValue);
    }

    return serializedObject;
  }

  return value;
}
