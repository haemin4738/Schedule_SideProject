import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/calendar/event_category.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';
import 'package:flutter/material.dart' show Color, FlutterError, FlutterErrorDetails;

import '../expenses/expense_fake_http.dart';

const _events = '/api/v1/events';
const _special = '/api/v1/special-days';
final _noon = DateTime(2026, 10, 8, 12);
final _one = DateTime(2026, 10, 8, 13);

Map<String, Object?> eventJson(
  int id, {
  String title = '일정',
  String startAt = '2026-10-08T14:00:00',
  String? endAt = '2026-10-08T15:00:00',
  bool allDay = false,
}) => {'id': id, 'title': title, 'startAt': startAt, 'endAt': endAt, 'allDay': allDay};

/// 일정 외 부가 조회(특일·구직활동·가계부)에 빈 정상 응답. 일정 경로면 null
ResponseBody? _aux(RequestOptions o) => switch (o.path) {
      _special => ok([]),
      '/api/v1/job-applications' => ok([], meta: {'page': 0, 'size': 100, 'total': 0, 'totalPages': 0}),
      '/api/v1/expenses/summary/daily' => ok({'days': []}),
      _ => null,
    };

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
    handler = (o) => _aux(o) ?? eventsPage([eventJson(1)]);
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
    handler = (o) => _aux(o) ?? eventsPage([eventJson(1, startAt: '2026-10-08T09:00:00', endAt: null)]);
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

  test('eventsOn_자정정각에끝나는일정_다음날에는표시하지않는다', () {
    final state = CalendarMonthState(events: [
      EventItem(id: 1, title: '야간', startAt: DateTime(2026, 10, 3, 23), endAt: DateTime(2026, 10, 4)),
      EventItem(id: 2, title: '자정 순간', startAt: DateTime(2026, 10, 4), endAt: DateTime(2026, 10, 4)),
    ]);

    expect(state.eventsOn(DateTime(2026, 10, 3)).map((e) => e.id), [1]);
    // 0분짜리 자정 일정은 그날에 보인다
    expect(state.eventsOn(DateTime(2026, 10, 4)).map((e) => e.id), [2]);
  });

  test('refresh_보이는데이터가있으면_다시불러오는동안로딩으로바꾸지않고실패해도유지한다', () async {
    final notifier = create();
    await until(() => !notifier.state.isLoading);
    final pending = Completer<ResponseBody>();
    handler = (o) => _aux(o) ?? pending.future;

    final refreshing = notifier.refresh();
    // 30초 주기 갱신 중에도 달력이 깜빡이지 않는다
    expect(notifier.state.isLoading, isFalse);
    expect(notifier.state.value!.events.single.id, 1);

    pending.complete(errorBody(500, '일시 오류'));
    await refreshing;
    expect(notifier.state.value!.events.single.id, 1);
    notifier.dispose();
  });

  test('goToToday_다른달에서_이번달로돌아와다시조회한다', () async {
    final notifier = create(month: DateTime(2020, 1, 1));
    await until(() => !notifier.state.isLoading);

    await notifier.goToToday();

    final now = DateTime.now();
    expect(notifier.month, DateTime(now.year, now.month));
    expect(adapter.requestsOf('GET', _events), hasLength(2));
    notifier.dispose();
  });

  test('refresh_여러페이지_끝까지이어받는다', () async {
    handler = (o) {
      final aux = _aux(o);
      if (aux != null) return aux;
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
    handler = (o) => _aux(o) ?? eventsPage([eventJson(1)], page: o.queryParameters['page'] as int, totalPages: 999);
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(adapter.requestsOf('GET', _events), hasLength(20));
    expect(notifier.state.value!.truncated, isTrue);
    notifier.dispose();
  });

  test('refresh_특일조회실패_일정은표시하고특일은비운다', () async {
    handler = (o) => o.path == _special ? errorBody(500, '외부 API 오류') : (_aux(o) ?? eventsPage([eventJson(1)]));
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
        : (_aux(o) ?? eventsPage([]));
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    final state = notifier.state.value!;
    expect(state.isHoliday(DateTime(2026, 10, 3)), isTrue);
    expect(state.isHoliday(DateTime(2026, 10, 8)), isFalse);
    expect(state.specialDaysOn(DateTime(2026, 10, 8)).single.name, '한로');
    notifier.dispose();
  });

  test('refresh_일정조회실패_오류상태가된다', () async {
    handler = (o) => _aux(o) ?? errorBody(500, '서버 오류');
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(notifier.state, isA<AsyncError<CalendarMonthState>>());
    notifier.dispose();
  });

  test('changeMonth_이전달응답이늦게와도_현재달결과를덮어쓰지않는다', () async {
    final slowOctober = Completer<ResponseBody>();
    handler = (o) {
      final aux = _aux(o);
      if (aux != null) return aux;
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

  group('생성·수정·삭제', () {
    final input = EventInput(
      title: '점심',
      allDay: false,
      startAt: _noon,
      endAt: _one,
      eventCategory: EventCategory.WORK,
      color: '#D50000',
      location: '강남',
    );

    test('create_입력_LocalDateTime본문으로보내고다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => _aux(o) ?? (o.method == 'POST' ? ok(eventJson(5)) : eventsPage([eventJson(5)]));

      await notifier.create(input);

      final post = adapter.requestsOf('POST', _events).single;
      expect(bodyOf(post), {
        'title': '점심',
        'allDay': false,
        'startAt': '2026-10-08T12:00:00',
        'endAt': '2026-10-08T13:00:00',
        'eventCategory': 'WORK',
        'color': '#D50000',
        'location': '강남',
        'description': null,
      });
      expect(adapter.requestsOf('GET', _events), hasLength(2));
      expect(notifier.state.value!.events.single.id, 5);
      notifier.dispose();
    });

    test('update_종료없음_endAt을null로보낸다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => _aux(o) ?? (o.method == 'PUT' ? ok(eventJson(1)) : eventsPage([eventJson(1)]));

      await notifier.update(
        1,
        EventInput(title: 't', allDay: false, startAt: _noon, endAt: null, eventCategory: EventCategory.PERSONAL),
      );

      final put = adapter.requestsOf('PUT', '$_events/1').single;
      expect(bodyOf(put)['endAt'], isNull);
      expect(bodyOf(put).containsKey('endAt'), isTrue);
      notifier.dispose();
    });

    test('delete_성공_삭제후다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => _aux(o) ?? (o.method == 'DELETE' ? ok(null) : eventsPage([]));

      await notifier.delete(1);

      expect(adapter.requestsOf('DELETE', '$_events/1'), hasLength(1));
      expect(notifier.state.value!.events, isEmpty);
      notifier.dispose();
    });

    test('create_서버오류_예외를던지고목록은그대로다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => _aux(o) ?? (o.method == 'POST' ? errorBody(400, '종료 시간은 시작 시간보다 빠를 수 없습니다.') : eventsPage([eventJson(1)]));

      await expectLater(
        notifier.create(input),
        throwsA(predicate((e) => eventErrorMessage(e!) == '종료 시간은 시작 시간보다 빠를 수 없습니다.')),
      );
      expect(notifier.state.value!.events.single.id, 1);
      notifier.dispose();
    });

    test('getDetail_설명장소카테고리색까지파싱한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => ok({
            ...eventJson(3, endAt: null),
            'color': '#D50000',
            'eventCategory': 'WORK',
            'description': '설명',
            'location': '회의실',
          });

      final detail = await notifier.getDetail(3);

      expect(detail.description, '설명');
      expect(detail.location, '회의실');
      expect(detail.eventCategory, EventCategory.WORK);
      expect(detail.endAt, isNull);
      expect(detail.displayColor, const Color(0xFFD50000));
      notifier.dispose();
    });
  });

  test('resolveEventColor_형식틀린색은카테고리기본색_둘다없으면개인색', () {
    expect(resolveEventColor('red', EventCategory.WORK), EventCategory.WORK.defaultColor);
    expect(resolveEventColor(null, null), EventCategory.PERSONAL.defaultColor);
    expect(resolveEventColor('#039be5', null), const Color(0xFF039BE5));
  });

  group('구직활동·가계부 오버레이', () {
    const jobs = '/api/v1/job-applications';
    const daily = '/api/v1/expenses/summary/daily';

    Map<String, Object?> jobJson(int id, String appliedAt) => {
          'id': id,
          'companyName': '회사$id',
          'position': '백엔드',
          'status': 'APPLIED',
          'appliedAt': appliedAt,
        };

    ResponseBody overlayHandler(RequestOptions o) {
      if (o.path == _special) return ok([]);
      if (o.path == jobs) {
        final page = o.queryParameters['page'] as int;
        return ok([jobJson(page + 1, '2026-10-0${page + 1}')],
            meta: {'page': page, 'size': 100, 'total': 2, 'totalPages': 2});
      }
      if (o.path == daily) {
        return ok({
          'from': '2026-09-27',
          'to': '2026-11-07',
          'totalIncome': 50000,
          'totalExpense': 12000,
          'net': 38000,
          'days': [
            {'date': '2026-10-07', 'income': 50000, 'expense': 12000, 'net': 38000},
          ],
        });
      }
      return eventsPage([]);
    }

    test('refresh_구직활동과가계부_보이는기간으로조회해날짜별로묶는다', () async {
      handler = overlayHandler;
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      final jobQuery = adapter.requestsOf('GET', jobs).first.queryParameters;
      expect(jobQuery['from'], '2026-09-27');
      expect(jobQuery['to'], '2026-11-07');
      expect(jobQuery['size'], 100);
      expect(adapter.requestsOf('GET', jobs), hasLength(2));
      expect(adapter.requestsOf('GET', daily).single.queryParameters, {'from': '2026-09-27', 'to': '2026-11-07'});

      final state = notifier.state.value!;
      expect(state.jobApplicationsOn(DateTime(2026, 10, 1)).single.companyName, '회사1');
      expect(state.jobApplicationsOn(DateTime(2026, 10, 2)).single.companyName, '회사2');
      expect(state.moneyOn(DateTime(2026, 10, 7))!.expense, 12000);
      expect(state.moneyOn(DateTime(2026, 10, 7))!.income, 50000);
      expect(state.moneyOn(DateTime(2026, 10, 8)), isNull);
      notifier.dispose();
    });

    test('refresh_구직활동가계부조회실패_일정은표시하고오버레이만비운다', () async {
      handler = (o) => (o.path == jobs || o.path == daily) ? errorBody(500, '오류') : (_aux(o) ?? eventsPage([eventJson(1)]));
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      final state = notifier.state.value!;
      expect(state.events, hasLength(1));
      expect(state.jobApplications, isEmpty);
      expect(state.money, isEmpty);
      notifier.dispose();
    });
  });

  test('refresh_부가정보응답형식오류_숨기지않고보고하며일정은표시한다', () async {
    final reported = <FlutterErrorDetails>[];
    final previous = FlutterError.onError;
    FlutterError.onError = reported.add;
    addTearDown(() => FlutterError.onError = previous);
    // 백엔드에 앱이 모르는 상태값이 추가된 경우 등
    handler = (o) => o.path == '/api/v1/job-applications'
        ? ok([
            {'id': 1, 'companyName': 'A', 'position': 'B', 'status': 'NEW_STATUS', 'appliedAt': '2026-10-01'},
          ], meta: {'page': 0, 'size': 100, 'total': 1, 'totalPages': 1})
        : (_aux(o) ?? eventsPage([eventJson(1)]));
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(notifier.state.value!.events, hasLength(1));
    expect(notifier.state.value!.jobApplications, isEmpty);
    expect(reported, hasLength(1));
    notifier.dispose();
  });

  test('refresh_부가정보통신오류_보고하지않는다', () async {
    final reported = <FlutterErrorDetails>[];
    final previous = FlutterError.onError;
    FlutterError.onError = reported.add;
    addTearDown(() => FlutterError.onError = previous);
    handler = (o) => o.path == '/api/v1/job-applications' ? errorBody(500, '오류') : (_aux(o) ?? eventsPage([]));
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(reported, isEmpty);
    notifier.dispose();
  });
}
