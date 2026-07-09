import { Test, TestingModule } from '@nestjs/testing';
import { UnauthorizedException } from '@nestjs/common';
import { createHash } from 'node:crypto';
import { AuthService } from './auth.service';
import { PrismaService } from '../../prisma/prisma.service';
import { hashPassword } from '../../common/security/password.util';

// Chaque test enchaîne un hash et une comparaison bcrypt (coût 12, JS pur) :
// ~2 s cumulées, davantage sous les workers Jest parallèles.
jest.setTimeout(60_000);

describe('AuthService', () => {
  let service: AuthService;

  const mockPrisma = {
    user: {
      findFirst: jest.fn(),
      update: jest.fn(),
    },
  };

  const baseUser = {
    id: 'u1',
    tenantId: 't1',
    role: 'TENANT_ADMIN',
    fullName: 'Admin Démo',
    email: 'admin@demo.td',
    status: 'ACTIVE',
  };

  const dto = { tenantCode: 'demo001', email: 'Admin@Demo.td', password: 'correct-horse' };

  beforeAll(() => {
    process.env.ACCESS_TOKEN_SECRET = 'test-secret-please-change';
  });

  beforeEach(async () => {
    jest.clearAllMocks();
    mockPrisma.user.update.mockResolvedValue({});

    const moduleRef: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = moduleRef.get(AuthService);
  });

  it('connecte un utilisateur avec un hash bcrypt sans le re-hacher', async () => {
    const passwordHash = await hashPassword('correct-horse');
    mockPrisma.user.findFirst.mockResolvedValue({ ...baseUser, passwordHash });

    const result = await service.login(dto);

    expect(result.accessToken).toEqual(expect.any(String));
    expect(result.user).toMatchObject({ id: 'u1', email: 'admin@demo.td', role: 'TENANT_ADMIN' });

    expect(mockPrisma.user.update).toHaveBeenCalledTimes(1);
    const updateData = mockPrisma.user.update.mock.calls[0][0].data;
    expect(updateData.lastLoginAt).toBeInstanceOf(Date);
    // Déjà en bcrypt : aucun re-hachage.
    expect(updateData.passwordHash).toBeUndefined();
  });

  it('migre un ancien hash SHA-256 vers bcrypt au login réussi', async () => {
    const legacyHash = createHash('sha256').update('correct-horse').digest('hex');
    mockPrisma.user.findFirst.mockResolvedValue({ ...baseUser, passwordHash: legacyHash });

    await service.login(dto);

    const updateData = mockPrisma.user.update.mock.calls[0][0].data;
    expect(updateData.passwordHash).toMatch(/^\$2[aby]\$/);
  });

  it('rejette un mot de passe invalide (401) sans rien mettre à jour', async () => {
    const passwordHash = await hashPassword('correct-horse');
    mockPrisma.user.findFirst.mockResolvedValue({ ...baseUser, passwordHash });

    await expect(service.login({ ...dto, password: 'wrong' })).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect(mockPrisma.user.update).not.toHaveBeenCalled();
  });

  it('rejette un utilisateur inconnu (401)', async () => {
    mockPrisma.user.findFirst.mockResolvedValue(null);

    await expect(service.login(dto)).rejects.toBeInstanceOf(UnauthorizedException);
    expect(mockPrisma.user.update).not.toHaveBeenCalled();
  });
});
