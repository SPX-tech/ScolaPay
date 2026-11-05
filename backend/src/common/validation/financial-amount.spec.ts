import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { RecordCashPaymentDto } from '../../modules/ledger/dto/record-cash-payment.dto';
import { CreateStudentDto } from '../../modules/students/dto/create-student.dto';
import { CreateWithdrawalDto } from '../../modules/finance/dto/create-withdrawal.dto';

describe('Validation des montants financiers', () => {
  it.each(['0', '-1', '1.5', '1e3', '9223372036854775808', '9'.repeat(1000)])(
    'refuse le paiement invalide %s',
    async (amountCents) => {
      const dto = plainToInstance(RecordCashPaymentDto, { studentId: 'c'.repeat(25), amountCents });
      expect(await validate(dto)).not.toHaveLength(0);
    },
  );

  it.each(['0', '9007199254740993', '9223372036854775807'])(
    'accepte une dette initiale exacte %s',
    async (initialDebtCents) => {
      const dto = plainToInstance(CreateStudentDto, { studentNumber: 'ST001', fullName: 'Élève Test', initialDebtCents });
      expect(await validate(dto)).toHaveLength(0);
    },
  );

  it.each(['-1', '1.5', 'abc', '9223372036854775808'])(
    'refuse la dette initiale invalide %s',
    async (initialDebtCents) => {
      const dto = plainToInstance(CreateStudentDto, { studentNumber: 'ST001', fullName: 'Élève Test', initialDebtCents });
      expect(await validate(dto)).not.toHaveLength(0);
    },
  );

  it('refuse les retraits numériques dont la précision est déjà perdue', async () => {
    const dto = plainToInstance(CreateWithdrawalDto, { amountCents: 9007199254740992, method: 'MOBILE_MONEY', destination: '+23566000000' });
    expect(await validate(dto)).not.toHaveLength(0);
  });
});
