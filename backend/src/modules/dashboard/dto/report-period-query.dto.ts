import { IsOptional, IsString } from 'class-validator';

/** Le format et l'ordre des dates sont contrôlés par `resolveReportPeriod`. */
export class ReportPeriodQueryDto {
  @IsOptional()
  @IsString()
  startDate?: string;

  @IsOptional()
  @IsString()
  endDate?: string;
}
