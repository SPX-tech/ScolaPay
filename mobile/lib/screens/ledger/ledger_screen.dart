import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:uuid/uuid.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/student.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';
import 'package:intl/intl.dart';

final ledgerStudentsProvider = FutureProvider.autoDispose<List<Student>>((ref) {
  final api = ref.watch(apiClientProvider);
  return api.getStudents(status: 'ACTIVE', limit: 100).then((r) {
    final resp = StudentListResponse.fromJson(r.data as Map<String, dynamic>);
    return resp.data;
  });
});

class LedgerScreen extends ConsumerStatefulWidget {
  const LedgerScreen({super.key});

  @override
  ConsumerState<LedgerScreen> createState() => _LedgerScreenState();
}

class _LedgerScreenState extends ConsumerState<LedgerScreen> {
  int _step = 0; // 0: select student, 1: amount, 2: confirm
  Student? _selectedStudent;
  final _amountController = TextEditingController();
  String? _note;
  bool _loading = false;
  Map<String, dynamic>? _result;

  @override
  void initState() {
    super.initState();
    final extra = GoRouterState.of(context).extra;
    if (extra is Map && extra.containsKey('studentId')) {
      // TODO: handle pre-selected student
    }
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _recordPayment() async {
    final amountStr = _amountController.text.replaceAll(' ', '');
    final amount = int.tryParse(amountStr);
    if (amount == null || amount <= 0 || _selectedStudent == null) return;

    setState(() => _loading = true);
    try {
      final api = ref.read(apiClientProvider);
      final response = await api.recordCashPayment(
        studentId: _selectedStudent!.id,
        amountCents: amount * 100,
        idempotencyKey: const Uuid().v4(),
        note: _note,
      );
      setState(() {
        _result = response.data as Map<String, dynamic>;
        _step = 2;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Erreur: ${e.toString()}')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _reset() {
    setState(() {
      _step = 0;
      _selectedStudent = null;
      _amountController.clear();
      _note = null;
      _result = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Encaissement')),
      body: _loading
          ? const Center(child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Enregistrement du paiement...'),
              ],
            ))
          : _buildBody(theme),
    );
  }

  Widget _buildBody(ThemeData theme) {
    // Step indicators
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              _StepIndicator(number: '1', label: 'Élève', active: _step >= 0, done: _step > 0),
              Expanded(child: Divider(color: _step > 0 ? AppTheme.success : Colors.grey.shade300)),
              _StepIndicator(number: '2', label: 'Montant', active: _step >= 1, done: _step > 1),
              Expanded(child: Divider(color: _step > 1 ? AppTheme.success : Colors.grey.shade300)),
              _StepIndicator(number: '3', label: 'Confirmation', active: _step >= 2, done: false),
            ],
          ),
        ),
        Expanded(
          child: _step == 0
              ? _buildStudentSelection(theme)
              : _step == 1
                  ? _buildAmountForm(theme)
                  : _buildConfirmation(theme),
        ),
      ],
    );
  }

  Widget _buildStudentSelection(ThemeData theme) {
    final studentsAsync = ref.watch(ledgerStudentsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text('Sélectionner un élève', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: studentsAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur'),
            data: (students) => students.isEmpty
                ? const EmptyState(icon: Icons.people_outline, title: 'Aucun élève actif')
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: students.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final s = students[index];
                      final selected = _selectedStudent?.id == s.id;
                      return ListTile(
                        selected: selected,
                        selectedTileColor: AppTheme.primary.withOpacity(0.05),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                          side: selected ? BorderSide(color: AppTheme.primary, width: 1.5) : BorderSide.none,
                        ),
                        leading: CircleAvatar(
                          backgroundColor: AppTheme.primary.withOpacity(0.1),
                          child: Text(s.fullName[0].toUpperCase(), style: const TextStyle(color: AppTheme.primary, fontWeight: FontWeight.bold)),
                        ),
                        title: Text(s.fullName),
                        subtitle: Text('${s.studentNumber} • ${s.className ?? '-'}'),
                        trailing: AmountText(cents: s.debtCents, style: TextStyle(
                          fontWeight: FontWeight.w600,
                          color: s.hasDebt ? AppTheme.error : AppTheme.success,
                        )),
                        onTap: () => setState(() => _selectedStudent = s),
                      );
                    },
                  ),
          ),
        ),
        if (_selectedStudent != null)
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => setState(() => _step = 1),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(50),
                  ),
                  child: const Text('Continuer'),
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildAmountForm(ThemeData theme) {
    final debt = _selectedStudent?.debtCents ?? 0;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(_selectedStudent?.fullName ?? '', style: const TextStyle(fontWeight: FontWeight.w600)),
                        Text(_selectedStudent?.studentNumber ?? '', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text('Dette', style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
                      AmountText(cents: debt, style: TextStyle(fontWeight: FontWeight.bold, color: debt > 0 ? AppTheme.error : AppTheme.success)),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text('Montant encaissé', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          TextField(
            controller: _amountController,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              hintText: 'Ex: 50000',
              prefixIcon: const Icon(Icons.money_rounded),
              suffixText: 'FCFA',
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            decoration: const InputDecoration(
              labelText: 'Note (optionnelle)',
              prefixIcon: Icon(Icons.note_rounded),
            ),
            onChanged: (v) => _note = v.isEmpty ? null : v,
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _recordPayment,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.success,
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(50),
              ),
              child: const Text('Enregistrer le paiement'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConfirmation(ThemeData theme) {
    if (_result == null) return const SizedBox();
    final receiptNumber = _result!['receiptNumber'] as String? ?? '';
    final amountCents = int.tryParse(_result!['amountCents']?.toString() ?? '0') ?? 0;
    final newDebt = int.tryParse(_result!['newDebtCents']?.toString() ?? '0') ?? 0;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.check_circle_rounded, color: AppTheme.success, size: 72),
          const SizedBox(height: 16),
          Text('Paiement enregistré !', style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  _ConfRow(label: 'Reçu n°', value: receiptNumber),
                  const Divider(),
                  _ConfRow(label: 'Montant', value: '${(amountCents / 100).toStringAsFixed(0)} XAF'),
                  const Divider(),
                  _ConfRow(label: 'Nouvelle dette', value: '${(newDebt / 100).toStringAsFixed(0)} XAF'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _reset,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primary,
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(50),
              ),
              child: const Text('Nouvel encaissement'),
            ),
          ),
        ],
      ),
    );
  }
}

class _StepIndicator extends StatelessWidget {
  final String number;
  final String label;
  final bool active;
  final bool done;

  const _StepIndicator({required this.number, required this.label, required this.active, required this.done});

  @override
  Widget build(BuildContext context) {
    final color = done ? AppTheme.success : (active ? AppTheme.primary : Colors.grey.shade300);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
          ),
          child: Center(
            child: done
                ? const Icon(Icons.check, color: Colors.white, size: 16)
                : Text(number, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold)),
          ),
        ),
        const SizedBox(height: 4),
        Text(label, style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
      ],
    );
  }
}

class _ConfRow extends StatelessWidget {
  final String label;
  final String value;
  const _ConfRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey.shade600)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
