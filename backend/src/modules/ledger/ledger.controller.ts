import { Body, Controller, Post } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { RecordCashPaymentDto } from './dto/record-cash-payment.dto';
import { LedgerService } from './ledger.service';

@Controller('ledger')
export class LedgerController {
  constructor(private readonly ledgerService: LedgerService) {}

  @Post('record-cash-payment')
  recordCashPayment(
    @ReqContext() context: RequestContext,
    @Body() dto: RecordCashPaymentDto,
  ) {
    return this.ledgerService.recordCashPayment(context, dto);
  }
}
