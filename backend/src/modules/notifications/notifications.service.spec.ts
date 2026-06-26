import { NotificationsService } from './notifications.service';

// Mock du SDK Africa's Talking pour valider la bascule mock <-> réel
// sans dépendre d'un vrai compte / réseau.
const mockSend = jest.fn();
const mockFactory = jest.fn(() => ({ SMS: { send: mockSend } }));

// L'accès à mockFactory est différé dans une arrow afin d'éviter le TDZ :
// la factory de jest.mock est hissée au-dessus des const ci-dessus.
jest.mock('africastalking', () => ({
  __esModule: true,
  default: (config: unknown) => mockFactory(config),
}));

describe('NotificationsService', () => {
  const basePayload = {
    transactionId: 'tx_1',
    tenantId: 'tenant_1',
    studentId: 'stu_1',
    studentName: 'Jean Dupont',
    guardianName: 'Pierre Dupont',
    guardianPhone: '+23599110001',
    amountCents: '50000',
    receiptNumber: 'RCP-001',
    recordedAt: '2026-06-26T10:00:00.000Z',
  };

  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    jest.clearAllMocks();
    // Repartir d'un environnement propre à chaque test
    process.env = { ...ORIGINAL_ENV };
    delete process.env.AT_API_KEY;
    delete process.env.AT_USERNAME;
    delete process.env.AT_SENDER_ID;
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  describe('mode mock (aucune clé API)', () => {
    it("simule le SMS et ne touche pas au SDK Africa's Talking", async () => {
      const service = new NotificationsService();

      const result = await service.queueCashPaymentNotification(basePayload);

      expect(result.status).toBe('queued');
      expect(result.provider).toBe('mock-sms');
      expect(mockFactory).not.toHaveBeenCalled();
      expect(mockSend).not.toHaveBeenCalled();
    });
  });

  describe('mode réel (AT_API_KEY configuré)', () => {
    beforeEach(() => {
      process.env.AT_API_KEY = 'test-api-key';
      process.env.AT_USERNAME = 'sandbox';
    });

    it('initialise le SDK et envoie un vrai SMS au tuteur', async () => {
      mockSend.mockResolvedValue({
        SMSMessageData: { Recipients: [{ messageId: 'msg_123' }] },
      });
      const service = new NotificationsService();

      const result = await service.queueCashPaymentNotification(basePayload);

      expect(mockFactory).toHaveBeenCalledWith(
        expect.objectContaining({
          apiKey: 'test-api-key',
          username: 'sandbox',
        }),
      );
      expect(mockSend).toHaveBeenCalledWith(
        expect.objectContaining({ to: [basePayload.guardianPhone] }),
      );
      expect(result.status).toBe('sent');
      expect(result.provider).toBe('africastalking');
    });

    it("retourne un statut failed si l'envoi échoue", async () => {
      mockSend.mockRejectedValue(new Error('network down'));
      const service = new NotificationsService();

      const result = await service.queueCashPaymentNotification(basePayload);

      expect(result.status).toBe('failed');
      expect(result.provider).toBe('africastalking');
    });

    it("retombe sur le mock si le tuteur n'a pas de téléphone", async () => {
      const service = new NotificationsService();

      const result = await service.queueCashPaymentNotification({
        ...basePayload,
        guardianPhone: undefined,
      });

      expect(mockSend).not.toHaveBeenCalled();
      expect(result.provider).toBe('mock-sms');
    });

    it("ne réinitialise le SDK qu'une seule fois (memoïsation)", async () => {
      mockSend.mockResolvedValue({
        SMSMessageData: { Recipients: [{ messageId: 'msg_123' }] },
      });
      const service = new NotificationsService();

      await service.queueCashPaymentNotification(basePayload);
      await service.queueCashPaymentNotification(basePayload);

      expect(mockFactory).toHaveBeenCalledTimes(1);
    });
  });
});
