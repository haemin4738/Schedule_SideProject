import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/core/network/sse_client.dart';
import 'package:mobile/features/calendar/event_category.dart';
import 'package:mobile/features/jobapplications/provider/job_applications_provider.dart';

final _dateTime = DateFormat("yyyy-MM-dd'T'HH:mm:ss");
final _date = DateFormat('yyyy-MM-dd');

/// 목록 조회 응답 (백엔드 EventSummary). 종료가 없는 일정은 endAt 이 null 이다.
class EventItem {
  final int id;
  final String title;
  final DateTime startAt;
  final DateTime? endAt;
  final bool allDay;
  final String? color;
  final EventCategory? eventCategory;

  const EventItem({
    required this.id,
    required this.title,
    required this.startAt,
    this.endAt,
    this.allDay = false,
    this.color,
    this.eventCategory,
  });

  factory EventItem.fromJson(Map<String, dynamic> json) => EventItem(
        id: json['id'] as int,
        title: json['title'] as String,
        startAt: DateTime.parse(json['startAt'] as String),
        endAt: json['endAt'] == null ? null : DateTime.parse(json['endAt'] as String),
        allDay: json['allDay'] as bool? ?? false,
        color: json['color'] as String?,
        eventCategory: parseEventCategory(json['eventCategory']),
      );

  Color get displayColor => resolveEventColor(color, eventCategory);

  /// [day] 하루와 겹치는지. 종료가 없으면 시작한 날에만 보인다.
  /// 종료는 웹 캘린더처럼 경계 미포함 — 자정 정각에 끝나는 일정은 다음 날에 그리지 않는다
  bool occursOn(DateTime day) {
    // 날짜 계산은 Duration 이 아니라 연·월·일로 한다 (서머타임 지역에서도 하루가 밀리지 않게)
    final dayStart = DateTime(day.year, day.month, day.day);
    final dayEnd = DateTime(day.year, day.month, day.day + 1);
    if (!startAt.isBefore(dayEnd)) return false;
    final end = endAt;
    if (end == null) return !startAt.isBefore(dayStart);
    return end.isAfter(dayStart) || (end == startAt && !startAt.isBefore(dayStart));
  }
}

/// 단건 조회 응답 (백엔드 EventResponse) — 목록에 없는 설명·장소를 포함한다. 수정 폼은 반드시 이것으로 채운다
class EventDetail extends EventItem {
  final String? description;
  final String? location;

  const EventDetail({
    required super.id,
    required super.title,
    required super.startAt,
    super.endAt,
    super.allDay,
    super.color,
    super.eventCategory,
    this.description,
    this.location,
  });

  factory EventDetail.fromJson(Map<String, dynamic> json) {
    final base = EventItem.fromJson(json);
    return EventDetail(
      id: base.id,
      title: base.title,
      startAt: base.startAt,
      endAt: base.endAt,
      allDay: base.allDay,
      color: base.color,
      eventCategory: base.eventCategory,
      description: json['description'] as String?,
      location: json['location'] as String?,
    );
  }
}

/// 생성·수정 요청 본문 (백엔드 EventRequest). 날짜는 타임존 없는 LocalDateTime 문자열
class EventInput {
  final String title;
  final bool allDay;
  final DateTime startAt;
  final DateTime? endAt;
  final EventCategory eventCategory;
  final String? color;
  final String? location;
  final String? description;

  const EventInput({
    required this.title,
    required this.allDay,
    required this.startAt,
    required this.endAt,
    required this.eventCategory,
    this.color,
    this.location,
    this.description,
  });

  Map<String, dynamic> toJson() => {
        'title': title,
        'allDay': allDay,
        'startAt': _dateTime.format(startAt),
        'endAt': endAt == null ? null : _dateTime.format(endAt!),
        'eventCategory': eventCategory.name,
        'color': color,
        'location': location,
        'description': description,
      };
}

/// 서버 envelope 의 error 메시지(한국어)를 그대로 꺼낸다. 없으면 일반 안내 문구
String eventErrorMessage(Object error) {
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map && data['error'] is String) return data['error'] as String;
    return '서버와 통신할 수 없습니다.';
  }
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
}

/// 공휴일·기념일·24절기 (백엔드 SpecialDayResponse)
class SpecialDay {
  final String date;
  final String name;
  final bool holiday;

  const SpecialDay({required this.date, required this.name, required this.holiday});

  factory SpecialDay.fromJson(Map<String, dynamic> json) => SpecialDay(
        date: json['date'] as String,
        name: json['name'] as String,
        holiday: json['holiday'] as bool? ?? false,
      );
}

/// 월간 달력에 보이는 첫날(그 달 1일이 속한 주의 일요일)
DateTime gridStart(DateTime month) {
  final first = DateTime(month.year, month.month);
  return DateTime(first.year, first.month, 1 - first.weekday % 7);
}

/// 월간 달력은 항상 6주(42칸)를 보여준다
const gridDays = 42;

