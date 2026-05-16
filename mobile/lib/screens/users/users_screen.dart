import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/user.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';

final usersListProvider = FutureProvider.autoDispose<List<User>>((ref) {
  final api = ref.watch(apiClientProvider);
  return api.getUsers().then((r) {
    final data = r.data as List<dynamic>;
    return data.map((e) => User.fromJson(e as Map<String, dynamic>)).toList();
  });
});

class UsersScreen extends ConsumerWidget {
  const UsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final usersAsync = ref.watch(usersListProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Utilisateurs')),
      body: usersAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => EmptyState(icon: Icons.error_outline, title: 'Erreur', subtitle: err.toString()),
        data: (users) {
          if (users.isEmpty) {
            return const EmptyState(icon: Icons.person_outline, title: 'Aucun utilisateur');
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(usersListProvider),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: users.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (context, index) {
                final user = users[index];
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: AppTheme.primary.withOpacity(0.1),
                    child: Text(
                      user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : '?',
                      style: const TextStyle(color: AppTheme.primary, fontWeight: FontWeight.bold),
                    ),
                  ),
                  title: Text(user.fullName, style: const TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: Row(
                    children: [
                      Text(user.email, style: const TextStyle(fontSize: 12)),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppTheme.primary.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(user.roleLabel, style: TextStyle(fontSize: 10, color: AppTheme.primary, fontWeight: FontWeight.w500)),
                      ),
                    ],
                  ),
                  trailing: StatusBadge(status: user.status),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
