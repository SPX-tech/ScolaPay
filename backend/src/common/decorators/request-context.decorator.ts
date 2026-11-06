import {
  UnauthorizedException,
  createParamDecorator,
  ExecutionContext,
} from '@nestjs/common';
import { Request } from 'express';
import {
  REQUEST_CONTEXT_KEY,
  RequestContext,
} from '../types/request-context.type';

type RequestWithContext = Request & {
  [REQUEST_CONTEXT_KEY]?: RequestContext;
};

export const ReqContext = createParamDecorator(
  (_: unknown, context: ExecutionContext): RequestContext => {
    const request = context.switchToHttp().getRequest<RequestWithContext>();
    if (!request[REQUEST_CONTEXT_KEY]) {
      throw new UnauthorizedException('Request context is not available.');
    }

    return request[REQUEST_CONTEXT_KEY];
  },
);
