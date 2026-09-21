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

final eventsProvider =
    StateNotifierProvider<EventsNotifier, AsyncValue<List<EventItem>>>(
  (_) => EventsNotifier(),
);
