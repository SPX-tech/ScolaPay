import {
  IsObject,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  MinLength,
} from 'class-validator';
import { IsMoneyString } from '../../../common/validation/is-money-string';

export class RecordCashPaymentDto {
  @IsString()
  @MinLength(20)
  studentId!: string;

  @IsString()
  @IsMoneyString()
  amountCents!: string;

  @IsOptional()
  @IsString()
  @Length(8, 128)
  idempotencyKey?: string;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  note?: string;

  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}
