import {
  IsEmail,
  IsEnum,
  IsOptional,
  IsString,
  Matches,
  MinLength,
} from 'class-validator';

export class UpdateUserDto {
  @IsOptional()
  @IsEmail()
  email?: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  fullName?: string;

  @IsOptional()
  @IsString()
  @Matches(/^\+?[0-9]{8,15}$/)
  phone?: string;

  @IsOptional()
  @IsString()
  @MinLength(8)
  password?: string;

  @IsOptional()
  @IsString()
  @IsEnum(['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT', 'AUDITOR'])
  role?: 'TENANT_ADMIN' | 'CASHIER' | 'ACCOUNTANT' | 'AUDITOR';

  @IsOptional()
  @IsString()
  @IsEnum(['ACTIVE', 'INVITED', 'SUSPENDED'])
  status?: 'ACTIVE' | 'INVITED' | 'SUSPENDED';
}
