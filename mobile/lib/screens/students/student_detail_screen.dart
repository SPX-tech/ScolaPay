import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/student.dart';
import '../../models/transaction.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';
import 'package:intl/intl.dart';

final studentDetailProvider = FutureProvider.autoDispose.family<Student, String>((ref, id) {
  final api = ref.watch(apiClientProvider);
  return api.getStudent(id).then((r) => Student.fromJson(r.data as Map<String, dynamic>));
});

final studentTransactionsProvider = FutureProvider.autoDispose.family<List<Transaction>, String>((ref, studentId) {
  final api = ref.watch(apiClientProvider);
  return api.getStudentTransactions(studentId).then((r) {
    final resp = TransactionListResponse.fromJson(r.data as Map<String, dynamic>);
    return resp.data;
  });
});

class StudentDetailScreen extends ConsumerWidget {
  final String studentId;
  const StudentDetailScreen({super.key, required this.studentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final studentAsync = ref.watch(studentDetailProvider(studentId));
    final txAsync = ref.watch(studentTransactionsProvider(studentId));
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Fiche élève')),
      body: studentAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur', subtitle: err.toString()),
        data: (student) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(studentDetailProvider(studentId));
            ref.invalidate(studentTransactionsProvider(studentId));
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Avatar + header
                Center(
                  child: Column(
                    children: [
                      CircleAvatar(
                        radius: 36,
                        backgroundColor: AppTheme.primary.withOpacity(0.1),
                        child: Text(
                          student.fullName.isNotEmpty ? student.fullName[0].toUpperCase() : '?',
                          style: const TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: AppTheme.primary),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(student.fullName, style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      Text(student.studentNumber, style: TextStyle(fontSize: 14, color: Colors.grey.shade600)),
                      const SizedBox(height: 8),
                      StatusBadge(status: student.status),
                    ],
                  ),
                ),
                const SizedBox(height: 24),

                // Financial summary
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            children: [
                              Text('Dette', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                              const SizedBox(height: 4),
                              Text(
                                student.debtFormatted,
                                style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: student.hasDebt ? AppTheme.error : AppTheme.success,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Container(width: 1, height: 40, color: Colors.grey.shade200),
                        Expanded(
                          child: Column(
                            children: [
                              Text('Crédit', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                              const SizedBox(height: 4),
                              Text(
                                student.creditFormatted,
                                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.success),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Info
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Informations', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                        const SizedBox(height: 12),
                        _InfoRow(label: 'Classe', value: student.className ?? '-'),
                        _InfoRow(label: 'Tuteur', value: student.guardianName ?? '-'),
                        _InfoRow(label: 'Contact tuteur', value: student.guardianPhone ?? '-'),
                        if (student.enrolledAt != null) _InfoRow(label: 'Inscrit le', value: DateFormat('dd/MM/yyyy', 'fr_FR').format(student.enrolledAt!)),
                        if (student.lastPaymentAt != null) _InfoRow(label: 'Dernier paiement', value: DateFormat('dd/MM/yyyy', 'fr_FR').format(student.lastPaymentAt!)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Actions
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: () => context.go('/ledger', extra: {'studentId': studentId}),
                        icon: const Icon(Icons.payments_rounded),
                        label: const Text('Encaisser'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppTheme.success,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Transactions history
                SectionHeader(title: 'Historique des transactions'),
                const SizedBox(height: 8),
                txAsync.when(
                  loading: () => const Center(child: Padding(
                    padding: EdgeInsets.all(20),
                    child: CircularProgressIndicator(),
                  )),
                  error: (err, stack) => const EmptyState(icon: Icons.error_outline, title: 'Erreur'),
                  data: (transactions) {
                    if (transactions.isEmpty) {
                      return const EmptyState(icon: Icons.receipt_long_outlined, title: 'Aucune transaction');
                    }
                    return Column(
                      children: transactions.map((tx) => TransactionTile(
                        studentName: student.fullName,
                        type: tx.type,
                        status: tx.status,
                        amountCents: tx.amountCents,
                        date: tx.recordedAt,
                      )).toList(),
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(fontSize: 14, color: Colors.grey.shade600)),
          Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}
