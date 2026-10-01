import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/presentation/expense_categories_page.dart';
import 'package:mobile/features/expenses/presentation/expense_form_sheet.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

const _incomeColor = Colors.blue;
const _expenseColor = Colors.red;

class ExpensesPage extends ConsumerStatefulWidget {
  /// 캘린더에서 넘어올 때 보여줄 달 (없으면 이번 달)
  final DateTime? initialMonth;

  const ExpensesPage({super.key, this.initialMonth});

  /// `?month=yyyy-MM` → 그 달 1일. 형식이 틀리면 null(이번 달)
  static DateTime? parseMonth(String? value) {
    final m = RegExp(r'^(\d{4})-(0[1-9]|1[0-2])$').firstMatch(value ?? '');
    return m == null ? null : DateTime(int.parse(m[1]!), int.parse(m[2]!));
  }

  @override
  ConsumerState<ExpensesPage> createState() => _ExpensesPageState();
}

class _ExpensesPageState extends ConsumerState<ExpensesPage> {
  @override
  void initState() {
    super.initState();
    final month = widget.initialMonth;
    if (month == null) return;
    // provider 는 이번 달로 만들어지므로 첫 프레임 뒤에 요청한 달로 옮긴다
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final current = ref.read(expensesProvider.notifier).month;
      if (current.year == month.year && current.month == month.month) return;
      _changeMonth(current, (month.year - current.year) * 12 + month.month - current.month);
    });
  }

  @override
  Widget build(BuildContext context) {
    final expensesAsync = ref.watch(expensesProvider);
    final summaryAsync = ref.watch(expenseSummaryProvider);
    final categoriesAsync = ref.watch(expenseCategoriesProvider);
    final notifier = ref.read(expensesProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('가계부'),
        actions: [
          IconButton(
            icon: const Icon(Icons.category_outlined),
            tooltip: '카테고리 관리',
            onPressed: _openCategories,
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '새로고침',
            onPressed: _refreshAll,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 88),
        children: [
          _MonthSelector(
            month: notifier.month,
            onChange: (delta) => _changeMonth(notifier.month, delta),
          ),
          _SummaryCard(summaryAsync: summaryAsync),
          _Filters(
            type: notifier.typeFilter,
            categoryId: notifier.categoryIdFilter,
            categories: categoriesAsync.valueOrNull ?? const [],
            onTypeChanged: (type) => notifier.filter(type: type),
            onCategoryChanged: (id) =>
                notifier.filter(type: notifier.typeFilter, categoryId: id),
          ),
          const Divider(height: 1),
          ..._buildList(expensesAsync, notifier),
          const Divider(height: 1),
          _CategoryBreakdown(summaryAsync: summaryAsync),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(),
        icon: const Icon(Icons.add),
        label: const Text('내역 추가'),
      ),
    );
  }

  List<Widget> _buildList(
    AsyncValue<ExpensesState> expensesAsync,
    ExpensesNotifier notifier,
  ) {
    return expensesAsync.when(
      loading: () => const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      ],
      error: (e, _) => [
        Padding(
          padding: const EdgeInsets.all(24),
          child: Center(child: Text('오류: ${expenseErrorMessage(e)}')),
        ),
      ],
      data: (data) {
        final pagination = data.totalPages > 1
            ? _Pagination(
                page: data.page,
                totalPages: data.totalPages,
                onPageChanged: notifier.goToPage,
              )
            : null;
        if (data.items.isEmpty) {
          return [
            const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: Text('내역이 없습니다')),
            ),
            ?pagination,
          ];
        }
        return [
          ...data.items.map((item) => Dismissible(
                key: ValueKey(item.id),
                direction: DismissDirection.endToStart,
                background: Container(
                  color: Colors.red,
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: const Icon(Icons.delete, color: Colors.white),
                ),
                confirmDismiss: (_) => _confirmDelete(context),
                onDismissed: (_) => _delete(item.id),
                child: _ExpenseRow(
                  item: item,
                  onTap: () => _openForm(existingId: item.id),
                ),
              )),
          ?pagination,
        ];
      },
    );
  }

  void _changeMonth(DateTime current, int delta) {
    final next = DateTime(current.year, current.month + delta);
    ref.read(expensesProvider.notifier).changeMonth(next);
    ref.read(expenseSummaryProvider.notifier).fetch(next);
  }

  Future<void> _refreshAll() async {
    await Future.wait([
      ref.read(expensesProvider.notifier).refresh(),
      ref.read(expenseSummaryProvider.notifier).refresh(),
      ref.read(expenseCategoriesProvider.notifier).fetch(),
    ]);
  }

  Future<bool> _confirmDelete(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('삭제 확인'),
        content: const Text('이 내역을 삭제할까요?'),
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

  Future<void> _delete(int id) async {
    try {
      await ref.read(expensesProvider.notifier).delete(id);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(expenseErrorMessage(e))));
      }
    } finally {
      if (mounted) ref.read(expenseSummaryProvider.notifier).refresh();
    }
  }

  /// 기본 날짜: 선택 월이 이번 달이면 오늘, 아니면 그 달 1일.
  DateTime _defaultDate() {
    final month = ref.read(expensesProvider.notifier).month;
    final now = DateTime.now();
    if (month.year == now.year && month.month == now.month) {
      return DateTime(now.year, now.month, now.day);
    }
    return month;
  }

  Future<void> _openForm({int? existingId}) async {
    ExpenseItem? existing;
    if (existingId != null) {
      // 목록 응답에는 memo 가 없으므로 반드시 단건 조회 결과로 폼을 채운다.
      try {
        existing =
            await ref.read(expensesProvider.notifier).getDetail(existingId);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('조회 실패: ${expenseErrorMessage(e)}')),
          );
        }
        return;
      }
    }
    if (!mounted) return;
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => ExpenseFormSheet(
        existing: existing,
        defaultDate: _defaultDate(),
        onManageCategories: _openCategories,
      ),
    );
    // 목록은 notifier.create/update 가 이미 재조회하므로 요약만 갱신한다.
    if (saved == true && mounted) {
      ref.read(expenseSummaryProvider.notifier).refresh();
    }
  }

  Future<void> _openCategories() async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const ExpenseCategoriesPage()),
    );
    if (!mounted) return;
    // 카테고리 이름이 바뀌었거나 선택 중인 카테고리가 삭제됐을 수 있으므로 목록/요약을 다시 조회한다.
    ref.read(expensesProvider.notifier).refreshAfterCategoryChange(
        ref.read(expenseCategoriesProvider).valueOrNull);
    ref.read(expenseSummaryProvider.notifier).refresh();
  }
}

