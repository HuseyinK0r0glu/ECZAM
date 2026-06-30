import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/schedules/schedule_dto.dart';
import 'package:medtrack/models/medication.dart' show minuteToHHmm;

/// Reminder schedules (`/schedules`, `/user-medications/{id}/schedules`).
class ScheduleRepository {
  final ApiClient api;
  ScheduleRepository(this.api);

  Future<List<ScheduleView>> listForUser() async {
    final (items, _) = await api.getList(
      '/schedules',
      (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
    );
    return items;
  }

  Future<List<ScheduleView>> listForMedication(String userMedicationId) async {
    final (items, _) = await api.getList(
      '/user-medications/$userMedicationId/schedules',
      (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
    );
    return items;
  }

  /// Creates a daily reminder schedule from a med's reminder minutes.
  Future<ScheduleView> createDaily({
    required String userMedicationId,
    required List<int> reminderMinutes,
    double dosageAmount = 1,
  }) =>
      api.postJson(
        '/user-medications/$userMedicationId/schedules',
        ScheduleView.dailyBody(
          dosageAmount: dosageAmount,
          reminderMinutes: reminderMinutes,
        ),
        (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<ScheduleView> updateTimes(
    String scheduleId, {
    required List<int> reminderMinutes,
    double? dosageAmount,
  }) =>
      api.patchJson(
        '/schedules/$scheduleId',
        {
          'scheduledTimes': reminderMinutes.map(minuteToHHmm).toList(),
          if (dosageAmount != null) 'dosageAmount': dosageAmount,
        },
        (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<ScheduleView> pause(String scheduleId) => api.postJson(
        '/schedules/$scheduleId/pause',
        null,
        (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<ScheduleView> resume(String scheduleId) => api.postJson(
        '/schedules/$scheduleId/resume',
        null,
        (j) => ScheduleView.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<void> delete(String scheduleId) =>
      api.deleteOne('/schedules/$scheduleId');
}
