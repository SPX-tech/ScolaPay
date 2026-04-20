import { Controller, Get } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { TenantsService } from './tenants.service';

@Controller('tenants')
export class TenantsController {
  constructor(private readonly tenantsService: TenantsService) {}

  @Get('me')
  getCurrentTenant(@ReqContext() context: RequestContext) {
    return this.tenantsService.getTenantById(context.tenantId);
  }
}
