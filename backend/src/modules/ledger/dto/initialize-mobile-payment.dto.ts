import { IsString, MaxLength, MinLength } from 'class-validator';
import { IsMoneyString } from '../../../common/validation/is-money-string';

export class InitializeMobilePaymentDto {
  @IsString()
  @MinLength(20)
  studentId!: string;

  @IsString()
  @IsMoneyString()
  amountCents!: string;

  /** Numéro mobile money qui sera débité (push USSD). */
  @IsString()
  @MinLength(8)
  @MaxLength(20)
  payerPhone!: string;
}
