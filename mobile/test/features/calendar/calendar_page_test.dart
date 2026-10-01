import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/calendar/presentation/calendar_page.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

/// 네트워크 없이 상태만 주입하는 fake. 달 이동은 기록만 한다
class _FakeEventsNotifier extends EventsNotifier {
  _FakeEventsNotifier(AsyncValue<CalendarMonthState> initial, DateTime month)
      : super(initialMonth: month) {
    state = initial;
  }

  int refreshCount = 0;

  @override
  Future<void> refresh() async => refreshCount++;

  @override
  Future<void> changeMonth(int delta) async {
    month = DateTime(month.year, month.month + delta);
  }
}

final _now = DateTime.now();
final _month = DateTime(_now.year, _now.month);
// 이번 달 안의 날짜(10일)로 테스트 데이터를 만든다
final _day10 = DateTime(_month.year, _month.month, 10);
String _key(DateTime d) =>
    '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

CalendarMonthState _state() => CalendarMonthState(
      events: [
        EventItem(id: 1, title: '팀 회의', startAt: _day10.add(const Duration(hours: 14)),
            endAt: _day10.add(const Duration(hours: 15))),
        EventItem(id: 2, title: '종료 없는 일정', startAt: _day10.add(const Duration(hours: 9))),
      ],
      specialDays: {
        _key(_day10): [SpecialDay(date: _key(_day10), name: '테스트공휴일', holiday: true)],
      },
    );

Widget _wrap(_FakeEventsNotifier notifier) => ProviderScope(
      overrides: [eventsProvider.overrideWith((ref) => notifier)],
      child: const MaterialApp(home: CalendarPage()),
    );

/// 휴대폰 세로 화면 크기로 그린다
void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(390 * 3, 844 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
}

void main() {
  testWidgets('render_이번달_머리글과요일과42칸을그린다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    expect(find.text('${_month.year}년 ${_month.month}월'), findsOneWidget);
    for (final w in ['일', '월', '화', '수', '목', '금', '토']) {
      expect(find.text(w), findsWidgets);
    }
    expect(find.byWidgetPredicate((w) => w is Semantics && (w.properties.label ?? '').contains('월 ') && w.properties.button == true),
        findsNWidgets(42));
  });

  testWidgets('render_공휴일_이름과날짜숫자를빨갛게표시한다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    final name = tester.widget<Text>(find.text('테스트공휴일').first);
    expect(name.style?.color, Colors.red.shade600);
    // 접근성 이름에도 공휴일이 들어간다 (색만으로 구분하지 않게)
    expect(find.bySemanticsLabel(RegExp('${_month.month}월 10일 .요일, 테스트공휴일, 일정 2개')), findsOneWidget);
  });

  testWidgets('onTap_날짜를누르면_그날일정과특일을아래에보여준다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    await tester.tap(find.bySemanticsLabel(RegExp('${_month.month}월 10일 ')));
    await tester.pump();

    expect(find.text('팀 회의'), findsOneWidget);
    expect(find.text('14:00 ~ 15:00'), findsOneWidget);
    // 종료 없는 일정도 시작 시각만으로 보여준다
    expect(find.text('종료 없는 일정'), findsOneWidget);
    expect(find.text('09:00'), findsOneWidget);
    expect(find.text('테스트공휴일'), findsNWidgets(2));
  });

  testWidgets('semantics_날짜칸_스크린리더탭으로선택된다', (tester) async {
    _phone(tester);
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    final cell = find.bySemanticsLabel(RegExp('${_month.month}월 10일 '));
    final node = tester.getSemantics(cell);
    expect(node.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);

    tester.semantics.tap(find.semantics.byLabel(RegExp('${_month.month}월 10일 ')));
    await tester.pump();

    expect(find.text('팀 회의'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('onPressed_다음달_선택일이그달1일로바뀐다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    await tester.tap(find.byTooltip('다음 달'));
    await tester.pump();

    final next = DateTime(_month.year, _month.month + 1);
    expect(find.text('${next.month}월 1일 ${['일', '월', '화', '수', '목', '금', '토'][next.weekday % 7]}요일'), findsOneWidget);
  });

  testWidgets('onTap_일정없는날_안내문구를보여준다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    await tester.tap(find.bySemanticsLabel(RegExp('${_month.month}월 11일 ')));
    await tester.pump();

    expect(find.text('일정이 없습니다.'), findsOneWidget);
  });

  testWidgets('onPressed_다음달_머리글이바뀐다', (tester) async {
    _phone(tester);
    final notifier = _FakeEventsNotifier(AsyncValue.data(_state()), _month);
    await tester.pumpWidget(_wrap(notifier));

    await tester.tap(find.byTooltip('다음 달'));
    await tester.pump();

    final next = DateTime(_month.year, _month.month + 1);
    expect(find.text('${next.year}년 ${next.month}월'), findsOneWidget);
  });

  testWidgets('render_조회실패_다시시도를누르면다시불러온다', (tester) async {
    _phone(tester);
    final notifier = _FakeEventsNotifier(AsyncValue.error(Exception('x'), StackTrace.empty), _month);
    await tester.pumpWidget(_wrap(notifier));
    final before = notifier.refreshCount;

    expect(find.text('일정을 불러오지 못했습니다.'), findsOneWidget);
    await tester.tap(find.text('다시 시도'));

    expect(notifier.refreshCount, before + 1);
  });

  testWidgets('render_가로넓은화면_넘치지않고그린다', (tester) async {
    // 기본 테스트 화면(800x600 가로)에서도 RenderFlex overflow 가 나지 않아야 한다
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(AsyncValue.data(_state()), _month)));

    expect(tester.takeException(), isNull);
    expect(find.text('${_month.year}년 ${_month.month}월'), findsOneWidget);
  });

  testWidgets('render_일부만가져옴_안내를보여준다', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_wrap(_FakeEventsNotifier(
      const AsyncValue.data(CalendarMonthState(events: [], truncated: true)),
      _month,
    )));

    expect(find.text('일정이 너무 많아 일부만 표시합니다.'), findsOneWidget);
  });
}
