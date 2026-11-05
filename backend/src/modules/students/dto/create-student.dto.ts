import {
  IsObject,
  IsOptional,
  IsString,
  Length,
  Matches,
  MinLength,
} from 'class-validator';
import { IsMoneyString } from '../../../common/validation/is-money-string';

export class CreateStudentDto {
  @IsString()
  @Length(3, 64)
  studentNumber!: string;

  @IsString()
  @MinLength(3)
  fullName!: string;

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
  @IsObject()
  metadata?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @IsMoneyString(true)
  initialDebtCents?: string;
}
