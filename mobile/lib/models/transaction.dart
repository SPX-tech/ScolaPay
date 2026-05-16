class Transaction {
  final String id;
  final String studentId;
  final String studentName;
  final String studentNumber;
  final String recordedByName;
  final String type;
  final String status;
  final int amountCents;
  final int previousDebtCents;
  final int newDebtCents;
  final String currency;
  final String receiptNumber;
  final String? note;
  final DateTime recordedAt;
  final DateTime? voidedAt;
  final String? voidReason;

  Transaction({
    required this.id,
    required this.studentId,
    required this.studentName,
    required this.studentNumber,
    required this.recordedByName,
    required this.type,
    required this.status,
    required this.amountCents,
    required this.previousDebtCents,
    required this.newDebtCents,
    required this.currency,
    required this.receiptNumber,
    this.note,
    required this.recordedAt,
    this.voidedAt,
    this.voidReason,
  });

  factory Transaction.fromJson(Map<String, dynamic> json) {
    final student = json['student'] as Map<String, dynamic>?;
    final recordedBy = json['recordedBy'] as Map<String, dynamic>?;

    return Transaction(
      id: json['id'] as String,
      studentId: json['studentId'] as String,
      studentName: student?['fullName'] as String? ?? '',
      studentNumber: student?['studentNumber'] as String? ?? '',
      recordedByName: recordedBy?['fullName'] as String? ?? '',
      type: json['type'] as String,
      status: json['status'] as String,
      amountCents: _parseInt(json['amountCents']),
      previousDebtCents: _parseInt(json['previousDebtCents']),
      newDebtCents: _parseInt(json['newDebtCents']),
      currency: json['currency'] as String? ?? 'XAF',
      receiptNumber: json['receiptNumber'] as String? ?? '',
      note: json['note'] as String?,
      recordedAt: DateTime.parse(json['recordedAt'] as String),
      voidedAt: json['voidedAt'] != null ? DateTime.parse(json['voidedAt'] as String) : null,
      voidReason: json['voidReason'] as String?,
    );
  }

  static int _parseInt(dynamic value) {
    if (value is int) return value;
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  String get amountFormatted => '${(amountCents / 100).toStringAsFixed(0)} $currency';
  String get previousDebtFormatted => '${(previousDebtCents / 100).toStringAsFixed(0)} $currency';
  String get newDebtFormatted => '${(newDebtCents / 100).toStringAsFixed(0)} $currency';

  bool get isPosted => status == 'POSTED';
  bool get isVoided => status == 'VOIDED';
  bool get isCashPayment => type == 'CASH_PAYMENT';

  String get typeLabel {
    switch (type) {
      case 'CASH_PAYMENT':
        return 'Encaissement';
      case 'DEBT_ADJUSTMENT':
        return 'Ajustement';
      case 'REFUND':
        return 'Remboursement';
      default:
        return type;
    }
  }

  String get statusLabel {
    switch (status) {
      case 'POSTED':
        return 'Validé';
      case 'VOIDED':
        return 'Annulé';
      default:
        return status;
    }
  }

  DateTime get sortDate => recordedAt;
}

class TransactionListResponse {
  final List<Transaction> data;
  final int total;
  final int page;
  final int limit;

  TransactionListResponse({
    required this.data,
    required this.total,
    required this.page,
    required this.limit,
  });

  factory TransactionListResponse.fromJson(Map<String, dynamic> json) {
    return TransactionListResponse(
      data: (json['data'] as List<dynamic>?)?.map((e) => Transaction.fromJson(e as Map<String, dynamic>)).toList() ?? [],
      total: json['total'] as int? ?? 0,
      page: json['page'] as int? ?? 1,
      limit: json['limit'] as int? ?? 20,
    );
  }

  bool get hasMore => page * limit < total;
}
