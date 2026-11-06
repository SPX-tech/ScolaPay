type SmsInput = { to: string[]; message: string; from?: string };
type SmsResult = {
  SMSMessageData: { Recipients: { messageId: string; statusCode: number }[] };
};

function isSmsResult(
  value: unknown,
  recipientCount: number,
): value is SmsResult {
  if (!value || typeof value !== 'object' || !('SMSMessageData' in value))
    return false;
  const data = value.SMSMessageData;
  if (
    !data ||
    typeof data !== 'object' ||
    !('Recipients' in data) ||
    !Array.isArray(data.Recipients)
  )
    return false;
  return (
    data.Recipients.length === recipientCount &&
    data.Recipients.every(
      (entry: unknown) =>
        !!entry &&
        typeof entry === 'object' &&
        'messageId' in entry &&
        typeof entry.messageId === 'string' &&
        'statusCode' in entry &&
        entry.statusCode === 101,
    )
  );
}

/** Transport limité à l'API SMS existante, sans les autres produits du SDK. */
export default function createSmsClient(options: {
  apiKey: string;
  username: string;
}) {
  return {
    SMS: {
      async send(input: SmsInput): Promise<SmsResult> {
        if (
          input.to.length < 1 ||
          input.to.length > 100 ||
          input.to.some((phone) => !/^(?:\+|00)[1-9]\d{7,14}$/.test(phone)) ||
          input.message.length > 4096
        ) {
          throw new Error('Invalid SMS request.');
        }
        const body = new URLSearchParams({
          username: options.username,
          to: input.to.join(','),
          message: input.message,
          bulkSMSMode: '1',
        });
        if (input.from) body.set('from', input.from);
        const host =
          options.username === 'sandbox'
            ? 'api.sandbox.africastalking.com'
            : 'api.africastalking.com';
        const response = await fetch(`https://${host}/version1/messaging`, {
          method: 'POST',
          headers: {
            apiKey: options.apiKey,
            Accept: 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body,
          redirect: 'error',
          signal: AbortSignal.timeout(10_000),
        });
        if (response.status !== 201 || !response.body) {
          await response.body?.cancel();
          throw new Error('SMS provider unavailable.');
        }
        const reader = response.body.getReader();
        const chunks: Uint8Array[] = [];
        let size = 0;
        try {
          for (;;) {
            const chunk = await reader.read();
            if (chunk.done) break;
            size += chunk.value.byteLength;
            if (size > 65_536) {
              await reader.cancel();
              throw new Error('SMS provider response too large.');
            }
            chunks.push(chunk.value);
          }
        } finally {
          reader.releaseLock();
        }
        let result: unknown;
        try {
          result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        } catch {
          throw new Error('Invalid SMS provider response.');
        }
        if (!isSmsResult(result, input.to.length))
          throw new Error('SMS provider rejected the message.');
        return result;
      },
    },
  };
}
