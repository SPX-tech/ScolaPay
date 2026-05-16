import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/constants.dart';
import '../../models/school_class.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';

final tenantProvider = FutureProvider.autoDispose<Tenant>((ref) {
  final api = ref.watch(apiClientProvider);
  return api.getTenant().then((r) => Tenant.fromJson(r.data as Map<String, dynamic>));
});

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tenantAsync = ref.watch(tenantProvider);
    final authState = ref.watch(authProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Paramètres')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Tenant info
          tenantAsync.when(
            loading: () => const Card(child: Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator())),
            error: (err, stack) => const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Erreur de chargement'))),
            data: (tenant) => Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppTheme.primary.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.business_rounded, color: AppTheme.primary, size: 24),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(tenant.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                              Text('Code: ${tenant.code}', style: TextStyle(fontSize: 13, color: Colors.grey.shade600)),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 24),
                    _InfoRow(label: 'Code', value: tenant.code),
                    _InfoRow(label: 'Monnaie', value: tenant.currency),
                    _InfoRow(label: 'Fuseau horaire', value: tenant.timezone),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // User info
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppTheme.primary.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(Icons.person_rounded, color: AppTheme.primary, size: 24),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(authState.user?.fullName ?? '', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                            Text(authState.user?.email ?? '', style: TextStyle(fontSize: 13, color: Colors.grey.shade600)),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 24),
                  if (authState.user != null) ...[
                    _InfoRow(label: 'Rôle', value: authState.user!.roleLabel),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // App info
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppTheme.primary.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(Icons.info_outline_rounded, color: AppTheme.primary, size: 24),
                      ),
                      const SizedBox(width: 12),
                      Text('À propos', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                    ],
                  ),
                  const Divider(height: 24),
                  _InfoRow(label: 'Application', value: AppConstants.appName),
                  _InfoRow(label: 'Version', value: '1.0.0'),
                  _InfoRow(label: 'Pays', value: AppConstants.country),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Logout
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () {
                ref.read(authProvider.notifier).logout();
              },
              icon: const Icon(Icons.logout_rounded, color: AppTheme.error),
              label: const Text('Déconnexion', style: TextStyle(color: AppTheme.error)),
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: AppTheme.error.withOpacity(0.3)),
                minimumSize: const Size.fromHeight(50),
              ),
            ),
          ),
        ],
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
