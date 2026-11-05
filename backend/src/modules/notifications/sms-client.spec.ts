import createSmsClient from './sms-client';

describe('Transport SMS borné', () => {
  const result = { SMSMessageData: { Recipients: [{ messageId: 'test-id', statusCode: 101 }] } };
  let transport: jest.SpiedFunction<typeof fetch>;

  beforeEach(() => { transport = jest.spyOn(globalThis, 'fetch'); });
  afterEach(() => { transport.mockRestore(); });

  it('conserve le contrat du fournisseur et sépare le sandbox', async () => {
    transport.mockResolvedValue(new Response(JSON.stringify(result)));
    const client = createSmsClient({ apiKey: 'test-key', username: 'sandbox' });
    await expect(client.SMS.send({ to: ['+23566000000'], message: 'Reçu de test' })).resolves.toEqual(result);
    const [url, options] = transport.mock.calls[0];
    expect(url).toBe('https://api.sandbox.africastalking.com/version1/messaging');
    expect(options?.redirect).toBe('error');
    expect(options?.signal).toBeInstanceOf(AbortSignal);
    expect(new URLSearchParams(options?.body as URLSearchParams).get('to')).toBe('+23566000000');
  });

  it('refuse une réponse trop grande avant son analyse JSON', async () => {
    transport.mockResolvedValue(new Response('x'.repeat(65_537)));
    await expect(createSmsClient({ apiKey: 'test-key', username: 'sandbox' }).SMS.send({ to: ['+23566000000'], message: 'Test' })).rejects.toThrow('too large');
  });

  it('ne confond pas un rejet du fournisseur avec un SMS envoyé', async () => {
    transport.mockResolvedValue(new Response(JSON.stringify({ SMSMessageData: { Recipients: [{ messageId: 'test-id', statusCode: 401 }] } })));
    await expect(createSmsClient({ apiKey: 'test-key', username: 'sandbox' }).SMS.send({ to: ['+23566000000'], message: 'Test' })).rejects.toThrow('rejected');
  });
});
