import 'package:sqflite/sqflite.dart';

class AppDatabase {
  static const _dbName = 'medtrack.db';
  // v2: medications.slots -> reminder_minutes, dose_logs.slot -> minute_of_day
  // (exact reminder times). No production data existed, so the upgrade
  // recreates the tables rather than migrating fixed slots to minutes.
  static const _dbVersion = 2;

  /// [singleInstance] is false only in tests, so each in-memory database is
  /// isolated instead of sharing sqflite's per-path cache.
  static Future<Database> open({
    String? path,
    bool singleInstance = true,
  }) async {
    final dbPath = path ?? '${await getDatabasesPath()}/$_dbName';
    return openDatabase(
      dbPath,
      version: _dbVersion,
      singleInstance: singleInstance,
      onCreate: (db, version) => _createSchema(db),
      onUpgrade: (db, oldVersion, newVersion) async {
        await db.execute('DROP TABLE IF EXISTS dose_logs');
        await db.execute('DROP TABLE IF EXISTS medications');
        await _createSchema(db);
      },
    );
  }

  static Future<void> _createSchema(Database db) async {
    await db.execute('''
      CREATE TABLE medications(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        dose TEXT NOT NULL,
        kind TEXT NOT NULL,
        reminder_minutes TEXT NOT NULL,
        photo_file TEXT,
        created_at INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE dose_logs(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        med_id INTEGER NOT NULL,
        med_name TEXT NOT NULL,
        med_dose TEXT NOT NULL,
        minute_of_day INTEGER NOT NULL,
        date_key TEXT NOT NULL,
        status TEXT NOT NULL,
        scheduled_at INTEGER NOT NULL,
        logged_at INTEGER NOT NULL,
        UNIQUE(med_id, minute_of_day, date_key) ON CONFLICT REPLACE
      )
    ''');
    await db.execute('CREATE INDEX idx_logs_date ON dose_logs(date_key)');
  }
}
