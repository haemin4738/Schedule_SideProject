import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/router/app_router.dart';
import 'package:mobile/core/ui/root_messenger.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';

void main() {
  runApp(const ProviderScope(child: LifelogApp()));
}

class LifelogApp extends ConsumerWidget {
  const LifelogApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.listen<String?>(authProvider.select((s) => s.sessionNotice), (_, notice) {
      if (notice == null) return;
      rootScaffoldMessengerKey.currentState
        ?..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          content: Text(notice),
          duration: const Duration(seconds: 6),
          // action 이 있으면 기본값이 persist=true(자동으로 닫히지 않음)라 6초 후 닫히도록 명시한다
          persist: false,
          action: SnackBarAction(label: '확인', onPressed: () {}),
        ));
      // 한 번 보여준 안내는 비운다. provider 알림 도중 상태를 바꾸지 않도록 다음 마이크로태스크로 미룬다
      Future.microtask(() => ref.read(authProvider.notifier).clearSessionNotice());
    });

    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: 'Lifelog',
      routerConfig: router,
      scaffoldMessengerKey: rootScaffoldMessengerKey,
      theme: ThemeData(colorSchemeSeed: Colors.blue, useMaterial3: true),
    );
  }
}
