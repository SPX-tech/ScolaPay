import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateClassDto } from './dto/create-class.dto';
import { UpdateClassDto } from './dto/update-class.dto';
import { Prisma } from '../../generated/prisma/client';

@Injectable()
export class ClassesService {
  constructor(private readonly prisma: PrismaService) {}

  private async validateClass(tenantId: string, classId: string) {
    const cls = await this.prisma.schoolClass.findFirst({
      where: { id: classId, tenantId },
      include: { _count: { select: { students: true } } },
    });

    if (!cls) {
      throw new NotFoundException('Classe non trouvée.');
    }

    return cls;
  }

  async createClass(tenantId: string, dto: CreateClassDto) {
    try {
      return await this.prisma.schoolClass.create({
        data: {
          tenantId,
          name: dto.name.trim(),
          description: dto.description?.trim(),
          academicYear: dto.academicYear.trim(),
          status: 'ACTIVE',
        },
      });
    } catch (error: unknown) {
      if (
        error &&
        typeof error === 'object' &&
        'code' in error &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(
          `Une classe "${dto.name}" existe déjà pour l'année ${dto.academicYear}.`,
        );
      }
      throw error;
    }
  }

  async listClasses(tenantId: string, academicYear?: string) {
    const where: Prisma.SchoolClassWhereInput = { tenantId };
    if (academicYear) where.academicYear = academicYear;

    return this.prisma.schoolClass.findMany({
      where,
      include: { _count: { select: { students: true } } },
      orderBy: [{ academicYear: 'desc' }, { name: 'asc' }],
    });
  }

  async getClassById(tenantId: string, classId: string) {
    return this.validateClass(tenantId, classId);
  }

  async updateClass(tenantId: string, classId: string, dto: UpdateClassDto) {
    await this.validateClass(tenantId, classId);

    try {
      const data: Prisma.SchoolClassUpdateInput = {};
      if (dto.name) data.name = dto.name.trim();
      if (dto.description !== undefined)
        data.description = dto.description?.trim();
      if (dto.academicYear) data.academicYear = dto.academicYear.trim();
      if (dto.status) data.status = dto.status;

      return await this.prisma.schoolClass.update({
        where: { id: classId },
        data,
        include: { _count: { select: { students: true } } },
      });
    } catch (error: unknown) {
      if (
        error &&
        typeof error === 'object' &&
        'code' in error &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(
          `Une classe "${dto.name}" existe déjà pour cette année académique.`,
        );
      }
      throw error;
    }
  }

  async deleteClass(tenantId: string, classId: string) {
    await this.validateClass(tenantId, classId);
    return this.prisma.schoolClass.delete({ where: { id: classId } });
  }
}
