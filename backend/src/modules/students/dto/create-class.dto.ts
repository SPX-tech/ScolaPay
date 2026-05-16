import { IsOptional, IsString, MinLength } from 'class-validator';

export class CreateClassDto {
  @IsString()
  @MinLength(2)
  name!: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsString()
  @MinLength(4)
  academicYear!: string;
}
