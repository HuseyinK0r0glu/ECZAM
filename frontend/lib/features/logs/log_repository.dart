import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/logs/log_dto.dart';

/// Immutable dose log (`/medication-logs`). Logging a dose atomically
/// decrements `user_medications.quantity` server-side.
class LogRepository {
  final ApiClient api;
  LogRepository(this.api);

  /// Records a taken dose. Throws [ApiException] with code `INSUFFICIENT_STOCK`
  /// (422) when there isn't enough remaining quantity.
  Future<LogResult> logTaken({
    required String userMedicationId,
    double quantityUsed = 1,
    String? scheduleId,
    String? notes,
    String? clientRequestId,
  }) =>
      api.postJson(
        '/medication-logs',
        {
          'userMedicationId': userMedicationId,
          'quantityUsed': quantityUsed,
          if (scheduleId != null) 'scheduleId': scheduleId,
          if (notes != null) 'notes': notes,
          if (clientRequestId != null) 'clientRequestId': clientRequestId,
        },
        (j) => LogResult.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<List<LogView>> history(
    String userMedicationId, {
    DateTime? from,
    DateTime? to,
    int limit = 50,
  }) async {
    final (items, _) = await api.getList(
      '/medication-logs',
      (j) => LogView.fromJson((j as Map).cast<String, dynamic>()),
      query: {
        'userMedicationId': userMedicationId,
        if (from != null) 'from': from.toUtc().toIso8601String(),
        if (to != null) 'to': to.toUtc().toIso8601String(),
        'limit': limit,
      },
    );
    return items;
  }
}
