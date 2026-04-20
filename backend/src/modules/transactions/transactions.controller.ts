import { Controller, Get, Param } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { TransactionsService } from './transactions.service';

@Controller('transactions')
export class TransactionsController {
  constructor(private readonly transactionsService: TransactionsService) {}

  @Get()
  listTransactions(@ReqContext() context: RequestContext) {
    return this.transactionsService.listTenantTransactions(context.tenantId);
  }

  @Get('student/:studentId')
  listStudentTransactions(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
  ) {
    return this.transactionsService.listStudentTransactions(context.tenantId, studentId);
  }

  @Get(':transactionId')
  getTransactionById(
    @ReqContext() context: RequestContext,
    @Param('transactionId') transactionId: string,
  ) {
    return this.transactionsService.getTenantTransactionById(
      context.tenantId,
      transactionId,
    );
  }
}
