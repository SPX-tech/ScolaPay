import { IsArray, IsObject, IsOptional, IsString, MinLength, ValidateNested } from 'class-validator';
import { Type } from 'class-transformer';

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
  debtCents?: string;

  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class ImportStudentsDto {
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => ImportStudentRow)
  students!: ImportStudentRow[];
}
