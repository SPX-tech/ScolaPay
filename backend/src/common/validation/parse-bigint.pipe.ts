import { BadRequestException, Injectable, PipeTransform } from '@nestjs/common';

@Injectable()
export class ParseBigIntPipe implements PipeTransform<string, bigint> {
  transform(value: string): bigint {
    if (typeof value !== 'string' || !/^\d+$/.test(value)) {
      throw new BadRequestException('Value must be a positive integer string.');
    }

    try {
      return BigInt(value);
    } catch {
      throw new BadRequestException('Value cannot be converted to bigint.');
    }
  }
}
