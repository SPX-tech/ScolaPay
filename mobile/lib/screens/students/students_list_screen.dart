import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/student.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/auth_provider.dart';

final studentsListProvider = FutureProvider.autoDispose.family<StudentListResponse, _StudentsQuery>((ref, query) {
  final api = ref.watch(apiClientProvider);
  return api.getStudents(page: query.page, limit: 20, search: query.search, status: query.status, classId: query.classId)
      .then((r) => StudentListResponse.fromJson(r.data as Map<String, dynamic>));
});

class _StudentsQuery {
  final int page;
  final String? search;
  final String? status;
  final String? classId;
  const _StudentsQuery({this.page = 1, this.search, this.status, this.classId});
}

class StudentsListScreen extends ConsumerStatefulWidget {
  const StudentsListScreen({super.key});

  @override
  ConsumerState<StudentsListScreen> createState() => _StudentsListScreenState();
}

class _StudentsListScreenState extends ConsumerState<StudentsListScreen> {
  final _searchController = TextEditingController();
  String? _statusFilter;
  String? _classFilter;
  int _page = 1;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final query = _StudentsQuery(page: _page, search: _searchController.text.isNotEmpty ? _searchController.text : null, status: _statusFilter);
    final studentsAsync = ref.watch(studentsListProvider(query));
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Élèves')),
      body: Column(
        children: [
          // Search + filters
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: Column(
              children: [
                TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: 'Rechercher un élève...',
                    prefixIcon: const Icon(Icons.search_rounded),
                    suffixIcon: _searchController.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: () {
                              _searchController.clear();
                              setState(() => _page = 1);
                            },
                          )
                        : null,
                  ),
                  onSubmitted: (_) => setState(() => _page = 1),
                ),
                const SizedBox(height: 8),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _FilterChip(
                        label: 'Tous',
                        selected: _statusFilter == null,
                        onTap: () => setState(() { _statusFilter = null; _page = 1; }),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Actifs',
                        selected: _statusFilter == 'ACTIVE',
                        onTap: () => setState(() { _statusFilter = 'ACTIVE'; _page = 1; }),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Suspendus',
                        selected: _statusFilter == 'SUSPENDED',
                        onTap: () => setState(() { _statusFilter = 'SUSPENDED'; _page = 1; }),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Diplômés',
                        selected: _statusFilter == 'GRADUATED',
                        onTap: () => setState(() { _statusFilter = 'GRADUATED'; _page = 1; }),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // List
          Expanded(
            child: studentsAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (err, stack) => EmptyState(
                icon: Icons.error_outline,
                title: 'Erreur de chargement',
                subtitle: err.toString(),
              ),
              data: (response) {
                if (response.data.isEmpty) {
                  return const EmptyState(
                    icon: Icons.people_outline,
                    title: 'Aucun élève trouvé',
                  );
                }
                return RefreshIndicator(
                  onRefresh: () async => ref.invalidate(studentsListProvider(query)),
                  child: ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: response.data.length + (response.hasMore ? 1 : 0),
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      if (index == response.data.length) {
                        return Padding(
                          padding: const EdgeInsets.all(16),
                          child: Center(
                            child: TextButton(
                              onPressed: () => setState(() => _page++),
                              child: const Text('Charger plus'),
                            ),
                          ),
                        );
                      }
                      final student = response.data[index];
                      return _StudentTile(
                        student: student,
                        onTap: () => context.go('/students/${student.id}'),
                      );
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.go('/students/create'),
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip({required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? AppTheme.primary : Colors.grey.shade100,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: selected ? Colors.white : Colors.grey.shade700,
          ),
        ),
      ),
    );
  }
}

class _StudentTile extends StatelessWidget {
  final Student student;
  final VoidCallback onTap;

  const _StudentTile({required this.student, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      leading: CircleAvatar(
        backgroundColor: student.isActive ? AppTheme.primary.withOpacity(0.1) : Colors.grey.shade100,
        child: Text(
          student.fullName.isNotEmpty ? student.fullName[0].toUpperCase() : '?',
          style: TextStyle(
            fontWeight: FontWeight.w600,
            color: student.isActive ? AppTheme.primary : Colors.grey,
          ),
        ),
      ),
      title: Text(student.fullName, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: Row(
        children: [
          Text(student.studentNumber, style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
          if (student.className != null) ...[
            const SizedBox(width: 8),
            Icon(Icons.class_rounded, size: 12, color: Colors.grey.shade400),
            const SizedBox(width: 2),
            Text(student.className!, style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
          ],
        ],
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          AmountText(cents: student.debtCents),
          if (student.hasDebt)
            Text('dette', style: TextStyle(fontSize: 11, color: AppTheme.error))
          else
            Text('OK', style: TextStyle(fontSize: 11, color: AppTheme.success)),
        ],
      ),
    );
  }
}
