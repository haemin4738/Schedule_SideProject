import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';
import 'package:mobile/features/calendar/presentation/event_form_sheet.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

const _weekdays = ['일', '월', '화', '수', '목', '금', '토'];
final _timeFormat = DateFormat('HH:mm');

bool _sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

/// 일요일·공휴일은 빨강, 토요일은 파랑 (웹 캘린더와 같은 규칙)
Color dayColor(DateTime day, {required bool holiday}) {
  if (day.weekday == DateTime.sunday || holiday) return Colors.red.shade600;
  if (day.weekday == DateTime.saturday) return Colors.blue.shade600;
  return Colors.black87;
}

class CalendarPage extends ConsumerStatefulWidget {
  const CalendarPage({super.key});

  @override
  ConsumerState<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends ConsumerState<CalendarPage> {
  Timer? _timer;
  DateTime _selected = DateTime.now();

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

  void _changeMonth(int delta) {
    final notifier = ref.read(eventsProvider.notifier);
    notifier.changeMonth(delta);
    // 넘긴 달의 1일을 고른다 (오늘이 그 달이면 오늘)
    final now = DateTime.now();
    setState(() {
      _selected = now.year == notifier.month.year && now.month == notifier.month.month
          ? now
          : notifier.month;
    });
  }

  /// 생성(existing 없음) 또는 수정 시트를 연다. 수정은 설명·장소가 유실되지 않게 단건을 조회한 뒤 연다
  // 단건 조회를 기다리는 동안 연달아 눌러 시트가 여러 개 열리지 않게 한다
  bool _opening = false;

  Future<void> _openForm({EventItem? item}) async {
    if (_opening) return;
    _opening = true;
    try {
      await _showForm(item);
    } finally {
      _opening = false;
    }
  }

  Future<void> _showForm(EventItem? item) async {
    EventDetail? detail;
    if (item != null) {
      try {
        detail = await ref.read(eventsProvider.notifier).getDetail(item.id);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(eventErrorMessage(e))));
        }
        return;
      }
    }
    if (!mounted) return;
    await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => EventFormSheet(existing: detail, defaultDate: _selected),
    );
  }

  void _goToToday() {
    ref.read(eventsProvider.notifier).goToToday();
    setState(() => _selected = DateTime.now());
  }

  @override
  Widget build(BuildContext context) {
    final eventsAsync = ref.watch(eventsProvider);
    final month = ref.read(eventsProvider.notifier).month;

    return Scaffold(
      floatingActionButton: FloatingActionButton(
        tooltip: '일정 추가',
        onPressed: () => _openForm(),
        child: const Icon(Icons.add),
      ),
      appBar: AppBar(
        title: const Text('캘린더'),
        actions: [
          IconButton(
            icon: const Icon(Icons.work_outline),
            tooltip: '구직활동',
            onPressed: () => context.push('/job-applications'),
          ),
          IconButton(
            icon: const Icon(Icons.account_balance_wallet_outlined),
            tooltip: '가계부',
            onPressed: () => context.push('/expenses'),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '새로고침',
            onPressed: () => ref.read(eventsProvider.notifier).refresh(),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: '로그아웃',
            onPressed: () => ref.read(authProvider.notifier).logout(),
          ),
        ],
      ),
      body: Column(
        children: [
          _MonthHeader(
            month: month,
            onPrev: () => _changeMonth(-1),
            onNext: () => _changeMonth(1),
            onToday: _goToToday,
          ),
          const _WeekdayRow(),
          Expanded(
            child: eventsAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('일정을 불러오지 못했습니다.'),
                    TextButton(
                      onPressed: () => ref.read(eventsProvider.notifier).refresh(),
                      child: const Text('다시 시도'),
                    ),
                  ],
                ),
              ),
              data: (data) => _MonthBody(
                month: month,
                data: data,
                selected: _selected,
                onSelect: (day) => setState(() => _selected = day),
                onOpenEvent: (item) => _openForm(item: item),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MonthHeader extends StatelessWidget {
  const _MonthHeader({
    required this.month,
    required this.onPrev,
    required this.onNext,
    required this.onToday,
  });

  final DateTime month;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final VoidCallback onToday;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(
        children: [
          IconButton(icon: const Icon(Icons.chevron_left), tooltip: '이전 달', onPressed: onPrev),
          Text(
            '${month.year}년 ${month.month}월',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          IconButton(icon: const Icon(Icons.chevron_right), tooltip: '다음 달', onPressed: onNext),
          const Spacer(),
          OutlinedButton(onPressed: onToday, child: const Text('오늘')),
        ],
      ),
    );
  }
}

class _WeekdayRow extends StatelessWidget {
  const _WeekdayRow();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (var i = 0; i < 7; i++)
          Expanded(
            child: Center(
              child: Text(
                _weekdays[i],
                style: TextStyle(
                  fontSize: 12,
                  color: i == 0
                      ? Colors.red.shade600
                      : i == 6
                          ? Colors.blue.shade600
                          : Colors.black54,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _MonthBody extends StatelessWidget {
  const _MonthBody({
    required this.month,
    required this.data,
    required this.selected,
    required this.onSelect,
    required this.onOpenEvent,
  });

  final DateTime month;
  final CalendarMonthState data;
  final DateTime selected;
  final ValueChanged<DateTime> onSelect;
  final ValueChanged<EventItem> onOpenEvent;

  @override
  Widget build(BuildContext context) {
    final start = gridStart(month);
    final days = [for (var i = 0; i < gridDays; i++) DateTime(start.year, start.month, start.day + i)];
    return LayoutBuilder(
      builder: (context, constraints) {
        // 칸은 세로로 약간 긴 정도가 보기 좋지만, 가로 화면·태블릿에서는 아래 일정 목록 자리를 남기도록 높이를 제한한다
        final gridHeight = (constraints.maxWidth / 7 * 1.15 * 6).clamp(0.0, constraints.maxHeight * 0.6);
        return Column(
          children: [
            if (data.truncated)
              Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  '일정이 너무 많아 일부만 표시합니다.',
                  style: TextStyle(color: Colors.orange.shade800, fontSize: 12),
                ),
              ),
            SizedBox(
              height: gridHeight,
              child: Column(
                children: [
                  for (var week = 0; week < 6; week++)
                    Expanded(
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          for (final day in days.sublist(week * 7, week * 7 + 7))
                            Expanded(
                              child: _DayCell(
                                day: day,
                                inMonth: day.month == month.month,
                                selected: _sameDay(day, selected),
                                data: data,
                                onTap: () => onSelect(day),
                              ),
                            ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(child: _DayDetail(day: selected, data: data, onOpenEvent: onOpenEvent)),
          ],
        );
      },
    );
  }
}

class _DayCell extends StatelessWidget {
  const _DayCell({
    required this.day,
    required this.inMonth,
    required this.selected,
    required this.data,
    required this.onTap,
  });

  final DateTime day;
  final bool inMonth;
  final bool selected;
  final CalendarMonthState data;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final events = data.eventsOn(day);
    final specials = data.specialDaysOn(day);
    final holidays = specials.where((d) => d.holiday).map((d) => d.name).toList();
    final isToday = _sameDay(day, DateTime.now());
    final color = dayColor(day, holiday: holidays.isNotEmpty);
    final label = [
      '${day.month}월 ${day.day}일 ${_weekdays[day.weekday % 7]}요일',
      ...holidays,
      if (events.isNotEmpty) '일정 ${events.length}개',
    ].join(', ');

    return Semantics(
      button: true,
      selected: selected,
      label: label,
      excludeSemantics: true,
      // 하위 InkWell 의 탭 동작이 제외되므로 스크린리더용 탭을 직접 연결한다
      onTap: onTap,
      child: InkWell(
        onTap: onTap,
        child: Opacity(
          opacity: inMonth ? 1 : 0.4,
          child: Container(
            decoration: BoxDecoration(
              color: selected ? Colors.blue.shade50 : null,
              border: Border.all(color: Colors.grey.shade200, width: 0.5),
            ),
            padding: const EdgeInsets.all(2),
            child: Column(
              children: [
                Container(
                  width: 22,
                  height: 22,
                  alignment: Alignment.center,
                  decoration: isToday
                      ? BoxDecoration(color: Colors.blue.shade600, shape: BoxShape.circle)
                      : null,
                  child: Text(
                    '${day.day}',
                    style: TextStyle(fontSize: 12, color: isToday ? Colors.white : color),
                  ),
                ),
                // 가로 화면처럼 칸이 낮으면 특일 이름·일정 점은 줄어들거나 잘린다 (날짜 숫자는 항상 보인다)
                if (specials.isNotEmpty)
                  Flexible(
                    child: Text(
                      specials.first.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 9,
                        color: specials.first.holiday ? Colors.red.shade600 : Colors.black45,
                      ),
                    ),
                  ),
                Expanded(
                  child: Align(
                    alignment: Alignment.bottomCenter,
                    child: events.isEmpty
                        ? null
                        : Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              for (var i = 0; i < events.length && i < 3; i++)
                                Container(
                                  width: 5,
                                  height: 5,
                                  margin: const EdgeInsets.symmetric(horizontal: 1, vertical: 2),
                                  decoration: BoxDecoration(color: events[i].displayColor, shape: BoxShape.circle),
                                ),
                            ],
                          ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 선택한 날의 특일과 일정 목록
class _DayDetail extends StatelessWidget {
  const _DayDetail({required this.day, required this.data, required this.onOpenEvent});

  final DateTime day;
  final CalendarMonthState data;
  final ValueChanged<EventItem> onOpenEvent;

  @override
  Widget build(BuildContext context) {
    final events = data.eventsOn(day);
    final specials = data.specialDaysOn(day);
    return ListView(
      // 마지막 일정이 '일정 추가' 버튼에 가리지 않게 아래 여백을 둔다
      padding: const EdgeInsets.only(bottom: 80),
      children: [
        ListTile(
          dense: true,
          title: Text(
            '${day.month}월 ${day.day}일 ${_weekdays[day.weekday % 7]}요일',
            style: TextStyle(fontWeight: FontWeight.w600, color: dayColor(day, holiday: data.isHoliday(day))),
          ),
          subtitle: specials.isEmpty ? null : Text(specials.map((d) => d.name).join(' · ')),
        ),
        if (events.isEmpty)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: Text('일정이 없습니다.')),
          ),
        for (final event in events)
          ListTile(
            leading: Icon(Icons.circle, size: 10, color: event.displayColor),
            title: Text(event.title),
            subtitle: Text(event.allDay ? '종일' : _timeLabel(event)),
            onTap: () => onOpenEvent(event),
          ),
      ],
    );
  }

  String _timeLabel(EventItem e) {
    final start = _timeFormat.format(e.startAt);
    final end = e.endAt;
    if (end == null) return start;
    // 다른 날에 끝나면 날짜도 보여준다
    final endText = _sameDay(e.startAt, end) ? _timeFormat.format(end) : DateFormat('M/d HH:mm').format(end);
    return '$start ~ $endText';
  }
}
