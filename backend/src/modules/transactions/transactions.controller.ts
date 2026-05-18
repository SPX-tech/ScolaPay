import {
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { Roles } from '../../common/decorators/roles.decorator';
import { TransactionsService } from './transactions.service';
import { ListTransactionsQueryDto } from './dto/list-transactions-query.dto';
import { VoidTransactionDto } from './dto/void-transaction.dto';

@Controller('transactions')
export class TransactionsController {
  constructor(private readonly transactionsService: TransactionsService) {}

  @Get()
  listTransactions(
    @ReqContext() context: RequestContext,
    @Query() query: ListTransactionsQueryDto,
  ) {
    return this.transactionsService.listTenantTransactions(context.tenantId, query);
  }

  @Get('student/:studentId')
  listStudentTransactions(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
    @Query() query: ListTransactionsQueryDto,
  ) {
    return this.transactionsService.listStudentTransactions(
      context.tenantId,
      studentId,
      query,
    );
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

  @Roles('TENANT_ADMIN', 'ACCOUNTANT')
  @Post(':transactionId/void')
  voidTransaction(
    @ReqContext() context: RequestContext,
    @Param('transactionId') transactionId: string,
    @Body() dto: VoidTransactionDto,
  ) {
    return this.transactionsService.voidTransaction(
      context.tenantId,
      transactionId,
      context.userId,
      context.userRole,
      dto,
    );
  }
}
