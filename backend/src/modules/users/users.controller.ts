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
import { UsersService } from './users.service';
import type { CreateUserDto } from './dto/create-user.dto';
import type { UpdateUserDto } from './dto/update-user.dto';

@Controller('users')
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
    return this.usersService.createUser(context.tenantId, dto);
  }

  @Patch(':userId')
  updateUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
    @Body() dto: UpdateUserDto,
  ) {
    return this.usersService.updateUser(context.tenantId, userId, dto);
  }

  @Post(':userId/suspend')
  suspendUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
  ) {
    return this.usersService.suspendUser(context.tenantId, userId);
  }

  @Post(':userId/activate')
  activateUser(
    @ReqContext() context: RequestContext,
    @Param('userId') userId: string,
  ) {
    return this.usersService.activateUser(context.tenantId, userId);
  }
}
