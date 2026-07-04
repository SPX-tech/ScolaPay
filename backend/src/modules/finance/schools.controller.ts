import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { Roles } from '../../common/decorators/roles.decorator';
import { VirtualAccountService } from './virtual-account.service';
import { WithdrawalsService } from './withdrawals.service';
import { CreateWithdrawalDto } from './dto/create-withdrawal.dto';
import { CompleteWithdrawalDto } from './dto/complete-withdrawal.dto';
import { FailWithdrawalDto } from './dto/fail-withdrawal.dto';
import { PaginationQueryDto } from './dto/pagination-query.dto';

@Controller('schools')
export class SchoolsController {
  constructor(
    private readonly virtualAccount: VirtualAccountService,
    private readonly withdrawals: WithdrawalsService,
  ) {}

  /** Solde du compte virtuel de l'école (balance, bloqué, disponible). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT', 'AUDITOR')
  @Get('balance')
  getBalance(@ReqContext() context: RequestContext) {
    return this.virtualAccount.getBalance(context.tenantId);
  }

  /** Historique paginé des demandes de retrait du tenant. */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT', 'AUDITOR')
  @Get('withdrawals')
  listWithdrawals(
    @ReqContext() context: RequestContext,
    @Query() query: PaginationQueryDto,
  ) {
    return this.withdrawals.list(context.tenantId, query);
  }

  /** Historique paginé du grand livre (LedgerEntry) du tenant. */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT', 'AUDITOR')
  @Get('ledger')
  listLedgerEntries(
    @ReqContext() context: RequestContext,
    @Query() query: PaginationQueryDto,
  ) {
    return this.virtualAccount.listLedgerEntries(context.tenantId, query);
  }

  /** Initie une demande de retrait (PENDING + blocage des fonds). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  // Endpoint financier sensible (sortie d'argent) : 10 req / min / IP.
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('withdraw')
  requestWithdrawal(
    @ReqContext() context: RequestContext,
    @Body() dto: CreateWithdrawalDto,
  ) {
    return this.withdrawals.initiate(context.tenantId, context.userId, dto);
  }

  /** Fait passer un retrait de PENDING à PROCESSING (prise en charge). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  @Post('withdrawals/:id/process')
  processWithdrawal(
    @ReqContext() context: RequestContext,
    @Param('id') id: string,
  ) {
    return this.withdrawals.process(context.tenantId, context.userId, id);
  }

  /** Fait passer un retrait de PROCESSING à COMPLETED (débit définitif). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  @Post('withdrawals/:id/complete')
  completeWithdrawal(
    @ReqContext() context: RequestContext,
    @Param('id') id: string,
    @Body() dto: CompleteWithdrawalDto,
  ) {
    return this.withdrawals.complete(context.tenantId, context.userId, id, dto);
  }

  /** Fait passer un retrait de PROCESSING à FAILED (libération du blocage). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  @Post('withdrawals/:id/fail')
  failWithdrawal(
    @ReqContext() context: RequestContext,
    @Param('id') id: string,
    @Body() dto: FailWithdrawalDto,
  ) {
    return this.withdrawals.fail(context.tenantId, context.userId, id, dto);
  }
}
