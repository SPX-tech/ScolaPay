import {
  Injectable,
  InternalServerErrorException,
  UnauthorizedException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { LoginDto } from './dto/login.dto';
import { signAccessToken } from '../../common/security/access-token.util';
import {
  hashPassword,
  verifyPassword,
} from '../../common/security/password.util';

@Injectable()
export class AuthService {
  constructor(private readonly prisma: PrismaService) {}

  async login(dto: LoginDto) {
    const tenantCode = dto.tenantCode.trim().toUpperCase();
    const email = dto.email.trim().toLowerCase();

    const user = await this.prisma.user.findFirst({
      where: {
        tenant: {
          code: tenantCode,
          isActive: true,
        },
        email,
        status: 'ACTIVE',
      },
    });

    if (!user) {
      throw new UnauthorizedException('Invalid credentials.');
    }

    if (!verifyPassword(dto.password, user.passwordHash)) {
      throw new UnauthorizedException('Invalid credentials.');
    }

    const isLegacyPasswordHash = !user.passwordHash.startsWith('scrypt$');

    const secret = process.env.ACCESS_TOKEN_SECRET;
    if (!secret) {
      throw new InternalServerErrorException(
        'ACCESS_TOKEN_SECRET is not configured.',
      );
    }

    const expiresInSeconds = Number(
      process.env.ACCESS_TOKEN_EXPIRY_SECONDS ?? 60 * 60 * 8,
    );
    const accessToken = signAccessToken(
      {
        sub: user.id,
        tenantId: user.tenantId,
        role: user.role,
      },
      secret,
      expiresInSeconds,
    );

    await this.prisma.user.update({
      where: { id: user.id },
      data: {
        lastLoginAt: new Date(),
        ...(isLegacyPasswordHash
          ? { passwordHash: hashPassword(dto.password) }
          : {}),
      },
    });

    return {
      accessToken,
      expiresInSeconds,
      user: {
        id: user.id,
        tenantId: user.tenantId,
        role: user.role,
        fullName: user.fullName,
        email: user.email,
      },
    };
  }
}
