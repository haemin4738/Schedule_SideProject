import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

const _nameMaxLength = 50;

class ExpenseCategoriesPage extends ConsumerStatefulWidget {
  const ExpenseCategoriesPage({super.key});

  @override
  ConsumerState<ExpenseCategoriesPage> createState() =>
      _ExpenseCategoriesPageState();
}

class _ExpenseCategoriesPageState extends ConsumerState<ExpenseCategoriesPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController =
      TabController(length: ExpenseType.values.length, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final categoriesAsync = ref.watch(expenseCategoriesProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('카테고리 관리'),
        bottom: TabBar(
          controller: _tabController,
          tabs: ExpenseType.values
              .map((t) => Tab(text: t.toKoreanLabel()))
              .toList(),
        ),
      ),
      body: categoriesAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: ${expenseErrorMessage(e)}')),
        data: (categories) => TabBarView(
          controller: _tabController,
          children: ExpenseType.values.map((type) {
            final items = categories.where((c) => c.type == type).toList();
            if (items.isEmpty) {
              return Center(
                child: Text('${type.toKoreanLabel()} 카테고리가 없습니다'),
              );
            }
            return ListView.builder(
              itemCount: items.length,
              itemBuilder: (context, index) {
                final category = items[index];
                return ListTile(
                  title: Text(category.name),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.edit_outlined),
                        tooltip: '이름 변경',
                        onPressed: () => _rename(category),
                      ),
                      IconButton(
                        icon: const Icon(Icons.delete_outline),
                        tooltip: '삭제',
                        onPressed: () => _delete(category),
                      ),
                    ],
                  ),
                );
              },
            );
          }).toList(),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _add,
        icon: const Icon(Icons.add),
        label: const Text('카테고리 추가'),
      ),
    );
  }

  /// 서버 오류(중복 이름 409, 100개 초과 409, 사용 중 삭제 409 등)는 메시지를 그대로 보여준다.
  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(expenseErrorMessage(e))));
      }
    }
  }

  Future<void> _add() async {
    final result = await showDialog<(ExpenseType, String)>(
      context: context,
      builder: (_) => _CategoryNameDialog(
        title: '카테고리 추가',
        initialType: ExpenseType.values[_tabController.index],
      ),
    );
    if (result == null) return;
    final (type, name) = result;
    await _run(() => ref
        .read(expenseCategoriesProvider.notifier)
        .create(type: type, name: name));
  }

  Future<void> _rename(ExpenseCategory category) async {
    final result = await showDialog<(ExpenseType, String)>(
      context: context,
      builder: (_) => _CategoryNameDialog(
        title: '이름 변경',
        initialName: category.name,
      ),
    );
    if (result == null) return;
    await _run(() => ref
        .read(expenseCategoriesProvider.notifier)
        .rename(category.id, result.$2));
  }

  Future<void> _delete(ExpenseCategory category) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('삭제 확인'),
        content: Text("'${category.name}' 카테고리를 삭제할까요?"),
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
    if (confirmed != true) return;
    await _run(
        () => ref.read(expenseCategoriesProvider.notifier).delete(category.id));
  }
}

/// 카테고리 이름 입력 다이얼로그. [initialType] 이 있으면 유형 선택(추가용)을 함께 보여준다.
/// 유형은 변경 불가(백엔드 규칙)이므로 이름 변경 시에는 유형 선택을 숨긴다.
class _CategoryNameDialog extends StatefulWidget {
  final String title;
  final ExpenseType? initialType;
  final String initialName;

  const _CategoryNameDialog({
    required this.title,
    this.initialType,
    this.initialName = '',
  });

  @override
  State<_CategoryNameDialog> createState() => _CategoryNameDialogState();
}

class _CategoryNameDialogState extends State<_CategoryNameDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController =
      TextEditingController(text: widget.initialName);
  late ExpenseType _type = widget.initialType ?? ExpenseType.EXPENSE;

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.pop(context, (_type, _nameController.text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.initialType != null) ...[
              SegmentedButton<ExpenseType>(
                segments: ExpenseType.values
                    .map((t) => ButtonSegment(
                          value: t,
                          label: Text(t.toKoreanLabel()),
                        ))
                    .toList(),
                selected: {_type},
                onSelectionChanged: (s) => setState(() => _type = s.first),
              ),
              const SizedBox(height: 8),
            ],
            TextFormField(
              controller: _nameController,
              autofocus: true,
              maxLength: _nameMaxLength,
              decoration: const InputDecoration(labelText: '이름'),
              validator: (v) {
                final name = v?.trim() ?? '';
                if (name.isEmpty) return '이름을 입력하세요.';
                if (name.length > _nameMaxLength) {
                  return '이름은 $_nameMaxLength자 이하로 입력하세요.';
                }
                return null;
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('취소'),
        ),
        TextButton(onPressed: _submit, child: const Text('저장')),
      ],
    );
  }
}