/// 가계부 하루 합계 (백엔드 DailySummaryResponse.DailyItem). 내역이 있는 날만 온다
class DailyMoney {
  final int income;
  final int expense;

  const DailyMoney({required this.income, required this.expense});
}

class CalendarMonthState {
  final List<EventItem> events;

  /// yyyy-MM-dd → 그날 지원한 구직활동. 불러오지 못하면 비어 있다
  final Map<String, List<JobApplicationItem>> jobApplications;

  /// yyyy-MM-dd → 그날 가계부 합계. 불러오지 못하면 비어 있다
  final Map<String, DailyMoney> money;

  /// yyyy-MM-dd → 그날의 특일. 불러오지 못하면 비어 있다 (일정 표시는 계속한다)
  final Map<String, List<SpecialDay>> specialDays;

  /// 보이는 기간 일정이 상한을 넘어 일부만 가져왔는지
  final bool truncated;

  const CalendarMonthState({
    required this.events,
    this.specialDays = const {},
    this.jobApplications = const {},
    this.money = const {},
    this.truncated = false,
  });

  List<JobApplicationItem> jobApplicationsOn(DateTime day) => jobApplications[_date.format(day)] ?? const [];

  DailyMoney? moneyOn(DateTime day) => money[_date.format(day)];

  List<EventItem> eventsOn(DateTime day) => events.where((e) => e.occursOn(day)).toList();

  List<SpecialDay> specialDaysOn(DateTime day) => specialDays[_date.format(day)] ?? const [];

  bool isHoliday(DateTime day) => specialDaysOn(day).any((d) => d.holiday);
}

// 백엔드 목록 size 상한
const _pageSize = 100;
// 비정상 응답으로 무한 반복하지 않도록 페이지 수 상한 (한 화면 2000건)
const _maxPages = 20;

class EventsNotifier extends StateNotifier<AsyncValue<CalendarMonthState>> {
  /// [changes] 를 주면 그 이벤트(SSE)로 실시간 갱신한다 — provider 는 서버 SSE 를, 테스트는 가짜 스트림을 넘긴다
  EventsNotifier({DateTime? initialMonth, Dio? dio, Stream<SseEvent> Function(Dio dio)? changes})
      : _dio = dio ?? createDio(),
        month = DateTime((initialMonth ?? DateTime.now()).year, (initialMonth ?? DateTime.now()).month),
        super(const AsyncValue.loading()) {
    refresh();
    var first = true;
    _changes = changes?.call(_dio).listen((event) {
      // 일정이 바뀌면(REFRESH) 다시 불러오고, 다시 연결되면 끊겨 있던 동안의 변경을 따라잡는다.
      // 첫 연결은 생성자에서 이미 불러왔으므로 건너뛴다
      if (event.name == sseOpenEvent && first) {
        first = false;
        return;
      }
      if (event.name == 'REFRESH' || event.name == sseOpenEvent) refresh();
    });
  }

  StreamSubscription<SseEvent>? _changes;

  @override
  void dispose() {
    _changes?.cancel();
    super.dispose();
  }

  final Dio _dio;

  /// 보고 있는 달 (1일). 로딩 중에도 머리글에 보여야 하므로 상태와 따로 둔다
  DateTime month;

  // 달을 빠르게 넘길 때 늦게 도착한 이전 달 응답이 화면을 덮어쓰지 않게 하는 요청 순번
  int _seq = 0;

