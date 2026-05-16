import { IsEmail, IsEnum, IsOptional, IsString, Matches, MinLength } from 'class-validator';

export class CreateUserDto {
  @IsEmail()
  email!: string;

  @IsString()
  @MinLength(3)
  fullName!: string;

  @IsOptional()
  @IsString()
  @Matches(/^\+?[0-9]{8,15}$/)
  phone?: string;

  @IsString()
  @MinLength(8)
  password!: string;

  @IsString()
  @IsEnum(['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT', 'AUDITOR'])
  role!: 'TENANT_ADMIN' | 'CASHIER' | 'ACCOUNTANT' | 'AUDITOR';
}
