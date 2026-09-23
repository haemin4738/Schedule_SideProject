import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/features/jobapplications/job_application_status.dart';
import 'package:mobile/features/jobapplications/provider/job_applications_provider.dart';

class JobApplicationsPage extends ConsumerStatefulWidget {
  const JobApplicationsPage({super.key});

  @override
  ConsumerState<JobApplicationsPage> createState() =>
      _JobApplicationsPageState();
}

class _JobApplicationsPageState extends ConsumerState<JobApplicationsPage> {
  @override
  Widget build(BuildContext context) {
    final stateAsync = ref.watch(jobApplicationsProvider);
    final notifier = ref.read(jobApplicationsProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('구직활동'),
        actions: [
          PopupMenuButton<JobApplicationStatus?>(
            icon: const Icon(Icons.filter_list),
            onSelected: (status) => notifier.filterByStatus(status),
            itemBuilder: (context) => [
              const PopupMenuItem(value: null, child: Text('전체')),
              ...JobApplicationStatus.values.map(
                (s) => PopupMenuItem(value: s, child: Text(s.toKoreanLabel())),
              ),
            ],
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: notifier.refresh,
          ),
        ],
      ),
      body: stateAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (data) {
          if (data.items.isEmpty) {
            return const Center(child: Text('구직활동 기록이 없습니다.'));
          }
          return Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: data.items.length,
                  itemBuilder: (context, index) {
                    final item = data.items[index];
                    return Dismissible(
                      key: ValueKey(item.id),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        color: Colors.red,
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        child: const Icon(Icons.delete, color: Colors.white),
                      ),
                      confirmDismiss: (_) => _confirmDelete(context),
                      onDismissed: (_) => notifier.delete(item.id),
                      child: ListTile(
                        title: Text('${item.companyName} · ${item.position}'),
                        subtitle: Text(item.appliedAt),
                        trailing: Chip(label: Text(item.status.toKoreanLabel())),
                        onTap: () => _openForm(context, ref, existingId: item.id),
                      ),
                    );
                  },
                ),
              ),
              if (data.totalPages > 1)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.chevron_left),
                        onPressed: data.page > 0
                            ? () => notifier.goToPage(data.page - 1)
                            : null,
                      ),
                      Text('${data.page + 1} / ${data.totalPages}'),
                      IconButton(
                        icon: const Icon(Icons.chevron_right),
                        onPressed: data.page < data.totalPages - 1
                            ? () => notifier.goToPage(data.page + 1)
                            : null,
                      ),
                    ],
                  ),
                ),
            ],
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _openForm(context, ref),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<bool> _confirmDelete(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('삭제 확인'),
        content: const Text('이 지원 기록을 삭제할까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _openForm(BuildContext context, WidgetRef ref, {int? existingId}) async {
    JobApplicationItem? existing;
    if (existingId != null) {
      try {
        existing =
            await ref.read(jobApplicationsProvider.notifier).getDetail(existingId);
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('조회 실패: $e')));
        }
        return;
      }
    }
    if (!context.mounted) return;
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => _JobApplicationFormSheet(existing: existing),
    );
  }
}

class _JobApplicationFormSheet extends ConsumerStatefulWidget {
  final JobApplicationItem? existing;

  const _JobApplicationFormSheet({this.existing});

  @override
  ConsumerState<_JobApplicationFormSheet> createState() =>
      _JobApplicationFormSheetState();
}

class _JobApplicationFormSheetState
    extends ConsumerState<_JobApplicationFormSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _companyNameController;
  late final TextEditingController _positionController;
  late final TextEditingController _jobPostingUrlController;
  late final TextEditingController _memoController;
  late JobApplicationStatus _status;
  late DateTime _appliedAt;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _companyNameController =
        TextEditingController(text: existing?.companyName ?? '');
    _positionController = TextEditingController(text: existing?.position ?? '');
    _jobPostingUrlController =
        TextEditingController(text: existing?.jobPostingUrl ?? '');
    _memoController = TextEditingController(text: existing?.memo ?? '');
    _status = existing?.status ?? JobApplicationStatus.APPLIED;
    _appliedAt = existing != null
        ? DateTime.parse(existing.appliedAt)
        : DateTime.now();
  }

  @override
  void dispose() {
    _companyNameController.dispose();
    _positionController.dispose();
    _jobPostingUrlController.dispose();
    _memoController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _appliedAt,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (picked != null) setState(() => _appliedAt = picked);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _submitting = true);
    final notifier = ref.read(jobApplicationsProvider.notifier);
    final appliedAt = DateFormat('yyyy-MM-dd').format(_appliedAt);
    final jobPostingUrl = _jobPostingUrlController.text.trim();
    final memo = _memoController.text.trim();
    try {
      if (widget.existing == null) {
        await notifier.create(
          companyName: _companyNameController.text.trim(),
          position: _positionController.text.trim(),
          status: _status,
          appliedAt: appliedAt,
          jobPostingUrl: jobPostingUrl.isEmpty ? null : jobPostingUrl,
          memo: memo.isEmpty ? null : memo,
        );
      } else {
        await notifier.update(
          widget.existing!.id,
          companyName: _companyNameController.text.trim(),
          position: _positionController.text.trim(),
          status: _status,
          appliedAt: appliedAt,
          jobPostingUrl: jobPostingUrl.isEmpty ? null : jobPostingUrl,
          memo: memo.isEmpty ? null : memo,
        );
      }
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('저장 실패: $e')));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.existing == null ? '지원 기록 추가' : '지원 기록 수정',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _companyNameController,
                decoration: const InputDecoration(labelText: '회사명'),
                validator: (v) => (v == null || v.trim().isEmpty) ? '필수입니다.' : null,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _positionController,
                decoration: const InputDecoration(labelText: '지원 직무'),
                validator: (v) => (v == null || v.trim().isEmpty) ? '필수입니다.' : null,
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<JobApplicationStatus>(
                initialValue: _status,
                decoration: const InputDecoration(labelText: '상태'),
                items: JobApplicationStatus.values
                    .map((s) => DropdownMenuItem(
                          value: s,
                          child: Text(s.toKoreanLabel()),
                        ))
                    .toList(),
                onChanged: (v) {
                  if (v != null) setState(() => _status = v);
                },
              ),
              const SizedBox(height: 8),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('지원일'),
                subtitle: Text(DateFormat('yyyy-MM-dd').format(_appliedAt)),
                trailing: const Icon(Icons.calendar_today),
                onTap: _pickDate,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _jobPostingUrlController,
                decoration: const InputDecoration(labelText: '채용공고 URL (선택)'),
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _memoController,
                decoration: const InputDecoration(labelText: '메모 (선택)'),
                maxLines: 3,
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
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
