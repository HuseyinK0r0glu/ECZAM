import 'dart:ui' show DartPluginRegistrant;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'package:medtrack/data/app_database.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

const kActionTaken = 'taken';
const kActionSkipped = 'skipped';
const kActionSnooze = 'snooze';

/// Max reminder times per medication. Bounds the notification-id space so
/// [NotificationService.cancelForMedication] can clear every reminder for a
/// med without knowing its current times.
const kMaxReminders = 12;

const _channelId = 'med_reminders';
const _channelName = 'Medication reminders';
const _darwinCategoryId = 'med_actions';
const _snoozeMinutes = 10;

/// Payload carried on every reminder: medId|minuteOfDay|index|name|dose.
class ReminderPayload {
  final int medId;
  final int minuteOfDay;
  final int index;
  final String name;
  final String dose;

  const ReminderPayload(
    this.medId,
    this.minuteOfDay,
    this.index,
    this.name,
    this.dose,
  );

  String encode() => '$medId|$minuteOfDay|$index|$name|$dose';

  static ReminderPayload? tryDecode(String? raw) {
    if (raw == null) return null;
    final parts = raw.split('|');
    if (parts.length < 5) return null;
    final medId = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    final index = int.tryParse(parts[2]);
    if (medId == null || minute == null || index == null) return null;
    return ReminderPayload(
      medId,
      minute,
      index,
      parts[3],
      parts.sublist(4).join('|'),
    );
  }
}

/// Handles Taken / Skip / Snooze pressed on a notification. Runs in the
/// main isolate when the app is alive, or in a background isolate when not,
/// so it talks to the database directly instead of going through app state.
@pragma('vm:entry-point')
Future<void> notificationActionHandler(NotificationResponse response) async {
  // When the app is backgrounded or terminated, this runs in a separate
  // background isolate where plugins are NOT registered by default. Without
  // this call sqflite (dose logging) and the snooze re-schedule silently
  // throw, so the Taken / Skip / Snooze buttons appear to do nothing.
  DartPluginRegistrant.ensureInitialized();
  final payload = ReminderPayload.tryDecode(response.payload);
  if (payload == null) return;

  switch (response.actionId) {
    case kActionTaken || kActionSkipped:
      final status = response.actionId == kActionTaken
          ? DoseStatus.taken
          : DoseStatus.skipped;
      final now = DateTime.now();
      final db = await AppDatabase.open();
      // Intentionally not closed: sqflite shares one native handle per path,
      // closing here would also close the main isolate's connection.
      await SqliteMedicationRepository(db).upsertLog(
        DoseLog(
          id: 0,
          medId: payload.medId,
          medName: payload.name,
          medDose: payload.dose,
          minuteOfDay: payload.minuteOfDay,
          dateKey: DoseLog.dateKeyFor(now),
          status: status,
          scheduledAt: DoseLog.scheduledAtFor(now, payload.minuteOfDay),
          loggedAt: now,
        ),
      );
    case kActionSnooze:
      await _scheduleSnooze(payload);
    default:
      break; // Plain tap: the app opens, nothing to record.
  }
}

/// Snooze is a one-off relative offset, so UTC is correct regardless of the
/// local timezone database state in a background isolate.
Future<void> _scheduleSnooze(ReminderPayload payload) async {
  tzdata.initializeTimeZones();
  final plugin = FlutterLocalNotificationsPlugin();
  await plugin.initialize(
    settings: const InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(
        requestAlertPermission: false,
        requestBadgePermission: false,
        requestSoundPermission: false,
      ),
    ),
  );
  await _zonedSchedule(
    plugin,
    id: NotificationService.snoozeNotificationId(payload.medId, payload.index),
    title: payload.name,
    body: 'Snoozed reminder — ${payload.dose}',
    payload: payload,
    scheduledDate: tz.TZDateTime.now(
      tz.UTC,
    ).add(const Duration(minutes: _snoozeMinutes)),
  );
}

