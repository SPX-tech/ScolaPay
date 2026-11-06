import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { PUBLIC_ROUTE_KEY } from '../constants/request-context.constants';
import { ROLES_KEY } from '../constants/roles.constants';
import {
  REQUEST_CONTEXT_KEY,
  RequestContext,
} from '../types/request-context.type';
import type { UserRole } from '../decorators/roles.decorator';

type RequestWithContext = Request & {
  [REQUEST_CONTEXT_KEY]?: RequestContext;
};

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const isPublic = this.reflector.getAllAndOverride<boolean>(
      PUBLIC_ROUTE_KEY,
      [context.getHandler(), context.getClass()],
    );

    if (isPublic) {
      return true;
    }

    const allowedRoles = this.reflector.getAllAndOverride<UserRole[]>(
      ROLES_KEY,
      [context.getHandler(), context.getClass()],
    );

    if (!allowedRoles?.length) {
      return true;
    }

    const request = context.switchToHttp().getRequest<RequestWithContext>();
    const userRole = request[REQUEST_CONTEXT_KEY]?.userRole;

    if (!userRole || !allowedRoles.includes(userRole as UserRole)) {
      throw new ForbiddenException('Insufficient role for this action.');
    }

    return true;
  }
}
