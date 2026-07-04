import { Controller, Get } from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { Public } from '../../common/decorators/public.decorator';

// Sonde de santé (load balancer / monitoring) : jamais rate-limitée.
@SkipThrottle()
@Controller('health')
export class HealthController {
  @Public()
  @Get()
  getHealth() {
    return {
      status: 'ok',
      service: 'scolapay-backend',
      timestamp: new Date().toISOString(),
    };
  }
}
