declare module 'africastalking' {
  interface SMSOptions {
    to: string[];
    message: string;
    from?: string;
  }

  interface SMSResponse {
    SMSMessageData: {
      Message: string;
      Recipients: Array<{
        messageId: string;
        number: string;
        cost: string;
        status: string;
      }>;
    };
  }

  interface AfricastalkingInstance {
    SMS: {
      send: (options: SMSOptions) => Promise<SMSResponse>;
    };
  }

  interface AfricastalkingConfig {
    apiKey: string;
    username: string;
  }

  function africastalking(config: AfricastalkingConfig): AfricastalkingInstance;
  export default africastalking;
}
