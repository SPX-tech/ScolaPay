import { Injectable } from '@nestjs/common';

@Injectable()
export class NotificationsService {
  async queueCashPaymentNotification(eventPayload: unknown) {
    return {
      status: 'queued',
      provider: 'sms-adapter-placeholder',
      payload: eventPayload,
    };
  }
}
