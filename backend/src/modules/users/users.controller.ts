import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
} from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { Roles } from '../../common/decorators/roles.decorator';
import { UsersService } from './users.service';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';

@Controller('users')
@Roles('TENANT_ADMIN')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get()
  listUsers(@ReqContext() context: RequestContext) {
    return this.usersService.listTenantUsers(context.tenantId);
  }

  @Get(':userId')
  getUserById(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
  ) {
    return this.usersService.getUserById(context.tenantId, userId);
  }

  @Post()
  createUser(
    @ReqContext() context: RequestContext,
    @Body() dto: CreateUserDto,
  ) {
    return this.usersService.createUser(context.tenantId, context.userId, dto);
  }

  @Patch(':userId')
  updateUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
    @Body() dto: UpdateUserDto,
  ) {
    return this.usersService.updateUser(context.tenantId, context.userId, userId, dto);
  }

  @Post(':userId/suspend')
  suspendUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
  ) {
    return this.usersService.suspendUser(context.tenantId, context.userId, userId);
  }

  @Post(':userId/activate')
  activateUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
  ) {
    return this.usersService.activateUser(context.tenantId, context.userId, userId);
  }
}