  Future<void> refresh() async {
    final seq = ++_seq;
    final from = gridStart(month);
    final to = DateTime(from.year, from.month, from.day + gridDays).subtract(const Duration(seconds: 1));
    // 이미 보이는 데이터가 있으면 새로 고치는 동안에도 유지한다 (30초 주기 갱신 시 깜빡임 방지)
    if (!state.hasValue) state = const AsyncValue.loading();
    try {
      final results = await Future.wait([
        _fetchEvents(from, to),
        _fetchSpecialDays(from, to),
        _fetchJobApplications(from, to),
        _fetchMoney(from, to),
      ]);
      if (!mounted || seq != _seq) return;
      final (events, truncated) = results[0] as (List<EventItem>, bool);
      state = AsyncValue.data(CalendarMonthState(
        events: events,
        specialDays: results[1] as Map<String, List<SpecialDay>>,
        jobApplications: results[2] as Map<String, List<JobApplicationItem>>,
        money: results[3] as Map<String, DailyMoney>,
        truncated: truncated,
      ));
    } catch (e, st) {
      if (!mounted || seq != _seq) return;
      // 이미 보이는 달력이 있으면(30초 주기 갱신이 한 번 실패한 경우 등) 오류 화면으로 바꾸지 않고 그대로 둔다
      if (state.valueOrNull != null) return;
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> changeMonth(int delta) {
    month = DateTime(month.year, month.month + delta);
    // 다른 달의 일정이 잠깐이라도 보이지 않게 비운다
    state = const AsyncValue.loading();
    return refresh();
  }

  Future<void> goToToday() {
    final now = DateTime.now();
    month = DateTime(now.year, now.month);
    state = const AsyncValue.loading();
    return refresh();
  }

  Future<(List<EventItem>, bool)> _fetchEvents(DateTime from, DateTime to) async {
    final events = <EventItem>[];
    for (var page = 0; page < _maxPages; page++) {
      final res = await _dio.get('/api/v1/events', queryParameters: {
        'from': _dateTime.format(from),
        'to': _dateTime.format(to),
        'page': page,
        'size': _pageSize,
      });
      events.addAll((res.data['data'] as List)
          .map((e) => EventItem.fromJson(e as Map<String, dynamic>)));
      final totalPages = (res.data['meta']?['totalPages'] as num?)?.toInt() ?? 0;
      if (page + 1 >= totalPages) return (events, false);
    }
    return (events, true);
  }

  Future<EventDetail> getDetail(int id) async {
    final res = await _dio.get('/api/v1/events/$id');
    return EventDetail.fromJson(res.data['data'] as Map<String, dynamic>);
  }

  Future<void> create(EventInput input) async {
    await _dio.post('/api/v1/events', data: input.toJson());
    if (mounted) await refresh();
  }

  Future<void> update(int id, EventInput input) async {
    await _dio.put('/api/v1/events/$id', data: input.toJson());
    if (mounted) await refresh();
  }

  Future<void> delete(int id) async {
    await _dio.delete('/api/v1/events/$id');
    if (mounted) await refresh();
  }

  /// 지원일이 보이는 기간인 구직활동 (부가 정보 — 실패해도 일정 표시는 계속한다)
  Future<Map<String, List<JobApplicationItem>>> _fetchJobApplications(DateTime from, DateTime to) async {
    try {
      final map = <String, List<JobApplicationItem>>{};
      for (var page = 0; page < _maxPages; page++) {
        final res = await _dio.get('/api/v1/job-applications', queryParameters: {
          'from': _date.format(from),
          'to': _date.format(to),
          'page': page,
          'size': _pageSize,
        });
        for (final json in res.data['data'] as List) {
          final item = JobApplicationItem.fromJson(json as Map<String, dynamic>);
          map.putIfAbsent(item.appliedAt, () => []).add(item);
        }
        final totalPages = (res.data['meta']?['totalPages'] as num?)?.toInt() ?? 0;
        if (page + 1 >= totalPages) break;
      }
      return map;
    } catch (e, st) {
      return _optional(e, st);
    }
  }

  /// 가계부 일별 합계 (부가 정보 — 실패해도 일정 표시는 계속한다)
  Future<Map<String, DailyMoney>> _fetchMoney(DateTime from, DateTime to) async {
    try {
      final res = await _dio.get('/api/v1/expenses/summary/daily', queryParameters: {
        'from': _date.format(from),
        'to': _date.format(to),
      });
      return {
        for (final json in (res.data['data']['days'] as List).cast<Map<String, dynamic>>())
          json['date'] as String: DailyMoney(
            income: (json['income'] as num).toInt(),
            expense: (json['expense'] as num).toInt(),
          ),
      };
    } catch (e, st) {
      return _optional(e, st);
    }
  }

  /// 부가 정보(특일·구직활동·가계부) 조회 실패 처리: 통신 오류는 빈 값으로 넘기고,
  /// 그 밖의 예외(응답 파싱 버그 등)는 숨기지 않고 보고한 뒤 빈 값으로 넘긴다
  Map<String, T> _optional<T>(Object error, StackTrace stack) {
    if (error is! DioException) {
      FlutterError.reportError(FlutterErrorDetails(
        exception: error,
        stack: stack,
        library: 'calendar',
        context: ErrorDescription('캘린더 부가 정보 응답 처리 중'),
      ));
    }
    return const {};
  }

  /// 특일은 부가 정보라 실패해도 일정 표시는 계속한다
  Future<Map<String, List<SpecialDay>>> _fetchSpecialDays(DateTime from, DateTime to) async {
    try {
      final res = await _dio.get('/api/v1/special-days', queryParameters: {
        'from': _date.format(from),
        'to': _date.format(to),
      });
      final map = <String, List<SpecialDay>>{};
      for (final json in res.data['data'] as List) {
        final day = SpecialDay.fromJson(json as Map<String, dynamic>);
        map.putIfAbsent(day.date, () => []).add(day);
      }
      return map;
    } catch (e, st) {
      return _optional(e, st);
    }
  }
}

/// 로그아웃 후 다시 로그인했을 때 이전 사용자의 일정이 남지 않도록 autoDispose.
/// 캘린더 화면이 구독하는 동안에는 같은 인스턴스가 유지된다.
final eventsProvider = StateNotifierProvider.autoDispose<EventsNotifier,
    AsyncValue<CalendarMonthState>>(
  (_) => EventsNotifier(changes: (dio) => sseEvents(dio, '/api/v1/sse/events')),
);
