import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  static const String _baseUrlKey = 'API_BASE_URL';
  static const String _tokenKey = 'access_token';

  final Dio _dio;
  final FlutterSecureStorage _storage;
  String? _baseUrl;

  ApiClient({
    required FlutterSecureStorage storage,
    String? baseUrl,
  })  : _storage = storage,
        _dio = Dio() {
    _baseUrl = baseUrl;

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (_baseUrl != null) {
            options.baseUrl = _baseUrl!;
          } else {
            options.baseUrl = 'http://localhost:3000/api/v1';
          }

          final token = await getToken();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          options.headers['Content-Type'] = 'application/json';
          handler.next(options);
        },
        onError: (error, handler) async {
          if (error.response?.statusCode == 401) {
            await _storage.delete(key: _tokenKey);
            await _storage.delete(key: 'user');
          }
          handler.next(error);
        },
      ),
    );
  }

  Future<String?> getToken() => _storage.read(key: _tokenKey);

  Future<void> saveToken(String token) =>
      _storage.write(key: _tokenKey, value: token);

  Future<void> clearToken() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: 'user');
  }

  Future<void> setBaseUrl(String url) async {
    _baseUrl = url;
    await _storage.write(key: _baseUrlKey, value: url);
  }

  Future<String> loadBaseUrl() async {
    final url = await _storage.read(key: _baseUrlKey);
    if (url != null) _baseUrl = url;
    return _baseUrl ?? 'http://localhost:3000/api/v1';
  }

  // Auth
  Future<Response> login(String tenantCode, String email, String password) =>
      _dio.post('/auth/login', data: {
        'tenantCode': tenantCode,
        'email': email,
        'password': password,
      });

  // Students
  Future<Response> getStudents({int page = 1, int limit = 20, String? search, String? status, String? classId}) =>
      _dio.get('/students', queryParameters: {
        'page': page,
        'limit': limit,
        if (search != null) 'search': search,
        if (status != null) 'status': status,
        if (classId != null) 'classId': classId,
      });

  Future<Response> getStudent(String id) => _dio.get('/students/$id');

  Future<Response> createStudent(Map<String, dynamic> data) =>
      _dio.post('/students', data: data);

  Future<Response> updateStudent(String id, Map<String, dynamic> data) =>
      _dio.patch('/students/$id', data: data);

  Future<Response> deleteStudent(String id) => _dio.delete('/students/$id');

  Future<Response> importStudents(List<Map<String, dynamic>> students) =>
      _dio.post('/students/import', data: {'students': students});

  // Classes
  Future<Response> getClasses({String? academicYear}) =>
      _dio.get('/classes', queryParameters: {
        if (academicYear != null) 'academicYear': academicYear,
      });

  Future<Response> createClass(Map<String, dynamic> data) =>
      _dio.post('/classes', data: data);

  Future<Response> updateClass(String id, Map<String, dynamic> data) =>
      _dio.patch('/classes/$id', data: data);

  Future<Response> deleteClass(String id) => _dio.delete('/classes/$id');

  // Ledger
  Future<Response> recordCashPayment({
    required String studentId,
    required int amountCents,
    String? idempotencyKey,
    String? note,
  }) =>
      _dio.post('/ledger/record-cash-payment', data: {
        'studentId': studentId,
        'amountCents': amountCents,
        if (idempotencyKey != null) 'idempotencyKey': idempotencyKey,
        if (note != null) 'note': note,
      });

  Future<Response> getReceipt(String transactionId) =>
      _dio.get('/ledger/receipts/$transactionId');

  // Transactions
  Future<Response> getTransactions({
    int page = 1,
    int limit = 20,
    String? type,
    String? status,
    String? studentId,
    String? startDate,
    String? endDate,
  }) =>
      _dio.get('/transactions', queryParameters: {
        'page': page,
        'limit': limit,
        if (type != null) 'type': type,
        if (status != null) 'status': status,
        if (studentId != null) 'studentId': studentId,
        if (startDate != null) 'startDate': startDate,
        if (endDate != null) 'endDate': endDate,
      });

  Future<Response> getStudentTransactions(String studentId,
          {int page = 1, int limit = 20}) =>
      _dio.get('/transactions/student/$studentId', queryParameters: {
        'page': page,
        'limit': limit,
      });

  Future<Response> voidTransaction(String id, String reason) =>
      _dio.post('/transactions/$id/void', data: {'reason': reason});

  // Dashboard
  Future<Response> getDashboardSummary() => _dio.get('/dashboard/summary');

  // Users
  Future<Response> getUsers() => _dio.get('/users');

  Future<Response> createUser(Map<String, dynamic> data) =>
      _dio.post('/users', data: data);

  Future<Response> updateUser(String id, Map<String, dynamic> data) =>
      _dio.patch('/users/$id', data: data);

  Future<Response> suspendUser(String id) => _dio.post('/users/$id/suspend');

  Future<Response> activateUser(String id) => _dio.post('/users/$id/activate');

  // Reports
  Future<Response> getCollectionsReport(
          {String? startDate, String? endDate}) =>
      _dio.get('/dashboard/reports/collections', queryParameters: {
        if (startDate != null) 'startDate': startDate,
        if (endDate != null) 'endDate': endDate,
      });

  Future<Response> getStudentDebtReport() =>
      _dio.get('/dashboard/reports/students-debt');

  // Tenant
  Future<Response> getTenant() => _dio.get('/tenants/me');

  // Outbox
  Future<Response> getPendingOutboxEvents() => _dio.get('/outbox/pending');

  Future<Response> processOutboxEvents() => _dio.post('/outbox/process');
}
