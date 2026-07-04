import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { hashPassword } from '../../common/security/password.util';
import { AuditService } from '../audit/audit.service';

@Injectable()
export class UsersService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async listTenantUsers(tenantId: string) {
    return this.prisma.user.findMany({
      where: { tenantId },
      orderBy: { createdAt: 'desc' },
      omit: { passwordHash: true },
    });
  }

  async getUserById(tenantId: string, userId: string) {
    const user = await this.prisma.user.findFirst({
      where: { id: userId, tenantId },
      omit: { passwordHash: true },
    });

    if (!user) {
      throw new NotFoundException('Utilisateur non trouvé.');
    }

    return user;
  }

  async createUser(tenantId: string, actorUserId: string, dto: CreateUserDto) {
    try {
      const user = await this.prisma.user.create({
        data: {
          tenantId,
          email: dto.email.trim().toLowerCase(),
          fullName: dto.fullName.trim(),
          role: dto.role,
          passwordHash: await hashPassword(dto.password),
          phone: dto.phone?.trim(),
          status: 'ACTIVE',
        },
        omit: { passwordHash: true },
      });

      await this.auditService.record({
        tenantId,
        actorUserId,
        action: 'user.create',
        targetType: 'user',
        targetId: user.id,
        after: user,
      });

      return user;
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un utilisateur avec l'email "${dto.email}" existe déjà.`,
        );
      }
      throw error;
    }
  }

  async updateUser(tenantId: string, actorUserId: string, userId: string, dto: UpdateUserDto) {
    const existingUser = await this.getUserById(tenantId, userId);

    try {
      const data: any = {};
      if (dto.email) data.email = dto.email.trim().toLowerCase();
      if (dto.fullName) data.fullName = dto.fullName.trim();
      if (dto.phone !== undefined) data.phone = dto.phone?.trim();
      if (dto.role) data.role = dto.role;
      if (dto.status) data.status = dto.status;
      if (dto.password) data.passwordHash = await hashPassword(dto.password);

      const user = await this.prisma.user.update({
        where: { id: userId },
        data,
        omit: { passwordHash: true },
      });

      await this.auditService.record({
        tenantId,
        actorUserId,
        action: 'user.update',
        targetType: 'user',
        targetId: user.id,
        before: existingUser,
        after: user,
        metadata: { passwordChanged: Boolean(dto.password) },
      });

      return user;
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un utilisateur avec cet email existe déjà.`,
        );
      }
      throw error;
    }
  }

  async suspendUser(tenantId: string, actorUserId: string, userId: string) {
    const existingUser = await this.getUserById(tenantId, userId);
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: { status: 'SUSPENDED' },
      omit: { passwordHash: true },
    });

    await this.auditService.record({
      tenantId,
      actorUserId,
      action: 'user.suspend',
      targetType: 'user',
      targetId: user.id,
      before: existingUser,
      after: user,
    });

    return user;
  }

  async activateUser(tenantId: string, actorUserId: string, userId: string) {
    const existingUser = await this.getUserById(tenantId, userId);
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: { status: 'ACTIVE' },
      omit: { passwordHash: true },
    });

    await this.auditService.record({
      tenantId,
      actorUserId,
      action: 'user.activate',
      targetType: 'user',
      targetId: user.id,
      before: existingUser,
      after: user,
    });

    return user;
  }
}