class _Pagination extends StatelessWidget {
  final int page;
  final int totalPages;
  final ValueChanged<int> onPageChanged;

  const _Pagination({
    required this.page,
    required this.totalPages,
    required this.onPageChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            icon: const Icon(Icons.chevron_left),
            tooltip: '이전 페이지',
            onPressed: page > 0 ? () => onPageChanged(page - 1) : null,
          ),
          Text('${page + 1} / $totalPages'),
          IconButton(
            icon: const Icon(Icons.chevron_right),
            tooltip: '다음 페이지',
            onPressed:
                page < totalPages - 1 ? () => onPageChanged(page + 1) : null,
          ),
        ],
      ),
    );
  }
}

class _MonthSelector extends StatelessWidget {
  final DateTime month;
  final ValueChanged<int> onChange;

  const _MonthSelector({required this.month, required this.onChange});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        IconButton(
          icon: const Icon(Icons.chevron_left),
          tooltip: '이전 달',
          onPressed: () => onChange(-1),
        ),
        Text(
          '${month.year}년 ${month.month}월',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        IconButton(
          icon: const Icon(Icons.chevron_right),
          tooltip: '다음 달',
          onPressed: () => onChange(1),
        ),
      ],
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final AsyncValue<ExpenseSummaryState> summaryAsync;

