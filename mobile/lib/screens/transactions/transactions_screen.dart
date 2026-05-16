import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/transaction.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';
import 'package:intl/intl.dart';

final transactionsProvider = FutureProvider.autoDispose.family<TransactionListResponse, _TxQuery>((ref, query) {
  final api = ref.watch(apiClientProvider);
  return api.getTransactions(page: query.page, limit: 20, type: query.type, status: query.status, startDate: query.startDate, endDate: query.endDate)
      .then((r) => TransactionListResponse.fromJson(r.data as Map<String, dynamic>));
});

class _TxQuery {
  final int page;
  final String? type;
  final String? status;
  final String? startDate;
  final String? endDate;
  const _TxQuery({this.page = 1, this.type, this.status, this.startDate, this.endDate});
}

class TransactionsScreen extends ConsumerStatefulWidget {
  const TransactionsScreen({super.key});

  @override
  ConsumerState<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends ConsumerState<TransactionsScreen> {
  String? _typeFilter;
  String? _statusFilter;
  DateTime? _startDate;
  DateTime? _endDate;
  int _page = 1;

  Future<void> _pickDateRange() async {
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      locale: const Locale('fr', 'FR'),
      builder: (context, child) => Theme(
        data: Theme.of(context).copyWith(
          colorScheme: Theme.of(context).colorScheme.copyWith(primary: AppTheme.primary),
        ),
        child: child!,
      ),
    );
    if (range != null) {
      setState(() {
        _startDate = range.start;
        _endDate = range.end;
        _page = 1;
      });
    }
  }

  void _clearFilters() {
    setState(() {
      _typeFilter = null;
      _statusFilter = null;
      _startDate = null;
      _endDate = null;
      _page = 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy-MM-dd');
    final query = _TxQuery(
      page: _page,
      type: _typeFilter,
      status: _statusFilter,
      startDate: _startDate != null ? dateFormat.format(_startDate!) : null,
      endDate: _endDate != null ? dateFormat.format(_endDate!) : null,
    );
    final txAsync = ref.watch(transactionsProvider(query));
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Transactions')),
      body: Column(
        children: [
          // Filters
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: Column(
              children: [
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _FilterChip2(label: 'Tous', selected: _typeFilter == null, onTap: () => setState(() { _typeFilter = null; _page = 1; })),
                      const SizedBox(width: 6),
                      _FilterChip2(label: 'Encaissements', selected: _typeFilter == 'CASH_PAYMENT', onTap: () => setState(() { _typeFilter = 'CASH_PAYMENT'; _page = 1; })),
                      const SizedBox(width: 6),
                      _FilterChip2(label: 'Validés', selected: _statusFilter == 'POSTED', onTap: () => setState(() { _statusFilter = 'POSTED'; _page = 1; })),
                      const SizedBox(width: 6),
                      _FilterChip2(label: 'Annulés', selected: _statusFilter == 'VOIDED', onTap: () => setState(() { _statusFilter = 'VOIDED'; _page = 1; })),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: _pickDateRange,
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
                              Expanded(
                                child: Text(
                                  _startDate != null && _endDate != null
                                      ? '${DateFormat('dd/MM', 'fr_FR').format(_startDate!)} - ${DateFormat('dd/MM', 'fr_FR').format(_endDate!)}'
                                      : 'Filtrer par date',
                                  style: TextStyle(fontSize: 13, color: _startDate != null ? Colors.black87 : Colors.grey.shade600),
                                ),
                              ),
                              if (_startDate != null)
                                GestureDetector(
                                  onTap: _clearFilters,
                                  child: Icon(Icons.close, size: 16, color: Colors.grey.shade400),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // List
          Expanded(
            child: txAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur', subtitle: err.toString()),
              data: (response) {
                if (response.data.isEmpty) {
                  return const EmptyState(icon: Icons.receipt_long_outlined, title: 'Aucune transaction');
                }
                return RefreshIndicator(
                  onRefresh: () async => ref.invalidate(transactionsProvider(query)),
                  child: ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: response.data.length + (response.hasMore ? 1 : 0),
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      if (index == response.data.length) {
                        return Padding(
                          padding: const EdgeInsets.all(8),
                          child: Center(
                            child: TextButton(
                              onPressed: () => setState(() => _page++),
                              child: const Text('Charger plus'),
                            ),
                          ),
                        );
                      }
                      final tx = response.data[index];
                      return _TransactionCard(transaction: tx);
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterChip2 extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip2({required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? AppTheme.primary : Colors.grey.shade100,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: selected ? Colors.white : Colors.grey.shade700)),
      ),
    );
  }
}

class _TransactionCard extends StatelessWidget {
  final Transaction transaction;
  const _TransactionCard({required this.transaction});

  @override
  Widget build(BuildContext context) {
    final isVoided = transaction.isVoided;
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            CircleAvatar(
              radius: 20,
              backgroundColor: isVoided ? AppTheme.error.withOpacity(0.1) : AppTheme.success.withOpacity(0.1),
              child: Icon(isVoided ? Icons.cancel_outlined : Icons.payments_outlined, color: isVoided ? AppTheme.error : AppTheme.success, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(transaction.studentName, style: TextStyle(fontWeight: FontWeight.w500, decoration: isVoided ? TextDecoration.lineThrough : null)),
                  const SizedBox(height: 2),
                  Text(
                    '${transaction.typeLabel} • ${transaction.receiptNumber}',
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                  Text(
                    DateFormat('dd/MM/yyyy HH:mm', 'fr_FR').format(transaction.recordedAt),
                    style: TextStyle(fontSize: 11, color: Colors.grey.shade400),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                AmountText(cents: transaction.amountCents, style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                  decoration: isVoided ? TextDecoration.lineThrough : null,
                  color: isVoided ? Colors.grey : AppTheme.success,
                )),
                const SizedBox(height: 4),
                StatusBadge(status: transaction.status),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
