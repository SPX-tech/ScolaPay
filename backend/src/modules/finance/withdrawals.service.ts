import { randomUUID } from 'node:crypto';
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { VirtualAccountService } from './virtual-account.service';
import { CreateWithdrawalDto } from './dto/create-withdrawal.dto';

@Injectable()
export class WithdrawalsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly virtualAccount: VirtualAccountService,
  ) {}

  /**
   * Initie une demande de retrait :
   *  1) bloque les fonds sur le compte virtuel (échoue si disponible insuffisant) ;
   *  2) crée la demande en statut PENDING.
   * Les deux opérations sont dans une même transaction ACID : pas de blocage
   * de fonds sans demande, ni de demande sans blocage.
   *
   * Aucun appel à un prestataire externe n'est effectué ici (rails locaux).
   */
  async initiate(tenantId: string, userId: string, dto: CreateWithdrawalDto) {
    const amountCents = BigInt(dto.amountCents);
    const reference = this.generateReference();

    return this.prisma.$transaction(async (tx) => {
      await this.virtualAccount.reserveForWithdrawal(tenantId, amountCents, tx);

      return tx.withdrawalRequest.create({
        data: {
          tenantId,
          amountCents,
          method: dto.method,
          destination: dto.destination,
          reference,
          requestedById: userId,
          // status PENDING par défaut (schéma)
        },
      });
    });
  }

  /** Référence globalement unique pour la réconciliation externe. */
  private generateReference(): string {
    return `WD-${randomUUID()}`;
  }
}
