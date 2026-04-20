import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { PUBLIC_ROUTE_KEY } from '../constants/request-context.constants';
import { REQUEST_CONTEXT_KEY, RequestContext as RequestContextType } from '../types/request-context.type';

type RequestWithUser = Request & {
  user?: {
    sub?: string;
    tenantId?: string;
    role?: string;
  };
  headers: Request['headers'] & {
    'x-tenant-id'?: string;
  };
  [REQUEST_CONTEXT_KEY]?: RequestContextType;
};

@Injectable()
export class TenantGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const isPublic = this.reflector.getAllAndOverride<boolean>(PUBLIC_ROUTE_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (isPublic) {
      return true;
    }

    const request = context.switchToHttp().getRequest<RequestWithUser>();

    const tenantHeader = request.headers['x-tenant-id'];
    const tenantIdFromHeader =
      typeof tenantHeader === 'string' ? tenantHeader : tenantHeader?.[0];
    const tenantIdFromToken = request.user?.tenantId;

    if (tenantIdFromToken && tenantIdFromHeader && tenantIdFromToken !== tenantIdFromHeader) {
      throw new UnauthorizedException('Tenant mismatch between token and header.');
    }

    const tenantId = tenantIdFromToken ?? tenantIdFromHeader;

    if (!tenantId) {
      throw new UnauthorizedException('Tenant context is missing.');
    }

    const userId = request.user?.sub;
    const userRole = request.user?.role;

    if (!userId || !userRole) {
      throw new UnauthorizedException('User context is missing.');
    }

    request[REQUEST_CONTEXT_KEY] = {
      tenantId,
      userId,
      userRole,
    };

    return true;
  }
}
