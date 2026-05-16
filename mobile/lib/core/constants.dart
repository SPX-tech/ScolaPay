class AppConstants {
  static const String appName = 'Edu-Cash';
  static const String appTagline = 'Gestion Financière Scolaire';
  static const String country = 'Tchad';
  static const String defaultCurrency = 'XAF';

  // Storage keys
  static const String tokenKey = 'access_token';
  static const String userKey = 'user';
  static const String tenantKey = 'tenant';
  static const String baseUrlKey = 'API_BASE_URL';

  // Pagination
  static const int defaultPageSize = 20;
  static const int maxPageSize = 100;

  // Timeouts
  static const int connectTimeout = 10000;
  static const int receiveTimeout = 15000;

  // API
  static const String defaultApiUrl = 'http://10.0.2.2:3000/api/v1';
}
