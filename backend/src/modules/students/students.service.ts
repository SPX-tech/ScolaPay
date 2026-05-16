import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateStudentDto } from './dto/create-student.dto';
import { UpdateStudentDto } from './dto/update-student.dto';
import { ListStudentsQueryDto } from './dto/list-students-query.dto';
import { ImportStudentsDto } from './dto/import-students.dto';

@Injectable()
export class StudentsService {
  constructor(private readonly prisma: PrismaService) {}

  async createStudent(tenantId: string, dto: CreateStudentDto) {
    try {
      return await this.prisma.student.create({
        data: {
          tenantId,
          studentNumber: dto.studentNumber.trim(),
          fullName: dto.fullName.trim(),
          guardianPhone: dto.guardianPhone?.trim(),
          guardianName: dto.guardianName?.trim(),
          classId: dto.classId,
          status: 'ACTIVE',
          debtCents: dto.initialDebtCents ? BigInt(dto.initialDebtCents) : 0n,
          metadata: dto.metadata as any,
        },
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un étudiant avec le matricule "${dto.studentNumber}" existe déjà.`,
        );
      }
      throw error;
    }
  }

  async listStudents(tenantId: string, query: ListStudentsQueryDto) {
    const { page = 1, limit = 20, search, status, classId } = query;
    const skip = (page - 1) * limit;

    const where: any = { tenantId };
    if (search) {
      where.OR = [
        { fullName: { contains: search, mode: 'insensitive' } },
        { studentNumber: { contains: search, mode: 'insensitive' } },
      ];
    }
    if (status) where.status = status;
    if (classId) where.classId = classId;

    const [data, total] = await Promise.all([
      this.prisma.student.findMany({
        where,
        skip,
        take: limit,
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.student.count({ where }),
    ]);

    return {
      data,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async getStudentById(tenantId: string, studentId: string) {
    const student = await this.prisma.student.findFirst({
      where: { id: studentId, tenantId },
      include: {
        class: { select: { id: true, name: true, academicYear: true } },
      },
    });

    if (!student) {
      throw new NotFoundException('Étudiant non trouvé.');
    }

    return student;
  }

  async updateStudent(tenantId: string, studentId: string, dto: UpdateStudentDto) {
    await this.getStudentById(tenantId, studentId);

    try {
      const data: any = {};
      if (dto.fullName) data.fullName = dto.fullName.trim();
      if (dto.studentNumber) data.studentNumber = dto.studentNumber.trim();
      if (dto.guardianPhone !== undefined) data.guardianPhone = dto.guardianPhone?.trim();
      if (dto.guardianName !== undefined) data.guardianName = dto.guardianName?.trim();
      if (dto.classId !== undefined) data.classId = dto.classId;
      if (dto.status) data.status = dto.status;
      if (dto.debtCents !== undefined) data.debtCents = BigInt(dto.debtCents);
      if (dto.creditCents !== undefined) data.creditCents = BigInt(dto.creditCents);
      if (dto.metadata !== undefined) data.metadata = dto.metadata as any;

      return await this.prisma.student.update({
        where: { id: studentId },
        data,
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException(
          `Un étudiant avec le matricule "${dto.studentNumber}" existe déjà.`,
        );
      }
      throw error;
    }
  }

  async deleteStudent(tenantId: string, studentId: string) {
    await this.getStudentById(tenantId, studentId);
    return this.prisma.student.delete({ where: { id: studentId } });
  }

  async importStudents(tenantId: string, dto: ImportStudentsDto) {
    const results: { success: number; errors: { row: number; message: string }[] } = {
      success: 0,
      errors: [],
    };

    for (let i = 0; i < dto.students.length; i++) {
      const row = dto.students[i];
      const rowIndex = i + 1;

      try {
        // Resolve class name to ID if provided
        let classId: string | undefined;
        if (row.className) {
          const cls = await this.prisma.schoolClass.findFirst({
            where: { tenantId, name: row.className, status: 'ACTIVE' },
          });
          if (cls) {
            classId = cls.id;
          }
        }

        await this.prisma.student.create({
          data: {
            tenantId,
            studentNumber: String(row.studentNumber || `IMP-${Date.now()}-${rowIndex}`),
            fullName: String(row.fullName || 'Unknown'),
            guardianPhone: row.guardianPhone ? String(row.guardianPhone) : undefined,
            guardianName: row.guardianName ? String(row.guardianName) : undefined,
            classId,
            status: 'ACTIVE',
            debtCents: BigInt(row.debtCents || 0),
            metadata: row.metadata as any,
          },
        });
        results.success++;
      } catch (error: any) {
        results.errors.push({
          row: rowIndex,
          message: error?.message || 'Erreur inconnue',
        });
      }
    }

    return results;
  }
}
