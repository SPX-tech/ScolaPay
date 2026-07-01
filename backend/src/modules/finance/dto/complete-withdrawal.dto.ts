import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class CompleteWithdrawalDto {
  /** Référence du virement/dépôt mobile money côté prestataire externe. */
  @IsString()
  @IsNotEmpty()
  @MaxLength(255)
  externalReference!: string;
}
