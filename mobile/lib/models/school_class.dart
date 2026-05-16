class SchoolClass {
  final String id;
  final String name;
  final String? description;
  final String academicYear;
  final String status;
  final int studentCount;
  final DateTime createdAt;

  SchoolClass({
    required this.id,
    required this.name,
    this.description,
    required this.academicYear,
    required this.status,
    this.studentCount = 0,
    required this.createdAt,
  });

  factory SchoolClass.fromJson(Map<String, dynamic> json) {
    return SchoolClass(
      id: json['id'] as String,
      name: json['name'] as String,
      description: json['description'] as String?,
      academicYear: json['academicYear'] as String? ?? '',
      status: json['status'] as String? ?? 'ACTIVE',
      studentCount: json['_count'] is Map ? (json['_count'] as Map)['students'] as int? ?? 0 : (json['studentCount'] as int? ?? 0),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

class Tenant {
  final String id;
  final String code;
  final String name;
  final String currency;
  final String timezone;

  Tenant({
    required this.id,
    required this.code,
    required this.name,
    required this.currency,
    required this.timezone,
  });

  factory Tenant.fromJson(Map<String, dynamic> json) {
    return Tenant(
      id: json['id'] as String,
      code: json['code'] as String,
      name: json['name'] as String,
      currency: json['currency'] as String? ?? 'XAF',
      timezone: json['timezone'] as String? ?? 'Africa/Ndjamena',
    );
  }
}
