import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/jobapplications/job_application_status.dart';

/// 목록(summary) / 상세(response) 응답을 모두 이 모델 하나로 파싱한다.
/// 목록 응답에는 jobPostingUrl/memo/createdAt/updatedAt 이 내려오지 않으므로
/// 해당 필드는 nullable 로 두고, 상세 조회(GET /{id})로 채운다.
class JobApplicationItem {
  final int id;
  final String companyName;
  final String position;
  final JobApplicationStatus status;
  final String appliedAt;
  final String? jobPostingUrl;
  final String? memo;
  final String? createdAt;
  final String? updatedAt;

  const JobApplicationItem({
    required this.id,
    required this.companyName,
    required this.position,
    required this.status,
    required this.appliedAt,
    this.jobPostingUrl,
    this.memo,
    this.createdAt,
    this.updatedAt,
  });

  factory JobApplicationItem.fromJson(Map<String, dynamic> json) =>
      JobApplicationItem(
        id: json['id'] as int,
        companyName: json['companyName'] as String,
        position: json['position'] as String,
        status: JobApplicationStatus.values.byName(json['status'] as String),
        appliedAt: json['appliedAt'] as String,
        jobPostingUrl: json['jobPostingUrl'] as String?,
        memo: json['memo'] as String?,
        createdAt: json['createdAt'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}

/// 목록 화면 상태: 아이템 + 페이지네이션 메타 + 현재 상태 필터.
class JobApplicationsState {
  final List<JobApplicationItem> items;
  final int page;
  final int size;
  final int total;
  final int totalPages;
  final JobApplicationStatus? statusFilter;

  const JobApplicationsState({
    required this.items,
    required this.page,
    required this.size,
    required this.total,
    required this.totalPages,
    this.statusFilter,
  });
}

class JobApplicationsNotifier
    extends StateNotifier<AsyncValue<JobApplicationsState>> {
  JobApplicationsNotifier() : super(const AsyncValue.loading()) {
    fetch();
  }

  final _dio = createDio();

  Future<void> fetch({
    int page = 0,
    int size = 20,
    JobApplicationStatus? status,
  }) async {
    state = const AsyncValue.loading();
    try {
      final query = <String, dynamic>{'page': page, 'size': size};
      if (status != null) query['status'] = status.name;

      final res = await _dio.get(
        '/api/v1/job-applications',
        queryParameters: query,
      );
      final items = (res.data['data'] as List)
          .map((e) => JobApplicationItem.fromJson(e as Map<String, dynamic>))
          .toList();
      final meta = res.data['meta'] as Map<String, dynamic>;

      state = AsyncValue.data(JobApplicationsState(
        items: items,
        page: meta['page'] as int,
        size: meta['size'] as int,
        total: meta['total'] as int,
        totalPages: meta['totalPages'] as int,
        statusFilter: status,
      ));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  /// 현재 페이지/필터를 유지한 채 다시 조회한다.
  Future<void> refresh() {
    final current = state.valueOrNull;
    return fetch(
      page: current?.page ?? 0,
      status: current?.statusFilter,
    );
  }

  Future<void> goToPage(int page) {
    final current = state.valueOrNull;
    return fetch(page: page, status: current?.statusFilter);
  }

  Future<void> filterByStatus(JobApplicationStatus? status) {
    return fetch(page: 0, status: status);
  }

  Future<JobApplicationItem> getDetail(int id) async {
    final res = await _dio.get('/api/v1/job-applications/$id');
    return JobApplicationItem.fromJson(
      res.data['data'] as Map<String, dynamic>,
    );
  }

  Future<void> create({
    required String companyName,
    required String position,
    required JobApplicationStatus status,
    required String appliedAt,
    String? jobPostingUrl,
    String? memo,
  }) async {
    await _dio.post('/api/v1/job-applications', data: {
      'companyName': companyName,
      'position': position,
      'status': status.name,
      'appliedAt': appliedAt,
      'jobPostingUrl': jobPostingUrl,
      'memo': memo,
    });
    await refresh();
  }

  Future<void> update(
    int id, {
    required String companyName,
    required String position,
    required JobApplicationStatus status,
    required String appliedAt,
    String? jobPostingUrl,
    String? memo,
  }) async {
    await _dio.put('/api/v1/job-applications/$id', data: {
      'companyName': companyName,
      'position': position,
      'status': status.name,
      'appliedAt': appliedAt,
      'jobPostingUrl': jobPostingUrl,
      'memo': memo,
    });
    await refresh();
  }

  Future<void> delete(int id) async {
    await _dio.delete('/api/v1/job-applications/$id');
    await refresh();
  }
}

final jobApplicationsProvider = StateNotifierProvider<JobApplicationsNotifier,
    AsyncValue<JobApplicationsState>>(
  (_) => JobApplicationsNotifier(),
);
