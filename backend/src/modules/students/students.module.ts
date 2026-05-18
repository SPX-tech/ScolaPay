import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { StudentsController } from './students.controller';
import { StudentsService } from './students.service';
import { ClassesController } from './classes.controller';
import { ClassesService } from './classes.service';

@Module({
  imports: [PrismaModule, AuditModule],
  controllers: [StudentsController, ClassesController],
  providers: [StudentsService, ClassesService],
  exports: [StudentsService, ClassesService],
})
export class StudentsModule {}
