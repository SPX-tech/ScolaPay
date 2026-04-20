import { Controller, Get, Query } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { ListPendingEventsDto } from './dto/list-pending-events.dto';
import { OutboxService } from './outbox.service';

@Controller('outbox')
export class OutboxController {
  constructor(private readonly outboxService: OutboxService) {}

  @Get('pending')
  listPendingEvents(
    @ReqContext() context: RequestContext,
    @Query() query: ListPendingEventsDto,
  ) {
    return this.outboxService.listPendingEvents(context.tenantId, query.limit);
  }
}
