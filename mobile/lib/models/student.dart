class Student {
  final String id;
  final String tenantId;
  final String? classId;
  final String studentNumber;
  final String fullName;
  final String? guardianPhone;
  final String? guardianName;
  final String status;
  final int debtCents;
  final int creditCents;
  final DateTime? enrolledAt;
  final DateTime? lastPaymentAt;
  final String? className;

  Student({
    required this.id,
    required this.tenantId,
    this.classId,
    required this.studentNumber,
    required this.fullName,
    this.guardianPhone,
    this.guardianName,
    required this.status,
    required this.debtCents,
    required this.creditCents,
    this.enrolledAt,
    this.lastPaymentAt,
    this.className,
  });

  factory Student.fromJson(Map<String, dynamic> json) {
    return Student(
      id: json['id'] as String,
      tenantId: json['tenantId'] as String,
      classId: json['classId'] as String?,
      studentNumber: json['studentNumber'] as String,
      fullName: json['fullName'] as String,
      guardianPhone: json['guardianPhone'] as String?,
      guardianName: json['guardianName'] as String?,
      status: json['status'] as String,
      debtCents: _parseInt(json['debtCents']),
      creditCents: _parseInt(json['creditCents']),
      enrolledAt: json['enrolledAt'] != null ? DateTime.parse(json['enrolledAt'] as String) : null,
      lastPaymentAt: json['lastPaymentAt'] != null ? DateTime.parse(json['lastPaymentAt'] as String) : null,
      className: json['class'] is Map ? (json['class'] as Map)['name'] as String? : json['className'] as String?,
    );
  }

  static int _parseInt(dynamic value) {
    if (value is int) return value;
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  String get debtFormatted => '${(debtCents / 100).toStringAsFixed(0)} XAF';
  String get creditFormatted => '${(creditCents / 100).toStringAsFixed(0)} XAF';

  bool get hasDebt => debtCents > 0;
  bool get hasCredit => creditCents > 0;
  bool get isActive => status == 'ACTIVE';
}

class StudentListResponse {
  final List<Student> data;
  final int total;
  final int page;
  final int limit;

  StudentListResponse({
    required this.data,
    required this.total,
    required this.page,
    required this.limit,
  });

  factory StudentListResponse.fromJson(Map<String, dynamic> json) {
    return StudentListResponse(
      data: (json['data'] as List<dynamic>?)?.map((e) => Student.fromJson(e as Map<String, dynamic>)).toList() ?? [],
      total: json['total'] as int? ?? 0,
      page: json['page'] as int? ?? 1,
      limit: json['limit'] as int? ?? 20,
    );
  }

  bool get hasMore => page * limit < total;
}