Future<void> _zonedSchedule(
  FlutterLocalNotificationsPlugin plugin, {
  required int id,
  required String title,
  required String body,
  required ReminderPayload payload,
  required tz.TZDateTime scheduledDate,
  DateTimeComponents? matchDateTimeComponents,
}) async {
  const details = NotificationDetails(
    android: AndroidNotificationDetails(
      _channelId,
      _channelName,
      channelDescription: 'Reminders to take your medication on time',
      importance: Importance.max,
      priority: Priority.high,
      category: AndroidNotificationCategory.reminder,
      actions: [
        // cancelNotification: true dismisses the notification from the tray
        // as soon as an action is tapped (handled natively by the
        // ActionBroadcastReceiver). showsUserInterface stays false so the
        // taps are processed in the background without opening the app.
        AndroidNotificationAction(
          kActionTaken,
          'Taken',
          cancelNotification: true,
          showsUserInterface: false,
        ),
        AndroidNotificationAction(
          kActionSkipped,
          'Skip',
          cancelNotification: true,
          showsUserInterface: false,
        ),
        AndroidNotificationAction(
          kActionSnooze,
          'Snooze 10 min',
          cancelNotification: true,
          showsUserInterface: false,
        ),
      ],
    ),
    iOS: DarwinNotificationDetails(categoryIdentifier: _darwinCategoryId),
  );
  try {
    await plugin.zonedSchedule(
      id: id,
      title: title,
      body: body,
      scheduledDate: scheduledDate,
      notificationDetails: details,
      payload: payload.encode(),
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      matchDateTimeComponents: matchDateTimeComponents,
    );
  } on PlatformException catch (e) {
    // Exact alarm permission revoked (Android 12+): degrade to inexact
    // rather than dropping the reminder entirely.
    if (e.code != 'exact_alarms_not_permitted') rethrow;
    await plugin.zonedSchedule(
      id: id,
      title: title,
      body: body,
      scheduledDate: scheduledDate,
      notificationDetails: details,
      payload: payload.encode(),
      androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
      matchDateTimeComponents: matchDateTimeComponents,
    );
  }
}

class NotificationService {
  final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();

  /// Invoked when the user interacts with a notification while the app is
  /// running, so app state can reload today's logs.
  void Function(ReminderPayload payload, String? actionId)?
  onForegroundResponse;

  // Per-med id space: med N owns [N*100, N*100+62). Daily reminders use the
  // reminder index (0..kMaxReminders-1); snooze adds a +50 offset.
  static int dailyNotificationId(int medId, int index) => medId * 100 + index;

  static int snoozeNotificationId(int medId, int index) =>
      medId * 100 + 50 + index;

  /// Next wall-clock occurrence of [time] in [location]. TZDateTime
  /// normalizes nonexistent/ambiguous times across DST transitions, and
  /// scheduling with [DateTimeComponents.time] keeps the wall-clock time
  /// stable when the UTC offset shifts (FR-2.3).
  static tz.TZDateTime nextInstanceOf(
    TimeOfDay time, {
    tz.Location? location,
    DateTime? from,
  }) {
    final loc = location ?? tz.local;
    final now = from != null
        ? tz.TZDateTime.from(from, loc)
        : tz.TZDateTime.now(loc);
    var scheduled = tz.TZDateTime(
      loc,
      now.year,
      now.month,
      now.day,
      time.hour,
      time.minute,
    );
    if (!scheduled.isAfter(now)) {
      scheduled = tz.TZDateTime(
        loc,
        now.year,
        now.month,
        now.day + 1,
        time.hour,
        time.minute,
      );
    }
    return scheduled;
  }

