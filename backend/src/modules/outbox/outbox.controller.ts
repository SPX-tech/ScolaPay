import { Controller, Get, Post, Query } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { OutboxService } from './outbox.service';
import { OutboxWorkerService } from './outbox-worker.service';
import { ListPendingEventsDto } from './dto/list-pending-events.dto';

@Controller('outbox')
export class OutboxController {
  constructor(
    private readonly outboxService: OutboxService,
    private readonly outboxWorkerService: OutboxWorkerService,
  ) {}

  @Get('pending')
  listPendingEvents(
    @ReqContext() context: RequestContext,
    @Query() query: ListPendingEventsDto,
  ) {
    return this.outboxService.listPendingEvents(context.tenantId, query.limit);
  }

  @Post('process')
  async processPendingEvents() {
    return this.outboxWorkerService.processPendingEvents();
  }
}
