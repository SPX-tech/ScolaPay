import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { verifyAccessToken } from '../security/access-token.util';
import { PUBLIC_ROUTE_KEY } from '../constants/request-context.constants';

type AuthRequest = Request & {
  user?: {
    sub: string;
    tenantId: string;
    role: string;
  };
};

@Injectable()
export class AuthGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const isPublic = this.reflector.getAllAndOverride<boolean>(
      PUBLIC_ROUTE_KEY,
      [context.getHandler(), context.getClass()],
    );

    if (isPublic) {
      return true;
    }

    const request = context.switchToHttp().getRequest<AuthRequest>();
    const authorizationHeader = request.headers.authorization;
    const authorization =
      typeof authorizationHeader === 'string'
        ? authorizationHeader
        : authorizationHeader?.[0];

    if (!authorization || !authorization.startsWith('Bearer ')) {
      throw new UnauthorizedException('Missing bearer token.');
    }

    const token = authorization.slice(7);
    const secret = process.env.ACCESS_TOKEN_SECRET;

    if (!secret) {
      throw new UnauthorizedException('Auth secret is not configured.');
    }

    try {
      const claims = verifyAccessToken(token, secret);
      request.user = {
        sub: claims.sub,
        tenantId: claims.tenantId,
        role: claims.role,
      };
      return true;
    } catch {
      throw new UnauthorizedException('Invalid or expired bearer token.');
    }
  }
}
