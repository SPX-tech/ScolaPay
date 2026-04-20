import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateStudentDto } from './dto/create-student.dto';

@Injectable()
export class StudentsService {
  constructor(private readonly prisma: PrismaService) {}

  async createStudent(tenantId: string, dto: CreateStudentDto) {
    const studentNumber = dto.studentNumber.trim().toUpperCase();

    try {
      return await this.prisma.student.create({
        data: {
          tenantId,
          studentNumber,
          fullName: dto.fullName.trim(),
          guardianPhone: dto.guardianPhone?.trim(),
        },
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException('Student number already exists for this tenant.');
      }

      throw error;
    }
  }

  async listStudents(tenantId: string) {
    return this.prisma.student.findMany({
      where: { tenantId },
      orderBy: {
        createdAt: 'desc',
      },
    });
  }

  async getStudentById(tenantId: string, studentId: string) {
    const student = await this.prisma.student.findFirst({
      where: {
        id: studentId,
        tenantId,
      },
    });

    if (!student) {
      throw new NotFoundException('Student not found.');
    }

    return student;
  }
}
