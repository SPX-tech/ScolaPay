class DashboardSummary {
  final int totalStudents;
  final int activeStudents;
  final int totalClasses;
  final String totalCollectedCents;
  final String currentMonthCents;
  final int todayTransactions;
  final int pendingVoidRequests;
  final List<RecentTransaction> recentTransactions;

  DashboardSummary({
    required this.totalStudents,
    required this.activeStudents,
    required this.totalClasses,
    required this.totalCollectedCents,
    required this.currentMonthCents,
    required this.todayTransactions,
    required this.pendingVoidRequests,
    required this.recentTransactions,
  });

  factory DashboardSummary.fromJson(Map<String, dynamic> json) {
    return DashboardSummary(
      totalStudents: json['totalStudents'] as int? ?? 0,
      activeStudents: json['activeStudents'] as int? ?? 0,
      totalClasses: json['totalClasses'] as int? ?? 0,
      totalCollectedCents: _parseString(json['totalCollectedCents']),
      currentMonthCents: _parseString(json['currentMonthCents']),
      todayTransactions: json['todayTransactions'] as int? ?? 0,
      pendingVoidRequests: json['pendingVoidRequests'] as int? ?? 0,
      recentTransactions: (json['recentTransactions'] as List<dynamic>?)
              ?.map((e) => RecentTransaction.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  static String _parseString(dynamic value) {
    if (value is String) return value;
    if (value is int) return value.toString();
    return '0';
  }

  String get totalCollectedFormatted => '${(int.tryParse(totalCollectedCents) ?? 0) / 100} XAF';
  String get currentMonthFormatted => '${(int.tryParse(currentMonthCents) ?? 0) / 100} XAF';
}

class RecentTransaction {
  final String id;
  final String studentName;
  final int amountCents;
  final String status;
  final DateTime recordedAt;

  RecentTransaction({
    required this.id,
    required this.studentName,
    required this.amountCents,
    required this.status,
    required this.recordedAt,
  });

  factory RecentTransaction.fromJson(Map<String, dynamic> json) {
    return RecentTransaction(
      id: json['id'] as String,
      studentName: json['studentName'] as String? ?? '',
      amountCents: int.tryParse(json['amountCents']?.toString() ?? '0') ?? 0,
      status: json['status'] as String? ?? '',
      recordedAt: DateTime.parse(json['recordedAt'] as String),
    );
  }
}

class Receipt {
  final String receiptNumber;
  final String receiptHash;
  final String qrCode;
  final Map<String, dynamic> transaction;

  Receipt({
    required this.receiptNumber,
    required this.receiptHash,
    required this.qrCode,
    required this.transaction,
  });

  factory Receipt.fromJson(Map<String, dynamic> json) {
    return Receipt(
      receiptNumber: json['receiptNumber'] as String,
      receiptHash: json['receiptHash'] as String,
      qrCode: json['qrCode'] as String,
      transaction: json['transaction'] as Map<String, dynamic>,
    );
  }
}

class ReportCollection {
  final List<DailyCollection> dailyCollections;
  final List<TopPayer> topPayers;
  final ReportSummary summary;

  ReportCollection({
    required this.dailyCollections,
    required this.topPayers,
    required this.summary,
  });

  factory ReportCollection.fromJson(Map<String, dynamic> json) {
    return ReportCollection(
      dailyCollections: (json['dailyCollections'] as List<dynamic>?)
              ?.map((e) => DailyCollection.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      topPayers: (json['topPayers'] as List<dynamic>?)
              ?.map((e) => TopPayer.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      summary: ReportSummary.fromJson(json['summary'] as Map<String, dynamic>),
    );
  }
}

class DailyCollection {
  final String date;
  final String totalCents;
  final int transactionCount;

  DailyCollection({
    required this.date,
    required this.totalCents,
    required this.transactionCount,
  });

  factory DailyCollection.fromJson(Map<String, dynamic> json) {
    return DailyCollection(
      date: json['date'] as String,
      totalCents: json['totalCents'].toString(),
      transactionCount: json['transactionCount'] as int? ?? 0,
    );
  }
}

class TopPayer {
  final String studentId;
  final String fullName;
  final String studentNumber;
  final String totalCents;
  final int transactionCount;

  TopPayer({
    required this.studentId,
    required this.fullName,
    required this.studentNumber,
    required this.totalCents,
    required this.transactionCount,
  });

  factory TopPayer.fromJson(Map<String, dynamic> json) {
    return TopPayer(
      studentId: json['studentId'] as String,
      fullName: json['fullName'] as String,
      studentNumber: json['studentNumber'] as String,
      totalCents: json['totalCents'].toString(),
      transactionCount: json['transactionCount'] as int? ?? 0,
    );
  }
}

class ReportSummary {
  final String totalCollectedCents;
  final int transactionCount;

  ReportSummary({
    required this.totalCollectedCents,
    required this.transactionCount,
  });

  factory ReportSummary.fromJson(Map<String, dynamic> json) {
    return ReportSummary(
      totalCollectedCents: json['totalCollectedCents'].toString(),
      transactionCount: json['transactionCount'] as int? ?? 0,
    );
  }
}

class DebtStudent {
  final String id;
  final String studentNumber;
  final String fullName;
  final String? className;
  final int debtCents;
  final int creditCents;
  final String? guardianPhone;
  final String status;

  DebtStudent({
    required this.id,
    required this.studentNumber,
    required this.fullName,
    this.className,
    required this.debtCents,
    required this.creditCents,
    this.guardianPhone,
    required this.status,
  });

  factory DebtStudent.fromJson(Map<String, dynamic> json) {
    return DebtStudent(
      id: json['id'] as String,
      studentNumber: json['studentNumber'] as String,
      fullName: json['fullName'] as String,
      className: json['className'] as String?,
      debtCents: int.tryParse(json['debtCents'].toString()) ?? 0,
      creditCents: int.tryParse(json['creditCents'].toString()) ?? 0,
      guardianPhone: json['guardianPhone'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
    );
  }
}
