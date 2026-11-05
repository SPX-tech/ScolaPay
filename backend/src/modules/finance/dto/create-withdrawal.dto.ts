import { WithdrawalMethod } from '../../../generated/prisma/client';
import { IsEnum, IsInt, IsString, MaxLength, Min } from 'class-validator';

export class CreateWithdrawalDto {
  /** Montant à retirer, en cents (entier strictement positif). */
  @IsInt()
  @Min(1)
  amountCents!: number;

  @IsEnum(WithdrawalMethod)
  method!: WithdrawalMethod;

  /** Destination du retrait : numéro mobile money ou RIB. */
  @IsString()
  @MaxLength(255)
  destination!: string;
}
