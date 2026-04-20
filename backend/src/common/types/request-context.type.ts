export const REQUEST_CONTEXT_KEY = 'requestContext';

export type RequestContext = {
  tenantId: string;
  userId: string;
  userRole: string;
};
