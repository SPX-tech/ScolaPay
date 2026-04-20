import { Controller, Get } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { UsersService } from './users.service';

@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get()
  listUsers(@ReqContext() context: RequestContext) {
    return this.usersService.listTenantUsers(context.tenantId);
  }
}
