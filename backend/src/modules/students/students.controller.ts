import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import { RequestContext } from '../../common/types/request-context.type';
import { CreateStudentDto } from './dto/create-student.dto';
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
  listStudents(@ReqContext() context: RequestContext) {
    return this.studentsService.listStudents(context.tenantId);
  }

  @Get(':studentId')
  getStudentById(
    @ReqContext() context: RequestContext,
    @Param('studentId') studentId: string,
  ) {
    return this.studentsService.getStudentById(context.tenantId, studentId);
  }
}
