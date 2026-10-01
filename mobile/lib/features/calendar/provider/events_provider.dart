import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/network/dio_client.dart';

class EventItem {
  final int id;
  final String title;
  final String startAt;
  final String endAt;

  const EventItem({
    required this.id,
    required this.title,
    required this.startAt,
    required this.endAt,
  });

  factory EventItem.fromJson(Map<String, dynamic> json) => EventItem(
        id: json['id'] as int,
        title: json['title'] as String,
        startAt: json['startAt'] as String,
        endAt: json['endAt'] as String,
      );
}

class EventsNotifier extends StateNotifier<AsyncValue<List<EventItem>>> {
  EventsNotifier() : super(const AsyncValue.loading()) {
    refresh();
  }

  final _dio = createDio();

  Future<void> refresh() async {
    try {
      final res = await _dio.get('/api/v1/events');
      final items = (res.data['data'] as List)
          .map((e) => EventItem.fromJson(e as Map<String, dynamic>))
          .toList();
      state = AsyncValue.data(items);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

/// 로그아웃 후 다시 로그인했을 때 이전 사용자의 일정이 남지 않도록 autoDispose.
/// 캘린더 화면이 구독하는 동안에는 같은 인스턴스가 유지된다.
final eventsProvider = StateNotifierProvider.autoDispose<EventsNotifier,
    AsyncValue<List<EventItem>>>(
  (_) => EventsNotifier(),
);
