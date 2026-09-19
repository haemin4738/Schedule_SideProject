import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/router/app_router.dart';

void main() {
  runApp(const ProviderScope(child: LifelogApp()));
}

class LifelogApp extends ConsumerWidget {
  const LifelogApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: 'Lifelog',
      routerConfig: router,
      theme: ThemeData(colorSchemeSeed: Colors.blue, useMaterial3: true),
    );
  }
}
