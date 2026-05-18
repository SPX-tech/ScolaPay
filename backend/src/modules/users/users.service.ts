import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { hashPassword } from '../../common/security/password.util';

@Injectable()
export class UsersService {
  constructor(private readonly prisma: PrismaService) {}

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

  async createUser(tenantId: string, dto: CreateUserDto) {
    try {
      return await this.prisma.user.create({
        data: {
          tenantId,
          email: dto.email.trim().toLowerCase(),
          fullName: dto.fullName.trim(),
          role: dto.role,
          passwordHash: hashPassword(dto.password),
          phone: dto.phone?.trim(),
          status: 'ACTIVE',
        },
        omit: { passwordHash: true },
      });
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un utilisateur avec l'email "${dto.email}" existe déjà.`,
        );
      }
      throw error;
    }
  }

  async updateUser(tenantId: string, userId: string, dto: UpdateUserDto) {
    await this.getUserById(tenantId, userId);

    try {
      const data: any = {};
      if (dto.email) data.email = dto.email.trim().toLowerCase();
      if (dto.fullName) data.fullName = dto.fullName.trim();
      if (dto.phone !== undefined) data.phone = dto.phone?.trim();
      if (dto.role) data.role = dto.role;
      if (dto.status) data.status = dto.status;
      if (dto.password) data.passwordHash = hashPassword(dto.password);

      return await this.prisma.user.update({
        where: { id: userId },
        data,
        omit: { passwordHash: true },
      });
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un utilisateur avec cet email existe déjà.`,
        );
      }
      throw error;
    }
  }

  async suspendUser(tenantId: string, userId: string) {
    await this.getUserById(tenantId, userId);
    return this.prisma.user.update({
      where: { id: userId },
      data: { status: 'SUSPENDED' },
      omit: { passwordHash: true },
    });
  }

  async activateUser(tenantId: string, userId: string) {
    await this.getUserById(tenantId, userId);
    return this.prisma.user.update({
      where: { id: userId },
      data: { status: 'ACTIVE' },
      omit: { passwordHash: true },
    });
  }
}