  const _SummaryCard({required this.summaryAsync});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: summaryAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('요약 조회 실패: ${expenseErrorMessage(e)}'),
          data: (s) => Row(
            children: [
              _SummaryCell(label: '수입', amount: s.totalIncome, color: _incomeColor),
              _SummaryCell(label: '지출', amount: s.totalExpense, color: _expenseColor),
              _SummaryCell(
                label: '합계',
                amount: s.net,
                color: s.net < 0 ? _expenseColor : null,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SummaryCell extends StatelessWidget {
  final String label;
  final int amount;
  final Color? color;

  const _SummaryCell({required this.label, required this.amount, this.color});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 4),
          FittedBox(
            child: Text(
              formatAmount(amount),
              style: TextStyle(fontWeight: FontWeight.bold, color: color),
            ),
          ),
        ],
      ),
    );
  }
}

class _Filters extends StatelessWidget {
  final ExpenseType? type;
  final int? categoryId;
  final List<ExpenseCategory> categories;
  final ValueChanged<ExpenseType?> onTypeChanged;
  final ValueChanged<int?> onCategoryChanged;

  const _Filters({
    required this.type,
    required this.categoryId,
    required this.categories,
    required this.onTypeChanged,
    required this.onCategoryChanged,
  });

  @override
  Widget build(BuildContext context) {
    final options = type == null
        ? categories
        : categories.where((c) => c.type == type).toList();
    final selectedId = options.any((c) => c.id == categoryId) ? categoryId : null;
    const typeOptions = <ExpenseType?>[null, ExpenseType.EXPENSE, ExpenseType.INCOME];

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Wrap(
        spacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          ...typeOptions.map((t) => ChoiceChip(
                label: Text(t?.toKoreanLabel() ?? '전체'),
                selected: type == t,
                // 유형을 바꾸면 카테고리 필터는 초기화된다 (onTypeChanged 가 categoryId 없이 조회)
                onSelected: (_) => onTypeChanged(t),
              )),
          DropdownButton<int?>(
            value: selectedId,
            hint: const Text('카테고리 전체'),
            items: [
              const DropdownMenuItem<int?>(value: null, child: Text('카테고리 전체')),
              ...options.map((c) => DropdownMenuItem<int?>(
                    value: c.id,
                    child: Text(type == null
                        ? '${c.name} (${c.type.toKoreanLabel()})'
                        : c.name),
                  )),
            ],
            onChanged: onCategoryChanged,
          ),
        ],
      ),
    );
  }
}

class _ExpenseRow extends StatelessWidget {
  final ExpenseItem item;
  final VoidCallback onTap;

  const _ExpenseRow({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final date = DateTime.parse(item.transactionDate);
    final description = item.description;
    return ListTile(
      leading: SizedBox(
        width: 48,
        child: Center(child: Text('${date.month}/${date.day}')),
      ),
      title: Text(item.categoryName),
      subtitle: Text(
        description == null || description.isEmpty ? '-' : description,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Text(
        formatSignedAmount(item.type, item.amount),
        style: TextStyle(
          fontWeight: FontWeight.bold,
          color: item.type == ExpenseType.INCOME ? _incomeColor : _expenseColor,
        ),
      ),
      onTap: onTap,
    );
  }
}

class _CategoryBreakdown extends StatelessWidget {
  final AsyncValue<ExpenseSummaryState> summaryAsync;

  const _CategoryBreakdown({required this.summaryAsync});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('카테고리별 지출', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          summaryAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Text('조회 실패: ${expenseErrorMessage(e)}'),
            data: (s) {
              if (s.categories.isEmpty || s.categoryTotal <= 0) {
                return const Text('지출 내역이 없습니다');
              }
              return Column(
                children: s.categories.map((c) {
                  final ratio = c.amount / s.categoryTotal;
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(child: Text(c.categoryName)),
                            Text('${c.count}건 · '),
                            Text(formatAmount(c.amount)),
                            SizedBox(
                              width: 48,
                              child: Text(
                                '${(ratio * 100).round()}%',
                                textAlign: TextAlign.end,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        LinearProgressIndicator(
                          value: ratio,
                          color: _expenseColor,
                          backgroundColor: _expenseColor.withValues(alpha: 0.1),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              );
            },
          ),
        ],
      ),
    );
  }
}
