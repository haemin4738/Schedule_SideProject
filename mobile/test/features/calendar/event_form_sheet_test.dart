import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/calendar/event_category.dart';
import 'package:mobile/features/calendar/presentation/event_form_sheet.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

/// 네트워크 없이 생성·수정·삭제 호출만 기록하는 fake
class _RecordingNotifier extends EventsNotifier {
  _RecordingNotifier() : super(initialMonth: DateTime(2026, 10)) {
    state = const AsyncValue.data(CalendarMonthState(events: []));
  }

  EventInput? created;
  (int, EventInput)? updated;
  int? deleted;
  Object? failWith;

  @override
  Future<void> refresh() async {}

  @override
  Future<void> create(EventInput input) async {
    if (failWith != null) throw failWith!;
    created = input;
  }

  @override
  Future<void> update(int id, EventInput input) async {
    if (failWith != null) throw failWith!;
    updated = (id, input);
  }

  @override
  Future<void> delete(int id) async {
    if (failWith != null) throw failWith!;
    deleted = id;
  }
}

/// 시트를 열고 닫힌 결과(true=저장·삭제됨)를 기록하는 테스트 화면
Widget _host(_RecordingNotifier notifier, {EventDetail? existing, List<bool?>? results}) => ProviderScope(
      overrides: [eventsProvider.overrideWith((ref) => notifier)],
      child: MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () async {
                  final r = await showModalBottomSheet<bool>(
                    context: context,
                    isScrollControlled: true,
                    builder: (_) => EventFormSheet(existing: existing, defaultDate: DateTime(2026, 10, 8)),
                  );
                  results?.add(r);
                },
                child: const Text('열기'),
              ),
            ),
          ),
        ),
      ),
    );

EventDetail _detail({DateTime? endAt, String? color = '#D50000'}) => EventDetail(
      id: 7,
      title: '팀 회의',
      startAt: DateTime(2026, 10, 8, 14),
      endAt: endAt,
      color: color,
      eventCategory: EventCategory.WORK,
      description: '주간 회의',
      location: '회의실 A',
    );

Future<void> _open(WidgetTester tester) async {
  tester.view.physicalSize = const Size(390 * 3, 844 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.tap(find.text('열기'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('submit_새일정_고른날오전9시부터1시간으로만들고닫는다', (tester) async {
    final notifier = _RecordingNotifier();
    final results = <bool?>[];
    await tester.pumpWidget(_host(notifier, results: results));
    await _open(tester);

    expect(find.text('새 일정'), findsOneWidget);
    await tester.enterText(find.widgetWithText(TextFormField, '제목'), '  점심 약속  ');
    await tester.enterText(find.widgetWithText(TextFormField, '장소 (선택)'), '강남역');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    final input = notifier.created!;
    expect(input.title, '점심 약속');
    expect(input.startAt, DateTime(2026, 10, 8, 9));
    expect(input.endAt, DateTime(2026, 10, 8, 10));
    expect(input.eventCategory, EventCategory.PERSONAL);
    expect(input.location, '강남역');
    expect(input.description, isNull);
    expect(input.color, isNull);
    expect(results, [true]);
  });

  testWidgets('submit_제목비어있음_오류를보여주고저장하지않는다', (tester) async {
    final notifier = _RecordingNotifier();
    await tester.pumpWidget(_host(notifier));
    await _open(tester);

    await tester.tap(find.text('저장'));
    await tester.pump();

    expect(find.text('제목을 입력하세요.'), findsOneWidget);
    expect(notifier.created, isNull);
  });

  testWidgets('submit_종일_시작일0시부터종료일23시59분59초로보낸다', (tester) async {
    final notifier = _RecordingNotifier();
    await tester.pumpWidget(_host(notifier));
    await _open(tester);

    await tester.enterText(find.widgetWithText(TextFormField, '제목'), '휴가');
    await tester.tap(find.text('종일'));
    await tester.pump();
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(notifier.created!.allDay, isTrue);
    expect(notifier.created!.startAt, DateTime(2026, 10, 8));
    expect(notifier.created!.endAt, DateTime(2026, 10, 8, 23, 59, 59));
  });

  testWidgets('submit_수정_설명장소를채우고색은그대로보낸다', (tester) async {
    final notifier = _RecordingNotifier();
    await tester.pumpWidget(_host(notifier, existing: _detail(endAt: DateTime(2026, 10, 8, 15))));
    await _open(tester);

    expect(find.text('일정 수정'), findsOneWidget);
    expect(find.text('주간 회의'), findsOneWidget);
    expect(find.text('회의실 A'), findsOneWidget);
    await tester.enterText(find.widgetWithText(TextFormField, '제목'), '팀 회의 (변경)');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    final (id, input) = notifier.updated!;
    expect(id, 7);
    expect(input.title, '팀 회의 (변경)');
    expect(input.endAt, DateTime(2026, 10, 8, 15));
    // 앱에는 색 선택이 없어도 웹에서 고른 색이 지워지지 않는다
    expect(input.color, '#D50000');
    expect(input.eventCategory, EventCategory.WORK);
  });

  testWidgets('submit_종료없는일정수정_종료를건드리지않으면null을유지한다', (tester) async {
    final notifier = _RecordingNotifier();
    await tester.pumpWidget(_host(notifier, existing: _detail()));
    await _open(tester);

    expect(find.text('(종료 없음)'), findsOneWidget);
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(notifier.updated!.$2.endAt, isNull);
  });

  testWidgets('submit_서버오류_메시지를보여주고시트를유지한다', (tester) async {
    final notifier = _RecordingNotifier()
      ..failWith = DioException(
        requestOptions: RequestOptions(),
        response: Response(
          requestOptions: RequestOptions(),
          statusCode: 400,
          data: {'success': false, 'error': '종료 시간은 시작 시간보다 빠를 수 없습니다.'},
        ),
      );
    await tester.pumpWidget(_host(notifier));
    await _open(tester);

    await tester.enterText(find.widgetWithText(TextFormField, '제목'), '일정');
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(find.text('종료 시간은 시작 시간보다 빠를 수 없습니다.'), findsOneWidget);
    expect(find.text('새 일정'), findsOneWidget);
  });

  testWidgets('delete_확인하면_삭제하고닫는다', (tester) async {
    final notifier = _RecordingNotifier();
    final results = <bool?>[];
    await tester.pumpWidget(_host(notifier, existing: _detail(), results: results));
    await _open(tester);

    await tester.tap(find.byTooltip('삭제'));
    await tester.pumpAndSettle();
    expect(find.text('"팀 회의" 일정을 삭제할까요?'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, '삭제'));
    await tester.pumpAndSettle();

    expect(notifier.deleted, 7);
    expect(results, [true]);
  });

  testWidgets('delete_취소하면_삭제하지않는다', (tester) async {
    final notifier = _RecordingNotifier();
    await tester.pumpWidget(_host(notifier, existing: _detail()));
    await _open(tester);

    await tester.tap(find.byTooltip('삭제'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('취소'));
    await tester.pumpAndSettle();

    expect(notifier.deleted, isNull);
    expect(find.text('일정 수정'), findsOneWidget);
  });

  testWidgets('render_새일정_삭제버튼이없다', (tester) async {
    await tester.pumpWidget(_host(_RecordingNotifier()));
    await _open(tester);

    expect(find.byTooltip('삭제'), findsNothing);
  });
}
