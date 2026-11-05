import { Test, TestingModule } from '@nestjs/testing';
import { ConflictException, NotFoundException } from '@nestjs/common';
import { StudentsService } from './students.service';
import { PrismaService } from '../../prisma/prisma.service';
import { AuditService } from '../audit/audit.service';

describe('StudentsService', () => {
  let service: StudentsService;
  let prisma: any;

  const mockPrisma = {
    student: {
      create: jest.fn(),
      findMany: jest.fn(),
      findFirst: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
      count: jest.fn(),
    },
    schoolClass: {
      findFirst: jest.fn(),
    },
  };
  const mockAuditService = {
    record: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        StudentsService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: AuditService, useValue: mockAuditService },
      ],
    }).compile();

    service = module.get<StudentsService>(StudentsService);
    prisma = module.get(PrismaService);
    jest.clearAllMocks();
  });

  const tenantId = 'tenant-1';
  const actorUserId = 'user-1';
  const studentId = 'student-1';

  const baseDto = {
    studentNumber: 'ETU001',
    fullName: 'Jean Dupont',
    guardianPhone: '+23599112233',
    guardianName: 'Pierre Dupont',
    classId: 'class-1',
    initialDebtCents: '50000',
  };

  const baseStudent = {
    id: studentId,
    tenantId,
    studentNumber: 'ETU001',
    fullName: 'Jean Dupont',
    guardianPhone: '+23599112233',
    guardianName: 'Pierre Dupont',
    status: 'ACTIVE',
    debtCents: BigInt(50000),
    creditCents: BigInt(0),
    classId: 'class-1',
    class: { id: 'class-1', name: '6ème A', academicYear: '2024-2025' },
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  describe('createStudent', () => {
    it('should create a student successfully', async () => {
      mockPrisma.student.create.mockResolvedValue(baseStudent);

      const result = await service.createStudent(
        tenantId,
        actorUserId,
        baseDto,
      );

      expect(result).toEqual(baseStudent);
      expect(mockPrisma.student.create).toHaveBeenCalledWith({
        data: {
          tenantId,
          studentNumber: 'ETU001',
          fullName: 'Jean Dupont',
          guardianPhone: '+23599112233',
          guardianName: 'Pierre Dupont',
          classId: 'class-1',
          status: 'ACTIVE',
          debtCents: BigInt(50000),
          metadata: undefined,
        },
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'student.create',
          targetId: studentId,
          actorUserId,
        }),
      );
    });

    it('should throw ConflictException on duplicate studentNumber', async () => {
      const error = { code: 'P2002' };
      mockPrisma.student.create.mockRejectedValue(error);

      await expect(
        service.createStudent(tenantId, actorUserId, baseDto),
      ).rejects.toThrow(ConflictException);
    });

    it('should create a student without optional fields', async () => {
      const minimalDto = {
        studentNumber: 'ETU002',
        fullName: 'Marie Diallo',
      };

      mockPrisma.student.create.mockResolvedValue({
        ...baseStudent,
        studentNumber: 'ETU002',
        fullName: 'Marie Diallo',
        guardianPhone: null,
        guardianName: null,
        classId: null,
        class: null,
      });

      const result = await service.createStudent(
        tenantId,
        actorUserId,
        minimalDto,
      );

      expect(result.fullName).toBe('Marie Diallo');
      expect(mockPrisma.student.create).toHaveBeenCalledWith({
        data: {
          tenantId,
          studentNumber: 'ETU002',
          fullName: 'Marie Diallo',
          guardianPhone: undefined,
          guardianName: undefined,
          classId: undefined,
          status: 'ACTIVE',
          debtCents: 0n,
          metadata: undefined,
        },
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
    });
  });

  describe('listStudents', () => {
    it('should return paginated students', async () => {
      mockPrisma.student.findMany.mockResolvedValue([baseStudent]);
      mockPrisma.student.count.mockResolvedValue(1);

      const result = await service.listStudents(tenantId, {
        page: 1,
        limit: 20,
      });

      expect(result.data).toHaveLength(1);
      expect(result.meta.total).toBe(1);
      expect(result.meta.page).toBe(1);
      expect(result.meta.totalPages).toBe(1);
    });

    it('should filter by search query', async () => {
      mockPrisma.student.findMany.mockResolvedValue([baseStudent]);
      mockPrisma.student.count.mockResolvedValue(1);

      await service.listStudents(tenantId, { search: 'Jean' });

      expect(mockPrisma.student.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            OR: [
              { fullName: { contains: 'Jean', mode: 'insensitive' } },
              { studentNumber: { contains: 'Jean', mode: 'insensitive' } },
            ],
          }),
        }),
      );
    });

    it('should filter by status', async () => {
      mockPrisma.student.findMany.mockResolvedValue([]);
      mockPrisma.student.count.mockResolvedValue(0);

      await service.listStudents(tenantId, { status: 'ACTIVE' });

      expect(mockPrisma.student.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ status: 'ACTIVE' }),
        }),
      );
    });

    it('should handle pagination correctly', async () => {
      mockPrisma.student.findMany.mockResolvedValue([]);
      mockPrisma.student.count.mockResolvedValue(50);

      const result = await service.listStudents(tenantId, {
        page: 3,
        limit: 20,
      });

      expect(result.meta.page).toBe(3);
      expect(result.meta.totalPages).toBe(3);
      expect(mockPrisma.student.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ skip: 40, take: 20 }),
      );
    });
  });

  describe('getStudentById', () => {
    it('should return a student by id', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);

      const result = await service.getStudentById(tenantId, studentId);

      expect(result).toEqual(baseStudent);
      expect(mockPrisma.student.findFirst).toHaveBeenCalledWith({
        where: { id: studentId, tenantId },
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
    });

    it('should throw NotFoundException if student not found', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(null);

      await expect(
        service.getStudentById(tenantId, 'nonexistent'),
      ).rejects.toThrow(NotFoundException);
    });
  });

  describe('updateStudent', () => {
    it('should update a student successfully', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);
      const updatedStudent = {
        ...baseStudent,
        fullName: 'Jean Dupont Modifié',
      };
      mockPrisma.student.update.mockResolvedValue(updatedStudent);

      const result = await service.updateStudent(
        tenantId,
        actorUserId,
        studentId,
        {
          fullName: 'Jean Dupont Modifié',
        },
      );

      expect(result.fullName).toBe('Jean Dupont Modifié');
    });

    it('should throw NotFoundException if student does not exist', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(null);

      await expect(
        service.updateStudent(tenantId, actorUserId, 'nonexistent', {
          fullName: 'Test',
        }),
      ).rejects.toThrow(NotFoundException);
    });

    it('should throw ConflictException on duplicate studentNumber', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);
      const error = { code: 'P2002' };
      mockPrisma.student.update.mockRejectedValue(error);

      await expect(
        service.updateStudent(tenantId, actorUserId, studentId, {
          studentNumber: 'DUPLICATE',
        }),
      ).rejects.toThrow(ConflictException);
    });
  });

  describe('deleteStudent', () => {
    it('should archive a student successfully', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);
      const archivedStudent = { ...baseStudent, status: 'SUSPENDED' };
      mockPrisma.student.update.mockResolvedValue(archivedStudent);

      const result = await service.deleteStudent(
        tenantId,
        actorUserId,
        studentId,
      );

      expect(result).toEqual(archivedStudent);
      expect(mockPrisma.student.update).toHaveBeenCalledWith({
        where: { id: studentId },
        data: { status: 'SUSPENDED' },
        include: {
          class: { select: { id: true, name: true, academicYear: true } },
        },
      });
      expect(mockPrisma.student.delete).not.toHaveBeenCalled();
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'student.archive',
          targetId: studentId,
          actorUserId,
        }),
      );
    });

    it('should throw NotFoundException if student does not exist', async () => {
      mockPrisma.student.findFirst.mockResolvedValue(null);

      await expect(
        service.deleteStudent(tenantId, actorUserId, 'nonexistent'),
      ).rejects.toThrow(NotFoundException);
    });
  });

  describe('importStudents', () => {
    it('should import students successfully', async () => {
      const importDto = {
        students: [
          {
            studentNumber: 'IMP001',
            fullName: 'Import 1',
            className: '6ème A',
          },
          { studentNumber: 'IMP002', fullName: 'Import 2' },
        ],
      };

      mockPrisma.schoolClass.findFirst.mockResolvedValue({
        id: 'class-1',
        name: '6ème A',
      });
      mockPrisma.student.create
        .mockResolvedValueOnce({ id: 'new-1' })
        .mockResolvedValueOnce({ id: 'new-2' });

      const result = await service.importStudents(
        tenantId,
        actorUserId,
        importDto,
      );

      expect(result.success).toBe(2);
      expect(result.errors).toHaveLength(0);
      expect(mockPrisma.student.create).toHaveBeenCalledTimes(2);
    });

    it('should report errors for failed imports', async () => {
      const importDto = {
        students: [
          { studentNumber: 'V001', fullName: 'Valid' },
          { studentNumber: 'E001', fullName: 'Error' },
        ],
      };

      mockPrisma.student.create
        .mockResolvedValueOnce({ id: 'ok' })
        .mockRejectedValueOnce(new Error('DB error'));

      const result = await service.importStudents(
        tenantId,
        actorUserId,
        importDto,
      );

      expect(result.success).toBe(1);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].row).toBe(2);
    });

    it('should resolve class name to ID when provided', async () => {
      mockPrisma.schoolClass.findFirst.mockResolvedValue({
        id: 'found-class-id',
        name: '5ème B',
      });
      mockPrisma.student.create.mockResolvedValue({ id: 'new-1' });

      await service.importStudents(tenantId, actorUserId, {
        students: [
          {
            studentNumber: 'C001',
            fullName: 'Class Test',
            className: '5ème B',
          },
        ],
      });

      expect(mockPrisma.schoolClass.findFirst).toHaveBeenCalledWith({
        where: { tenantId, name: '5ème B', status: 'ACTIVE' },
      });
      expect(mockPrisma.student.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ classId: 'found-class-id' }),
        }),
      );
    });
  });
});
