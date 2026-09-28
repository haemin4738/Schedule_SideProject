import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

const _descriptionMaxLength = 200;
const _memoMaxLength = 10000;

/// 내역 생성/수정 bottom sheet. 저장 성공 시 true 를 반환하며 닫힌다.
/// 수정 시 [existing] 은 반드시 단건 조회(getDetail) 결과여야 memo 가 유실되지 않는다.
class ExpenseFormSheet extends ConsumerStatefulWidget {
  final ExpenseItem? existing;
  final DateTime defaultDate;
  final VoidCallback onManageCategories;

  const ExpenseFormSheet({
    super.key,
    this.existing,
    required this.defaultDate,
    required this.onManageCategories,
  });

  @override
  ConsumerState<ExpenseFormSheet> createState() => _ExpenseFormSheetState();
}

class _ExpenseFormSheetState extends ConsumerState<ExpenseFormSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _amountController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _memoController;
  late ExpenseType _type;
  int? _categoryId;
  late DateTime _transactionDate;
  bool _submitting = false;
  String? _serverError;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _amountController =
        TextEditingController(text: existing?.amount.toString() ?? '');
    _descriptionController =
        TextEditingController(text: existing?.description ?? '');
    _memoController = TextEditingController(text: existing?.memo ?? '');
    _type = existing?.type ?? ExpenseType.EXPENSE;
    _categoryId = existing?.categoryId;
    _transactionDate = existing != null
        ? DateTime.parse(existing.transactionDate)
        : widget.defaultDate;
  }

  @override
  void dispose() {
    _amountController.dispose();
    _descriptionController.dispose();
    _memoController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _transactionDate,
      // 기존 내역의 날짜가 범위 밖이어도 달력이 열리도록 범위를 넉넉히 잡는다
      firstDate: DateTime(1900),
      lastDate: DateTime(2100),
    );
    if (picked != null) setState(() => _transactionDate = picked);
  }

  String? _validateAmount(String? value) {
    if (value == null || value.isEmpty) return '금액을 입력하세요.';
    final amount = int.tryParse(value);
    if (amount == null ||
        amount < minExpenseAmount ||
        amount > maxExpenseAmount) {
      return '금액은 ${formatAmount(minExpenseAmount)} 이상 '
          '${formatAmount(maxExpenseAmount)} 이하로 입력하세요.';
    }
    return null;
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _serverError = null;
    });
    final notifier = ref.read(expensesProvider.notifier);
    final transactionDate = DateFormat('yyyy-MM-dd').format(_transactionDate);
    final description = _descriptionController.text.trim();
    final memo = _memoController.text.trim();
    final amount = int.parse(_amountController.text);
    try {
      if (widget.existing == null) {
        await notifier.create(
          type: _type,
          categoryId: _categoryId!,
          amount: amount,
          transactionDate: transactionDate,
          description: description.isEmpty ? null : description,
          memo: memo.isEmpty ? null : memo,
        );
      } else {
        await notifier.update(
          widget.existing!.id,
          type: _type,
          categoryId: _categoryId!,
          amount: amount,
          transactionDate: transactionDate,
          description: description.isEmpty ? null : description,
          memo: memo.isEmpty ? null : memo,
        );
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _serverError = expenseErrorMessage(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final categoriesAsync = ref.watch(expenseCategoriesProvider);
    final categories = (categoriesAsync.valueOrNull ?? const [])
        .where((c) => c.type == _type)
        .toList();
    final noCategories = categoriesAsync.hasValue && categories.isEmpty;
    final canSubmit = !_submitting && categoriesAsync.hasValue && !noCategories;
    // 목록에 없는 id 를 초기값으로 주면 Dropdown assert 가 터지므로 방어한다.
    final selectedCategoryId =
        categories.any((c) => c.id == _categoryId) ? _categoryId : null;

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
                widget.existing == null ? '내역 추가' : '내역 수정',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              SegmentedButton<ExpenseType>(
                segments: ExpenseType.values
                    .map((t) => ButtonSegment(
                          value: t,
                          label: Text(t.toKoreanLabel()),
                        ))
                    .toList(),
                selected: {_type},
                onSelectionChanged: (selected) => setState(() {
                  _type = selected.first;
                  // 카테고리는 유형별로 다르므로 유형이 바뀌면 선택을 해제한다.
                  _categoryId = null;
                }),
              ),
              const SizedBox(height: 8),
              if (categoriesAsync.isLoading && !categoriesAsync.hasValue)
                const LinearProgressIndicator()
              else if (categoriesAsync.hasError && !categoriesAsync.hasValue)
                Text(
                  '카테고리 조회 실패: ${expenseErrorMessage(categoriesAsync.error!)}',
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                )
              else if (noCategories)
                Row(
                  children: [
                    const Expanded(child: Text('먼저 카테고리를 추가하세요')),
                    TextButton(
                      onPressed: () {
                        Navigator.pop(context);
                        widget.onManageCategories();
                      },
                      child: const Text('카테고리 관리'),
                    ),
                  ],
                )
              else
                DropdownButtonFormField<int>(
                  // 유형이 바뀌면 새 필드로 다시 만들어 선택값을 확실히 초기화한다.
                  key: ValueKey(_type),
                  initialValue: selectedCategoryId,
                  decoration: const InputDecoration(labelText: '카테고리'),
                  items: categories
                      .map((c) => DropdownMenuItem(
                            value: c.id,
                            child: Text(c.name),
                          ))
                      .toList(),
                  onChanged: (v) => setState(() => _categoryId = v),
                  validator: (v) => v == null ? '카테고리를 선택하세요.' : null,
                ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _amountController,
                decoration:
                    const InputDecoration(labelText: '금액 (원)', suffixText: '원'),
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: _validateAmount,
              ),
              const SizedBox(height: 8),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('날짜'),
                subtitle:
                    Text(DateFormat('yyyy-MM-dd').format(_transactionDate)),
                trailing: const Icon(Icons.calendar_today),
                onTap: _pickDate,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _descriptionController,
                decoration: const InputDecoration(labelText: '설명 (선택)'),
                maxLength: _descriptionMaxLength,
                validator: (v) => (v != null && v.length > _descriptionMaxLength)
                    ? '설명은 $_descriptionMaxLength자 이하로 입력하세요.'
                    : null,
              ),
              TextFormField(
                controller: _memoController,
                decoration: const InputDecoration(labelText: '메모 (선택)'),
                minLines: 3,
                maxLines: 6,
                maxLength: _memoMaxLength,
                validator: (v) => (v != null && v.length > _memoMaxLength)
                    ? '메모는 $_memoMaxLength자 이하로 입력하세요.'
                    : null,
              ),
              if (_serverError != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    _serverError!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: canSubmit ? _submit : null,
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
