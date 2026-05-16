class User {
  final String id;
  final String tenantId;
  final String email;
  final String? phone;
  final String fullName;
  final String role;
  final String status;

  User({
    required this.id,
    required this.tenantId,
    required this.email,
    this.phone,
    required this.fullName,
    required this.role,
    required this.status,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] as String,
      tenantId: json['tenantId'] as String,
      email: json['email'] as String,
      phone: json['phone'] as String?,
      fullName: json['fullName'] as String,
      role: json['role'] as String,
      status: json['status'] as String,
    );
  }

  Map<String, dynamic> toJson() => {
        'fullName': fullName,
        'email': email,
        'phone': phone,
        'role': role,
      };

  String get roleLabel {
    switch (role) {
      case 'TENANT_ADMIN':
        return 'Administrateur';
      case 'CASHIER':
        return 'Caissier';
      case 'ACCOUNTANT':
        return 'Comptable';
      case 'AUDITOR':
        return 'Auditeur';
      default:
        return role;
    }
  }
}

class LoginResponse {
  final String accessToken;
  final int expiresInSeconds;
  final User user;

  LoginResponse({
    required this.accessToken,
    required this.expiresInSeconds,
    required this.user,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      accessToken: json['accessToken'] as String,
      expiresInSeconds: json['expiresInSeconds'] as int,
      user: User.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}
