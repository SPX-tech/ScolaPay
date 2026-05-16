import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'dart:convert';
import '../core/api_client.dart';
import '../models/user.dart';
import '../core/constants.dart';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

final apiClientProvider = Provider<ApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return ApiClient(storage: storage);
});

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final api = ref.watch(apiClientProvider);
  final storage = ref.watch(secureStorageProvider);
  return AuthNotifier(api: api, storage: storage);
});

enum AuthStatus { initial, loading, authenticated, unauthenticated, error }

class AuthState {
  final AuthStatus status;
  final User? user;
  final String? error;
  final String? token;

  const AuthState({
    this.status = AuthStatus.initial,
    this.user,
    this.error,
    this.token,
  });

  AuthState copyWith({
    AuthStatus? status,
    User? user,
    String? error,
    String? token,
  }) {
    return AuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      error: error ?? this.error,
      token: token ?? this.token,
    );
  }

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isLoading => status == AuthStatus.loading;
}

class AuthNotifier extends StateNotifier<AuthState> {
  final ApiClient api;
  final FlutterSecureStorage storage;

  AuthNotifier({required this.api, required this.storage})
      : super(const AuthState()) {
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    final token = await storage.read(key: AppConstants.tokenKey);
    if (token != null && token.isNotEmpty) {
      final userJson = await storage.read(key: AppConstants.userKey);
      if (userJson != null) {
        final user = User.fromJson(json.decode(userJson) as Map<String, dynamic>);
        state = AuthState(
          status: AuthStatus.authenticated,
          user: user,
          token: token,
        );
        return;
      }
    }
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  Future<void> login(String tenantCode, String email, String password) async {
    state = state.copyWith(status: AuthStatus.loading, error: null);
    try {
      final response = await api.login(tenantCode, email, password);
      final loginResponse = LoginResponse.fromJson(response.data as Map<String, dynamic>);

      await api.saveToken(loginResponse.accessToken);
      await storage.write(
        key: AppConstants.userKey,
        value: json.encode(loginResponse.user.toJson()),
      );

      state = AuthState(
        status: AuthStatus.authenticated,
        user: loginResponse.user,
        token: loginResponse.accessToken,
      );
    } on Exception catch (e) {
      String message = 'Erreur de connexion';
      if (e.toString().contains('401')) {
        message = 'Identifiants incorrects';
      } else if (e.toString().contains('SocketException') || e.toString().contains('Connection refused')) {
        message = 'Impossible de se connecter au serveur';
      }
      state = state.copyWith(status: AuthStatus.error, error: message);
    }
  }

  Future<void> logout() async {
    await api.clearToken();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  void clearError() {
    state = state.copyWith(error: null);
  }
}
