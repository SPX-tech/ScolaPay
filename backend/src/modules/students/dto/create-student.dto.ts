import { IsOptional, IsString, Length, Matches, MinLength } from 'class-validator';

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
}
