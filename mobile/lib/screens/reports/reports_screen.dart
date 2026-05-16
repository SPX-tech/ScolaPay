import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/dashboard.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';
import 'package:intl/intl.dart';

final reportsCollectionsProvider = FutureProvider.autoDispose.family<ReportCollection, String?>((ref, dateRange) {
  final api = ref.watch(apiClientProvider);
  final parts = dateRange?.split('|');
  final start = parts?.isNotEmpty == true ? parts![0] : null;
  final end = parts?.length == 2 ? parts[1] : null;
  return api.getCollectionsReport(startDate: start, endDate: end).then((r) => ReportCollection.fromJson(r.data as Map<String, dynamic>));
});

final studentDebtsProvider = FutureProvider.autoDispose<List<DebtStudent>>((ref) {
  final api = ref.watch(apiClientProvider);
  return api.getStudentDebtReport().then((r) {
    final data = r.data as List<dynamic>;
    return data.map((e) => DebtStudent.fromJson(e as Map<String, dynamic>)).toList();
  });
});

class ReportsScreen extends ConsumerStatefulWidget {
  const ReportsScreen({super.key});

  @override
  ConsumerState<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends ConsumerState<ReportsScreen> {
  int _tab = 0;
  DateTime? _startDate;
  DateTime? _endDate;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _startDate = DateTime(now.year, now.month, 1);
    _endDate = now;
  }

  String? get _dateParam {
    if (_startDate == null || _endDate == null) return null;
    final fmt = DateFormat('yyyy-MM-dd');
    return '${fmt.format(_startDate!)}|${fmt.format(_endDate!)}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Rapports')),
      body: Column(
        children: [
          // Tabs
          Container(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(child: _TabButton(label: 'Encaissements', selected: _tab == 0, onTap: () => setState(() => _tab = 0))),
                const SizedBox(width: 8),
                Expanded(child: _TabButton(label: 'Dettes', selected: _tab == 1, onTap: () => setState(() => _tab = 1))),
              ],
            ),
          ),
          Expanded(child: _tab == 0 ? _buildCollectionsTab(theme) : _buildDebtsTab(theme)),
        ],
      ),
    );
  }

  Widget _buildCollectionsTab(ThemeData theme) {
    final collectionsAsync = ref.watch(reportsCollectionsProvider(_dateParam));

    return Column(
      children: [
        // Date picker
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: InkWell(
            onTap: () async {
              final range = await showDateRangePicker(
                context: context,
                firstDate: DateTime(2020),
                lastDate: DateTime.now(),
                locale: const Locale('fr', 'FR'),
              );
              if (range != null) {
                setState(() {
                  _startDate = range.start;
                  _endDate = range.end;
                });
              }
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey.shade300),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  Icon(Icons.date_range_rounded, size: 18, color: AppTheme.primary),
                  const SizedBox(width: 8),
                  Text(
                    _startDate != null && _endDate != null
                        ? '${DateFormat('dd/MM/yyyy', 'fr_FR').format(_startDate!)} - ${DateFormat('dd/MM/yyyy', 'fr_FR').format(_endDate!)}'
                        : 'Sélectionner une période',
                    style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: collectionsAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur'),
            data: (data) {
              if (data.dailyCollections.isEmpty) {
                return const EmptyState(icon: Icons.bar_chart_outlined, title: 'Aucune donnée pour cette période');
              }
              return RefreshIndicator(
                onRefresh: () async => ref.invalidate(reportsCollectionsProvider(_dateParam)),
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    // Summary cards
                    Row(
                      children: [
                        Expanded(
                          child: KpiCard(
                            label: 'Total encaissé',
                            value: '${(int.tryParse(data.summary.totalCollectedCents) ?? 0) / 100} XAF',
                            icon: Icons.account_balance_wallet_rounded,
                            color: AppTheme.success,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: KpiCard(
                            label: 'Transactions',
                            value: data.summary.transactionCount.toString(),
                            icon: Icons.receipt_long_rounded,
                            color: AppTheme.primary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Daily collections
                    SectionHeader(title: 'Encaissements quotidiens'),
                    const SizedBox(height: 8),
                    ...data.dailyCollections.map((d) => Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          children: [
                            Expanded(child: Text(d.date, style: const TextStyle(fontWeight: FontWeight.w500))),
                            AmountText(cents: int.tryParse(d.totalCents) ?? 0),
                            const SizedBox(width: 16),
                            Text('${d.transactionCount} tx', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                          ],
                        ),
                      ),
                    )),
                    const SizedBox(height: 16),

                    // Top payers
                    SectionHeader(title: 'Top payeurs'),
                    const SizedBox(height: 8),
                    ...data.topPayers.take(5).map((p) => Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ListTile(
                        title: Text(p.fullName),
                        subtitle: Text(p.studentNumber),
                        trailing: AmountText(cents: int.tryParse(p.totalCents) ?? 0),
                      ),
                    )),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildDebtsTab(ThemeData theme) {
    final debtsAsync = ref.watch(studentDebtsProvider);

    return debtsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur'),
      data: (students) {
        final debtors = students.where((s) => s.debtCents > 0).toList();
        return RefreshIndicator(
          onRefresh: () async => ref.invalidate(studentDebtsProvider),
          child: debtors.isEmpty
              ? const EmptyState(icon: Icons.check_circle_outline, title: 'Aucun élève en dette', subtitle: 'Tous les comptes sont à jour !')
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Card(
                      color: AppTheme.warning.withOpacity(0.1),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          children: [
                            const Icon(Icons.warning_amber_rounded, color: Colors.orange),
                            const SizedBox(width: 8),
                            Text('${debtors.length} élève(s) en situation de dette', style: const TextStyle(fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    ...debtors.map((s) => Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: AppTheme.error.withOpacity(0.1),
                          child: const Icon(Icons.person_rounded, color: AppTheme.error, size: 20),
                        ),
                        title: Text(s.fullName, style: const TextStyle(fontWeight: FontWeight.w500)),
                        subtitle: Text('${s.studentNumber} • ${s.className ?? '-'}'),
                        trailing: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            AmountText(cents: s.debtCents, style: const TextStyle(color: AppTheme.error, fontWeight: FontWeight.bold)),
                            if (s.guardianPhone != null)
                              Text(s.guardianPhone!, style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
                          ],
                        ),
                      ),
                    )),
                  ],
                ),
        );
      },
    );
  }
}

class _TabButton extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _TabButton({required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected ? AppTheme.primary : Colors.grey.shade100,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Center(
          child: Text(label, style: TextStyle(
            fontWeight: FontWeight.w600,
            color: selected ? Colors.white : Colors.grey.shade700,
          )),
        ),
      ),
    );
  }
}
