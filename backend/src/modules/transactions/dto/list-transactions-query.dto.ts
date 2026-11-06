import { IsIn, IsInt, IsOptional, IsString, Max, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class ListTransactionsQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number = 50;

  @IsOptional()
  @IsIn([
    'CASH_PAYMENT',
    'DEBT_ADJUSTMENT',
    'REFUND',
    'MOBILE_PAYMENT',
    'WITHDRAWAL',
  ])
  type?:
    | 'CASH_PAYMENT'
    | 'DEBT_ADJUSTMENT'
    | 'REFUND'
    | 'MOBILE_PAYMENT'
    | 'WITHDRAWAL';

  @IsOptional()
  @IsIn(['PENDING', 'POSTED', 'FAILED', 'VOIDED'])
  status?: 'PENDING' | 'POSTED' | 'FAILED' | 'VOIDED';

  @IsOptional()
  @IsString()
  studentId?: string;

  @IsOptional()
  @IsString()
  startDate?: string;

  @IsOptional()
  @IsString()
  endDate?: string;
}
