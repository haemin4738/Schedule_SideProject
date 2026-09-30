import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile/features/auth/presentation/login_page.dart';
import 'package:mobile/features/calendar/presentation/calendar_page.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';
import 'package:mobile/features/expenses/presentation/expenses_page.dart';
import 'package:mobile/features/jobapplications/presentation/job_applications_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  // 로그인 여부만 구독한다 — sessionNotice 변경으로 라우터가 다시 만들어지지 않도록
  final accessToken = ref.watch(authProvider.select((s) => s.accessToken));

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isLoggedIn = accessToken != null;
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
