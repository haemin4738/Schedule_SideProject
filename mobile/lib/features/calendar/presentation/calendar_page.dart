import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

class CalendarPage extends ConsumerStatefulWidget {
  const CalendarPage({super.key});

  @override
  ConsumerState<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends ConsumerState<CalendarPage> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // ponytail: 30s polling; replace with SSE when eventsource package is available
    _timer = Timer.periodic(const Duration(seconds: 30), (_) {
      ref.read(eventsProvider.notifier).refresh();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final eventsAsync = ref.watch(eventsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('캘린더'),
        actions: [
          IconButton(
            icon: const Icon(Icons.work_outline),
            tooltip: '구직활동',
            onPressed: () => context.push('/job-applications'),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.read(eventsProvider.notifier).refresh(),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(authProvider.notifier).logout(),
          ),
        ],
      ),
      body: eventsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (events) => events.isEmpty
            ? const Center(child: Text('일정이 없습니다.'))
            : ListView.builder(
                itemCount: events.length,
                itemBuilder: (context, index) {
                  final event = events[index];
                  return ListTile(
                    title: Text(event.title),
                    subtitle: Text(event.startAt.replaceAll('T', ' ').substring(0, 16)),
                  );
                },
              ),
      ),
    );
  }
}
