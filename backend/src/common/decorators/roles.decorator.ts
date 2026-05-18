import { SetMetadata } from '@nestjs/common';
import { ROLES_KEY } from '../constants/roles.constants';

export type UserRole = 'TENANT_ADMIN' | 'CASHIER' | 'ACCOUNTANT' | 'AUDITOR';

export const Roles = (...roles: UserRole[]) => SetMetadata(ROLES_KEY, roles);
