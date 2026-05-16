import { IsOptional, IsString, MinLength } from 'class-validator';

export class UpdateClassDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  name?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  @MinLength(4)
  academicYear?: string;

  @IsOptional()
  @IsString()
  status?: 'ACTIVE' | 'ARCHIVED';
}
