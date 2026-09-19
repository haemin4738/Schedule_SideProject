import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile/features/auth/presentation/login_page.dart';
import 'package:mobile/features/calendar/presentation/calendar_page.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';

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
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),
      GoRoute(path: '/', builder: (_, __) => const CalendarPage()),
    ],
  );
});
