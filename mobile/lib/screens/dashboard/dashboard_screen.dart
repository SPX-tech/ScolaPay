import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/dashboard.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';
import 'package:intl/intl.dart';

final dashboardProvider = FutureProvider.autoDispose<DashboardSummary>((ref) {
  final api = ref.watch(apiClientProvider);
  final response = api.getDashboardSummary();
  return response.then((r) => DashboardSummary.fromJson(r.data as Map<String, dynamic>));
});

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardAsync = ref.watch(dashboardProvider);
    final theme = Theme.of(context);

    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(dashboardProvider),
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Text(
              'Tableau de bord',
              style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            Text(
              DateFormat('EEEE d MMMM yyyy', 'fr_FR').format(DateTime.now()),
              style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
            ),
            const SizedBox(height: 20),

            dashboardAsync.when(
              loading: () => const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(),
                ),
              ),
              error: (err, stack) => EmptyState(
                icon: Icons.cloud_off_rounded,
                title: 'Erreur de chargement',
                subtitle: 'Vérifie ta connexion et réessaie',
              ),
              data: (data) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // KPI Cards
                  GridView.count(
                    crossAxisCount: 2,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    childAspectRatio: 1.3,
                    children: [
                      KpiCard(
                        label: 'Élèves actifs',
                        value: data.activeStudents.toString(),
                        icon: Icons.people_rounded,
                        color: AppTheme.primary,
                        subtitle: '${data.totalStudents} total',
                        onTap: () => context.go('/students'),
                      ),
                      KpiCard(
                        label: 'Encaissé ce mois',
                        value: data.currentMonthFormatted,
                        icon: Icons.trending_up_rounded,
                        color: AppTheme.success,
                        onTap: () => context.go('/reports'),
                      ),
                      KpiCard(
                        label: 'Aujourd\'hui',
                        value: data.todayTransactions.toString(),
                        icon: Icons.receipt_long_rounded,
                        color: AppTheme.secondary,
                        subtitle: 'transactions',
                        onTap: () => context.go('/transactions'),
                      ),
                      KpiCard(
                        label: 'Total collecté',
                        value: data.totalCollectedFormatted,
                        icon: Icons.account_balance_wallet_rounded,
                        color: Colors.orange,
                        onTap: () => context.go('/reports'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Quick actions
                  SectionHeader(title: 'Actions rapides'),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: _QuickActionCard(
                          icon: Icons.payments_rounded,
                          label: 'Encaissement',
                          color: AppTheme.success,
                          onTap: () => context.go('/ledger'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickActionCard(
                          icon: Icons.person_add_rounded,
                          label: 'Nouvel élève',
                          color: AppTheme.primary,
                          onTap: () => context.go('/students/create'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickActionCard(
                          icon: Icons.bar_chart_rounded,
                          label: 'Rapports',
                          color: Colors.orange,
                          onTap: () => context.go('/reports'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Recent transactions
                  SectionHeader(
                    title: 'Dernières transactions',
                    actionLabel: 'Voir tout',
                    onAction: () => context.go('/transactions'),
                  ),
                  const SizedBox(height: 8),
                  if (data.recentTransactions.isEmpty)
                    const EmptyState(
                      icon: Icons.receipt_long_outlined,
                      title: 'Aucune transaction récente',
                    )
                  else
                    ...data.recentTransactions.take(5).map((tx) => TransactionTile(
                          studentName: tx.studentName,
                          type: 'CASH_PAYMENT',
                          status: tx.status,
                          amountCents: tx.amountCents,
                          date: tx.recordedAt,
                        )),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _QuickActionCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _QuickActionCard({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 12),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: color, size: 24),
              ),
              const SizedBox(height: 8),
              Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500), textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }
}
