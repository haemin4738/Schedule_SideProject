import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/features/calendar/event_category.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';

const _titleMaxLength = 200;
const _locationMaxLength = 255;
final _dateLabel = DateFormat('yyyy-MM-dd');
final _timeLabel = DateFormat('HH:mm');

/// 일정 생성/수정 bottom sheet. 저장·삭제 성공 시 true 를 반환하며 닫힌다.
/// 수정 시 [existing] 은 반드시 단건 조회(getDetail) 결과여야 설명·장소가 유실되지 않는다.
class EventFormSheet extends ConsumerStatefulWidget {
  final EventDetail? existing;

  /// 생성 시 기본 날짜 (캘린더에서 고른 날)
  final DateTime defaultDate;

  const EventFormSheet({super.key, this.existing, required this.defaultDate});

  @override
  ConsumerState<EventFormSheet> createState() => _EventFormSheetState();
}

class _EventFormSheetState extends ConsumerState<EventFormSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _titleController;
  late final TextEditingController _locationController;
  late final TextEditingController _descriptionController;
  late bool _allDay;
  late DateTime _start;
  late DateTime _end;
  late EventCategory _category;

  // 종료 없이 저장된 일정은 폼에 임시 종료(1시간 뒤)를 보여줄 뿐이다. 종료·종일을 건드리지 않으면 종료 없음을 유지한다 (웹과 같은 규칙)
  bool _endTouched = false;
  bool _submitting = false;
  String? _serverError;

  bool get _isEdit => widget.existing != null;
  bool get _keepsNoEnd => _isEdit && widget.existing!.endAt == null && !_endTouched;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _titleController = TextEditingController(text: e?.title ?? '');
    _locationController = TextEditingController(text: e?.location ?? '');
    _descriptionController = TextEditingController(text: e?.description ?? '');
    _category = e?.eventCategory ?? EventCategory.PERSONAL;
    if (e != null) {
      _allDay = e.allDay;
      _start = e.startAt;
      _end = e.endAt ?? (e.allDay ? e.startAt : e.startAt.add(const Duration(hours: 1)));
    } else {
      // 고른 날의 다음 정각부터 1시간 (오늘이 아니면 오전 9시)
      final now = DateTime.now();
      final d = widget.defaultDate;
      final isToday = d.year == now.year && d.month == now.month && d.day == now.day;
      _allDay = false;
      _start = isToday ? DateTime(d.year, d.month, d.day, now.hour + 1) : DateTime(d.year, d.month, d.day, 9);
      _end = _start.add(const Duration(hours: 1));
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _locationController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _pickDate({required bool start}) async {
    final current = start ? _start : _end;
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(1900),
      lastDate: DateTime(2100),
    );
    if (picked == null || !mounted) return;
    setState(() {
      final next = DateTime(picked.year, picked.month, picked.day, current.hour, current.minute);
      if (start) {
        // 시작을 옮기면 기간을 유지하도록 종료도 함께 민다
        final length = _end.difference(_start);
        _start = next;
        if (!_keepsNoEnd) _end = _start.add(length);
      } else {
        _end = next;
        _endTouched = true;
      }
    });
  }

  Future<void> _pickTime({required bool start}) async {
    final current = start ? _start : _end;
    final picked = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(current));
    if (picked == null || !mounted) return;
    setState(() {
      final next = DateTime(current.year, current.month, current.day, picked.hour, picked.minute);
      if (start) {
        final length = _end.difference(_start);
        _start = next;
        if (!_keepsNoEnd) _end = _start.add(length);
      } else {
        _end = next;
        _endTouched = true;
      }
    });
  }

  /// 종일 일정은 시작일 00:00:00 ~ 종료일 23:59:59 로 저장한다 (웹 toEventRequest 와 같은 규칙)
  EventInput _input() {
    final start = _allDay ? DateTime(_start.year, _start.month, _start.day) : _start;
    final end = _allDay ? DateTime(_end.year, _end.month, _end.day, 23, 59, 59) : _end;
    final location = _locationController.text.trim();
    final description = _descriptionController.text.trim();
    return EventInput(
      title: _titleController.text.trim(),
      allDay: _allDay,
      startAt: start,
      endAt: _keepsNoEnd ? null : end,
      eventCategory: _category,
      // 앱에는 색 선택이 없으므로 웹에서 고른 색을 지우지 않게 그대로 보낸다
      color: widget.existing?.color,
      location: location.isEmpty ? null : location,
      description: description.isEmpty ? null : description,
    );
  }

  String? _endError() {
    if (_keepsNoEnd) return null;
    final input = _input();
    return input.endAt!.isBefore(input.startAt) ? '종료는 시작보다 빠를 수 없습니다.' : null;
  }

  Future<void> _submit() async {
    final endError = _endError();
    if (!_formKey.currentState!.validate() || endError != null) {
      setState(() => _serverError = endError);
      return;
    }
    setState(() {
      _submitting = true;
      _serverError = null;
    });
    final notifier = ref.read(eventsProvider.notifier);
    try {
      if (_isEdit) {
        await notifier.update(widget.existing!.id, _input());
      } else {
        await notifier.create(_input());
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _serverError = eventErrorMessage(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('일정 삭제'),
        content: Text('"${widget.existing!.title}" 일정을 삭제할까요?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('삭제')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() {
      _submitting = true;
      _serverError = null;
    });
    try {
      await ref.read(eventsProvider.notifier).delete(widget.existing!.id);
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _serverError = eventErrorMessage(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Widget _dateTimeRow({required String label, required bool start}) {
    final value = start ? _start : _end;
    final noEnd = !start && _keepsNoEnd;
    // 좁은 화면에서 '(종료 없음)' 까지 붙으면 한 줄을 넘으므로 줄바꿈되게 한다
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        SizedBox(width: 40, child: Text(label)),
        TextButton(
          onPressed: _submitting ? null : () => _pickDate(start: start),
          child: Text(_dateLabel.format(value), semanticsLabel: '$label 날짜 ${_dateLabel.format(value)}'),
        ),
        if (!_allDay)
          TextButton(
            onPressed: _submitting ? null : () => _pickTime(start: start),
            child: Text(_timeLabel.format(value), semanticsLabel: '$label 시간 ${_timeLabel.format(value)}'),
          ),
        if (noEnd) const Text('(종료 없음)', style: TextStyle(fontSize: 12, color: Colors.black54)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final errorColor = Theme.of(context).colorScheme.error;
    return Padding(
      padding: EdgeInsets.only(left: 16, right: 16, top: 16, bottom: MediaQuery.of(context).viewInsets.bottom + 16),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(_isEdit ? '일정 수정' : '새 일정', style: Theme.of(context).textTheme.titleLarge),
                  ),
                  if (_isEdit)
                    IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: '삭제',
                      onPressed: _submitting ? null : _delete,
                    ),
                ],
              ),
              TextFormField(
                controller: _titleController,
                decoration: const InputDecoration(labelText: '제목'),
                maxLength: _titleMaxLength,
                autofocus: !_isEdit,
                validator: (v) => (v == null || v.trim().isEmpty) ? '제목을 입력하세요.' : null,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('종일'),
                value: _allDay,
                onChanged: _submitting
                    ? null
                    : (v) => setState(() {
                          _allDay = v;
                          _endTouched = true;
                        }),
              ),
              _dateTimeRow(label: '시작', start: true),
              _dateTimeRow(label: '종료', start: false),
              DropdownButtonFormField<EventCategory>(
                initialValue: _category,
                decoration: const InputDecoration(labelText: '카테고리'),
                items: EventCategory.values
                    .map((c) => DropdownMenuItem(value: c, child: Text(c.label)))
                    .toList(),
                onChanged: _submitting ? null : (v) => setState(() => _category = v ?? _category),
              ),
              TextFormField(
                controller: _locationController,
                decoration: const InputDecoration(labelText: '장소 (선택)'),
                maxLength: _locationMaxLength,
              ),
              TextFormField(
                controller: _descriptionController,
                decoration: const InputDecoration(labelText: '설명 (선택)'),
                minLines: 2,
                maxLines: 5,
              ),
              if (_serverError != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_serverError!, style: TextStyle(color: errorColor)),
                ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('저장'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
