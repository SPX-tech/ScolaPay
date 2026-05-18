import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { CreateStudentDto } from './dto/create-student.dto';
import { UpdateStudentDto } from './dto/update-student.dto';
import { ListStudentsQueryDto } from './dto/list-students-query.dto';
import { ImportStudentsDto } from './dto/import-students.dto';
import { StudentsService } from './students.service';

@Controller('students')
export class StudentsController {
  constructor(private readonly studentsService: StudentsService) {}

  @Post()
  createStudent(
    @ReqContext() context: RequestContext,
    @Body() dto: CreateStudentDto,
  ) {
    return this.studentsService.createStudent(context.tenantId, dto);
  }

  @Get()
  listStudents(
    @ReqContext() context: RequestContext,
    @Query() query: ListStudentsQueryDto,
  ) {
    return this.studentsService.listStudents(context.tenantId, query);
  }

  @Get(':studentId')
  getStudentById(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
  ) {
    return this.studentsService.getStudentById(context.tenantId, studentId);
  }

  @Patch(':studentId')
  updateStudent(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
    @Body() dto: UpdateStudentDto,
  ) {
    return this.studentsService.updateStudent(context.tenantId, studentId, dto);
  }

  @Delete(':studentId')
  deleteStudent(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
  ) {
    return this.studentsService.deleteStudent(context.tenantId, studentId);
  }

  @Post('import')
  importStudents(
    @ReqContext() context: RequestContext,
    @Body() dto: ImportStudentsDto,
  ) {
    return this.studentsService.importStudents(context.tenantId, dto);
  }
}
