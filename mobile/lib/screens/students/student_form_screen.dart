import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../models/school_class.dart';
import '../../providers/auth_provider.dart';

final classesForFormProvider = FutureProvider.autoDispose<List<SchoolClass>>((ref) {
  final api = ref.watch(apiClientProvider);
  return api.getClasses().then((r) {
    final data = r.data as List<dynamic>;
    return data.map((e) => SchoolClass.fromJson(e as Map<String, dynamic>)).toList();
  });
});

class StudentFormScreen extends ConsumerStatefulWidget {
  const StudentFormScreen({super.key});

  @override
  ConsumerState<StudentFormScreen> createState() => _StudentFormScreenState();
}

class _StudentFormScreenState extends ConsumerState<StudentFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _numberController = TextEditingController();
  final _nameController = TextEditingController();
  final _guardianNameController = TextEditingController();
  final _guardianPhoneController = TextEditingController();
  String? _selectedClassId;
  bool _loading = false;

  @override
  void dispose() {
    _numberController.dispose();
    _nameController.dispose();
    _guardianNameController.dispose();
    _guardianPhoneController.dispose();
    super.dispose();
  }

  Future<void> _handleCreate() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _loading = true);
    try {
      final api = ref.read(apiClientProvider);
      await api.createStudent({
        'studentNumber': _numberController.text.trim(),
        'fullName': _nameController.text.trim(),
        'guardianName': _guardianNameController.text.trim().isEmpty ? null : _guardianNameController.text.trim(),
        'guardianPhone': _guardianPhoneController.text.trim().isEmpty ? null : _guardianPhoneController.text.trim(),
        if (_selectedClassId != null) 'classId': _selectedClassId,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Élève créé avec succès')));
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Erreur: ${e.toString()}')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final classesAsync = ref.watch(classesForFormProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Nouvel élève')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _numberController,
              decoration: const InputDecoration(labelText: 'Matricule *', prefixIcon: Icon(Icons.tag_rounded)),
              validator: (v) => v == null || v.trim().isEmpty ? 'Requis' : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(labelText: 'Nom complet *', prefixIcon: Icon(Icons.person_rounded)),
              validator: (v) => v == null || v.trim().isEmpty ? 'Requis' : null,
            ),
            const SizedBox(height: 16),
            classesAsync.when(
              loading: () => const LinearProgressIndicator(),
              error: (err, stack) => const Text('Erreur chargement classes'),
              data: (classes) => DropdownButtonFormField<String>(
                value: _selectedClassId,
                decoration: const InputDecoration(
                  labelText: 'Classe',
                  prefixIcon: Icon(Icons.class_rounded),
                ),
                items: classes.map((c) => DropdownMenuItem(value: c.id, child: Text('${c.name} - ${c.academicYear}'))).toList(),
                onChanged: (v) => setState(() => _selectedClassId = v),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _guardianNameController,
              decoration: const InputDecoration(labelText: 'Nom du tuteur', prefixIcon: Icon(Icons.contacts_rounded)),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _guardianPhoneController,
              decoration: const InputDecoration(
                labelText: 'Téléphone tuteur',
                prefixIcon: Icon(Icons.phone_rounded),
                hintText: '+235 XX XX XX XX',
              ),
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _loading ? null : _handleCreate,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primary,
                  foregroundColor: Colors.white,
                  minimumSize: const Size.fromHeight(50),
                ),
                child: _loading
                    ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Créer l\'élève'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
