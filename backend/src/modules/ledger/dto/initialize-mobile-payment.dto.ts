import { IsString, Matches, MaxLength, MinLength } from 'class-validator';

export class InitializeMobilePaymentDto {
  @IsString()
  @MinLength(20)
  studentId!: string;

  @IsString()
  @Matches(/^[1-9]\d*$/)
  amountCents!: string;

  /** Numéro mobile money qui sera débité (push USSD). */
  @IsString()
  @MinLength(8)
  @MaxLength(20)
  payerPhone!: string;
}
