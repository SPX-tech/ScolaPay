import {
  ArrayMaxSize,
  IsArray,
  IsObject,
  IsOptional,
  IsString,
  MinLength,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { IsMoneyString } from '../../../common/validation/is-money-string';

class ImportStudentRow {
  @IsString()
  @MinLength(1)
  studentNumber!: string;

  @IsString()
  @MinLength(1)
  fullName!: string;

  @IsOptional()
  @IsString()
  guardianPhone?: string;

  @IsOptional()
  @IsString()
  guardianName?: string;

  @IsOptional()
  @IsString()
  debtAmount?: string;

  @IsOptional()
  @IsString()
  className?: string;

  @IsOptional()
  @IsString()
  @IsMoneyString(true)
  debtCents?: string;

  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class ImportStudentsDto {
  @IsArray()
  @ArrayMaxSize(1000)
  @ValidateNested({ each: true })
  @Type(() => ImportStudentRow)
  students!: ImportStudentRow[];
}