  Future<void> init() async {
    tzdata.initializeTimeZones();
    try {
      final info = await FlutterTimezone.getLocalTimezone();
      tz.setLocalLocation(tz.getLocation(info.identifier));
    } on Exception catch (_) {
      // Unknown zone id: tz.local stays UTC. Daily schedules still fire,
      // at a shifted wall-clock time, which beats crashing on startup.
    }

    await _plugin.initialize(
      settings: InitializationSettings(
        android: const AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(
          requestAlertPermission: false,
          requestBadgePermission: false,
          requestSoundPermission: false,
          notificationCategories: [
            DarwinNotificationCategory(
              _darwinCategoryId,
              actions: [
                DarwinNotificationAction.plain(kActionTaken, 'Taken'),
                DarwinNotificationAction.plain(kActionSkipped, 'Skip'),
                DarwinNotificationAction.plain(kActionSnooze, 'Snooze 10 min'),
              ],
            ),
          ],
        ),
      ),
      onDidReceiveNotificationResponse: _handleForegroundResponse,
      onDidReceiveBackgroundNotificationResponse: notificationActionHandler,
    );
  }

  Future<void> _handleForegroundResponse(NotificationResponse response) async {
    await notificationActionHandler(response);
    final payload = ReminderPayload.tryDecode(response.payload);
    if (payload != null) {
      onForegroundResponse?.call(payload, response.actionId);
    }
  }

  Future<bool> requestPermissions() async {
    final android = _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    if (android != null) {
      final granted = await android.requestNotificationsPermission() ?? true;
      await android.requestExactAlarmsPermission();
      return granted;
    }
    final ios = _plugin
        .resolvePlatformSpecificImplementation<
          IOSFlutterLocalNotificationsPlugin
        >();
    if (ios != null) {
      return await ios.requestPermissions(
            alert: true,
            badge: true,
            sound: true,
          ) ??
          false;
    }
    return true;
  }

  Future<bool> notificationsEnabled() async {
    final android = _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    if (android != null) {
      return await android.areNotificationsEnabled() ?? false;
    }
    return true;
  }

  /// Schedules one daily reminder per exact time in
  /// [Medication.reminderMinutes] (capped at [kMaxReminders]). Each fires at
  /// its precise hour:minute and repeats daily via [DateTimeComponents.time].
  Future<void> scheduleForMedication(Medication med) async {
    await cancelForMedication(med.id);
    final minutes = med.reminderMinutes.take(kMaxReminders).toList();
    for (var index = 0; index < minutes.length; index++) {
      final minute = minutes[index];
      await _zonedSchedule(
        _plugin,
        id: dailyNotificationId(med.id, index),
        title: med.name,
        body: med.dose.isEmpty ? 'Time to take it' : 'Time for ${med.dose}',
        payload: ReminderPayload(med.id, minute, index, med.name, med.dose),
        scheduledDate: nextInstanceOf(minuteToTimeOfDay(minute)),
        matchDateTimeComponents: DateTimeComponents.time,
      );
    }
  }

  Future<void> cancelForMedication(int medId) async {
    for (var index = 0; index < kMaxReminders; index++) {
      await _plugin.cancel(id: dailyNotificationId(medId, index));
      await _plugin.cancel(id: snoozeNotificationId(medId, index));
    }
  }

  /// In-app snooze: one-off reminder in 10 minutes (FR-2.2).
  Future<void> snoozeReminder(Medication med, int minuteOfDay) {
    final index = med.reminderMinutes.indexOf(minuteOfDay);
    return _scheduleSnooze(
      ReminderPayload(
        med.id,
        minuteOfDay,
        index < 0 ? 0 : index,
        med.name,
        med.dose,
      ),
    );
  }

  /// Defensive re-registration on every launch. Reboots are covered natively
  /// by ScheduledNotificationBootReceiver, but this also recovers from
  /// force-stops and timezone moves while the app was dead.
  Future<void> rescheduleAll(List<Medication> meds) async {
    for (final med in meds) {
      await scheduleForMedication(med);
    }
  }

  Future<bool> launchedFromNotification() async {
    final details = await _plugin.getNotificationAppLaunchDetails();
    return details?.didNotificationLaunchApp ?? false;
  }
}
