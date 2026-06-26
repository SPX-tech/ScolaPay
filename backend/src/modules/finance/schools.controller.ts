import { Body, Controller, Get, Post } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { Roles } from '../../common/decorators/roles.decorator';
import { VirtualAccountService } from './virtual-account.service';
import { WithdrawalsService } from './withdrawals.service';
import { CreateWithdrawalDto } from './dto/create-withdrawal.dto';

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

  /** Initie une demande de retrait (PENDING + blocage des fonds). */
  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  @Post('withdraw')
  requestWithdrawal(
    @ReqContext() context: RequestContext,
    @Body() dto: CreateWithdrawalDto,
  ) {
    return this.withdrawals.initiate(context.tenantId, context.userId, dto);
  }
}
