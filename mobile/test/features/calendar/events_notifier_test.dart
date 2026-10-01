import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

import '../expenses/expense_fake_http.dart';

const _events = '/api/v1/events';
const _special = '/api/v1/special-days';

Map<String, Object?> eventJson(
  int id, {
  String title = '일정',
  String startAt = '2026-10-08T14:00:00',
  String? endAt = '2026-10-08T15:00:00',
  bool allDay = false,
}) => {'id': id, 'title': title, 'startAt': startAt, 'endAt': endAt, 'allDay': allDay};

ResponseBody eventsPage(List<Map<String, Object?>> items, {int page = 0, int totalPages = 1}) =>
    ok(items, meta: {'page': page, 'size': 100, 'total': items.length, 'totalPages': totalPages});

void main() {
  late FakeHttpAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) handler;

  EventsNotifier create({DateTime? month}) {
    adapter = FakeHttpAdapter((o) => handler(o));
    return EventsNotifier(initialMonth: month ?? DateTime(2026, 10, 15), dio: fakeDio(adapter));
  }

  setUp(() {
    handler = (o) => o.path == _special ? ok([]) : eventsPage([eventJson(1)]);
  });

  test('refresh_초기생성_6주달력범위로일정과특일을조회한다', () async {
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    // 2026-10-01 은 목요일 → 달력은 9/27(일) ~ 11/7(토)
    final query = adapter.requestsOf('GET', _events).single.queryParameters;
    expect(query['from'], '2026-09-27T00:00:00');
    expect(query['to'], '2026-11-07T23:59:59');
    expect(query['size'], 100);
    expect(query['page'], 0);
    final special = adapter.requestsOf('GET', _special).single.queryParameters;
    expect(special['from'], '2026-09-27');
    expect(special['to'], '2026-11-07');
    expect(notifier.state.value!.events.single.id, 1);
    notifier.dispose();
  });

  test('refresh_종료없는일정_오류없이파싱하고시작일에표시한다', () async {
    handler = (o) => o.path == _special
        ? ok([])
        : eventsPage([eventJson(1, startAt: '2026-10-08T09:00:00', endAt: null)]);
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    final state = notifier.state.value!;
    expect(state.events.single.endAt, isNull);
    expect(state.eventsOn(DateTime(2026, 10, 8)), hasLength(1));
    expect(state.eventsOn(DateTime(2026, 10, 9)), isEmpty);
    notifier.dispose();
  });

  test('eventsOn_여러날일정_걸친모든날에포함된다', () {
    final state = CalendarMonthState(events: [
      EventItem(id: 1, title: '여행', startAt: DateTime(2026, 10, 3, 10), endAt: DateTime(2026, 10, 5, 18)),
    ]);

    expect(state.eventsOn(DateTime(2026, 10, 2)), isEmpty);
    expect(state.eventsOn(DateTime(2026, 10, 3)), hasLength(1));
    expect(state.eventsOn(DateTime(2026, 10, 4)), hasLength(1));
    expect(state.eventsOn(DateTime(2026, 10, 5)), hasLength(1));
    expect(state.eventsOn(DateTime(2026, 10, 6)), isEmpty);
  });

  test('refresh_여러페이지_끝까지이어받는다', () async {
    handler = (o) {
      if (o.path == _special) return ok([]);
      final page = o.queryParameters['page'] as int;
      return eventsPage([eventJson(page + 1)], page: page, totalPages: 3);
    };
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(adapter.requestsOf('GET', _events), hasLength(3));
    expect(notifier.state.value!.events.map((e) => e.id), [1, 2, 3]);
    expect(notifier.state.value!.truncated, isFalse);
    notifier.dispose();
  });

  test('refresh_페이지상한초과_일부만표시로표시한다', () async {
    handler = (o) => o.path == _special
        ? ok([])
        : eventsPage([eventJson(1)], page: o.queryParameters['page'] as int, totalPages: 999);
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(adapter.requestsOf('GET', _events), hasLength(20));
    expect(notifier.state.value!.truncated, isTrue);
    notifier.dispose();
  });

  test('refresh_특일조회실패_일정은표시하고특일은비운다', () async {
    handler = (o) => o.path == _special ? errorBody(500, '외부 API 오류') : eventsPage([eventJson(1)]);
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(notifier.state, isNot(isA<AsyncError<CalendarMonthState>>()));
    expect(notifier.state.value!.events, hasLength(1));
    expect(notifier.state.value!.specialDays, isEmpty);
    notifier.dispose();
  });

  test('refresh_특일_날짜별로묶고공휴일을판정한다', () async {
    handler = (o) => o.path == _special
        ? ok([
            {'date': '2026-10-03', 'name': '개천절', 'kind': 'HOLIDAY', 'holiday': true},
            {'date': '2026-10-08', 'name': '한로', 'kind': 'SOLAR_TERM', 'holiday': false},
          ])
        : eventsPage([]);
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    final state = notifier.state.value!;
    expect(state.isHoliday(DateTime(2026, 10, 3)), isTrue);
    expect(state.isHoliday(DateTime(2026, 10, 8)), isFalse);
    expect(state.specialDaysOn(DateTime(2026, 10, 8)).single.name, '한로');
    notifier.dispose();
  });

  test('refresh_일정조회실패_오류상태가된다', () async {
    handler = (o) => o.path == _special ? ok([]) : errorBody(500, '서버 오류');
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(notifier.state, isA<AsyncError<CalendarMonthState>>());
    notifier.dispose();
  });

  test('changeMonth_이전달응답이늦게와도_현재달결과를덮어쓰지않는다', () async {
    final slowOctober = Completer<ResponseBody>();
    handler = (o) {
      if (o.path == _special) return ok([]);
      final from = o.queryParameters['from'] as String;
      if (from.startsWith('2026-09-27')) return slowOctober.future;
      return eventsPage([eventJson(11, title: '11월 일정')]);
    };
    final notifier = create();

    await notifier.changeMonth(1);
    expect(notifier.month, DateTime(2026, 11));
    expect(notifier.state.value!.events.single.title, '11월 일정');

    slowOctober.complete(eventsPage([eventJson(10, title: '10월 일정')]));
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);

    expect(notifier.state.value!.events.single.title, '11월 일정');
    notifier.dispose();
  });

  test('changeMonth_연도경계_12월에서다음달은다음해1월이다', () async {
    final notifier = create(month: DateTime(2026, 12, 1));
    await until(() => !notifier.state.isLoading);

    await notifier.changeMonth(1);

    expect(notifier.month, DateTime(2027, 1));
    // 2027-01-01 은 금요일 → 달력은 2026-12-27(일)부터
    expect(adapter.requestsOf('GET', _events).last.queryParameters['from'], '2026-12-27T00:00:00');
    notifier.dispose();
  });

  test('gridStart_1일이일요일인달_그날부터시작한다', () {
    // 2026-11-01 은 일요일
    expect(gridStart(DateTime(2026, 11)), DateTime(2026, 11, 1));
  });
}
