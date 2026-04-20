import {
  IsObject,
  IsOptional,
  IsString,
  Length,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class RecordCashPaymentDto {
  @IsString()
  @MinLength(20)
  studentId!: string;

  @IsString()
  @Matches(/^[1-9]\d*$/)
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
