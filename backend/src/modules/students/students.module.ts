import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { StudentsController } from './students.controller';
import { StudentsService } from './students.service';
import { ClassesController } from './classes.controller';
import { ClassesService } from './classes.service';

@Module({
  imports: [PrismaModule],
  controllers: [StudentsController, ClassesController],
  providers: [StudentsService, ClassesService],
  exports: [StudentsService, ClassesService],
})
export class StudentsModule {}
