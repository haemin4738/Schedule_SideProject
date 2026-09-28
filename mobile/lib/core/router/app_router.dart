import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile/features/auth/presentation/login_page.dart';
import 'package:mobile/features/calendar/presentation/calendar_page.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';
import 'package:mobile/features/expenses/presentation/expenses_page.dart';
import 'package:mobile/features/jobapplications/presentation/job_applications_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isLoggedIn = authState.accessToken != null;
      if (!isLoggedIn && state.matchedLocation != '/login') return '/login';
      if (isLoggedIn && state.matchedLocation == '/login') return '/';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, _) => const LoginPage()),
      GoRoute(path: '/', builder: (_, _) => const CalendarPage()),
      GoRoute(
        path: '/job-applications',
        builder: (_, _) => const JobApplicationsPage(),
      ),
      GoRoute(
        path: '/expenses',
        builder: (_, _) => const ExpensesPage(),
      ),
    ],
  );
});
