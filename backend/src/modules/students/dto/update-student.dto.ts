import { IsObject, IsOptional, IsString, Length, Matches, MinLength } from 'class-validator';
import { IsMoneyString } from '../../../common/validation/is-money-string';

export class UpdateStudentDto {
  @IsOptional()
  @IsString()
  @Length(3, 64)
  studentNumber?: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  fullName?: string;

  @IsOptional()
  @IsString()
  @Matches(/^\+?[0-9]{8,15}$/)
  guardianPhone?: string;

  @IsOptional()
  @IsString()
  @MinLength(2)
  guardianName?: string;

  @IsOptional()
  @IsString()
  classId?: string;

  @IsOptional()
  @IsString()
  status?: 'ACTIVE' | 'SUSPENDED' | 'GRADUATED';

  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @IsMoneyString(true)
  debtCents?: string;

  @IsOptional()
  @IsString()
  @IsMoneyString(true)
  creditCents?: string;
}
