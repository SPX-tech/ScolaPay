import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'providers/auth_provider.dart';
import 'screens/login/login_screen.dart';
import 'screens/dashboard/dashboard_screen.dart';
import 'screens/students/students_list_screen.dart';
import 'screens/students/student_detail_screen.dart';
import 'screens/students/student_form_screen.dart';
import 'screens/ledger/ledger_screen.dart';
import 'screens/transactions/transactions_screen.dart';
import 'screens/users/users_screen.dart';
import 'screens/reports/reports_screen.dart';
import 'screens/settings/settings_screen.dart';
import 'core/theme.dart';
import 'core/constants.dart';

// GoRouter configuration
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);
  return GoRouter(
    initialLocation: '/dashboard',
    redirect: (context, state) {
      final isLoggedIn = authState.isAuthenticated;
      final isLoginRoute = state.matchedLocation == '/login';

      if (!isLoggedIn && !isLoginRoute) return '/login';
      if (isLoggedIn && isLoginRoute) return '/dashboard';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      ShellRoute(
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(
            path: '/dashboard',
            builder: (context, state) => const DashboardScreen(),
          ),
          GoRoute(
            path: '/students',
            builder: (context, state) => const StudentsListScreen(),
            routes: [
              GoRoute(
                path: ':id',
                builder: (context, state) => StudentDetailScreen(
                  studentId: state.pathParameters['id']!,
                ),
              ),
              GoRoute(
                path: 'create',
                builder: (context, state) => const StudentFormScreen(),
              ),
            ],
          ),
          GoRoute(
            path: '/ledger',
            builder: (context, state) => const LedgerScreen(),
          ),
          GoRoute(
            path: '/transactions',
            builder: (context, state) => const TransactionsScreen(),
          ),
          GoRoute(
            path: '/users',
            builder: (context, state) => const UsersScreen(),
          ),
          GoRoute(
            path: '/reports',
            builder: (context, state) => const ReportsScreen(),
          ),
          GoRoute(
            path: '/settings',
            builder: (context, state) => const SettingsScreen(),
          ),
        ],
      ),
    ],
  );
});

class AppShell extends ConsumerWidget {
  final Widget child;
  const AppShell({super.key, required this.child});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final location = GoRouterState.of(context).matchedLocation;
    final currentIndex = _locationToIndex(location);

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: currentIndex,
        onDestinationSelected: (index) {
          final route = _indexToRoute(index);
          if (route != null) context.go(route);
        },
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard_rounded), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.people_rounded), label: 'Élèves'),
          NavigationDestination(icon: Icon(Icons.payments_rounded), label: 'Caisse'),
          NavigationDestination(icon: Icon(Icons.receipt_long_rounded), label: 'Transactions'),
          NavigationDestination(icon: Icon(Icons.more_horiz_rounded), label: 'Plus'),
        ],
      ),
    );
  }

  int _locationToIndex(String location) {
    if (location.startsWith('/dashboard')) return 0;
    if (location.startsWith('/students')) return 1;
    if (location.startsWith('/ledger')) return 2;
    if (location.startsWith('/transactions')) return 3;
    return 4;
  }

  String? _indexToRoute(int index) {
    switch (index) {
      case 0: return '/dashboard';
      case 1: return '/students';
      case 2: return '/ledger';
      case 3: return '/transactions';
      case 4: return '/settings';
      default: return '/dashboard';
    }
  }
}
