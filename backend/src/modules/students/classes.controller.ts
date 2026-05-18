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
import { ClassesService } from './classes.service';
import { CreateClassDto } from './dto/create-class.dto';
import { UpdateClassDto } from './dto/update-class.dto';

@Controller('classes')
export class ClassesController {
  constructor(private readonly classesService: ClassesService) {}

  @Post()
  createClass(
    @ReqContext() context: RequestContext,
    @Body() dto: CreateClassDto,
  ) {
    return this.classesService.createClass(context.tenantId, dto);
  }

  @Get()
  listClasses(
    @ReqContext() context: RequestContext,
    @Query('academicYear') academicYear?: string,
  ) {
    return this.classesService.listClasses(context.tenantId, academicYear);
  }

  @Get(':classId')
  getClassById(
    @ReqContext() context: RequestContext,
    @Param('classId') classId: string,
  ) {
    return this.classesService.getClassById(context.tenantId, classId);
  }

  @Patch(':classId')
  updateClass(
    @ReqContext() context: RequestContext,
    @Param('classId') classId: string,
    @Body() dto: UpdateClassDto,
  ) {
    return this.classesService.updateClass(context.tenantId, classId, dto);
  }

  @Delete(':classId')
  deleteClass(
    @ReqContext() context: RequestContext,
    @Param('classId') classId: string,
  ) {
    return this.classesService.deleteClass(context.tenantId, classId);
  }
}
