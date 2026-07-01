import { IsNotEmpty, IsString, MinLength } from 'class-validator';

export class FailWithdrawalDto {
  @IsString()
  @IsNotEmpty()
  @MinLength(5)
  reason!: string;
}
