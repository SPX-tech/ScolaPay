import { IsNumber, IsOptional, IsString, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class ListTransactionsQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  limit?: number = 50;

  @IsOptional()
  @IsString()
  type?: 'CASH_PAYMENT' | 'DEBT_ADJUSTMENT' | 'REFUND';

  @IsOptional()
  @IsString()
  status?: 'POSTED' | 'VOIDED';

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
