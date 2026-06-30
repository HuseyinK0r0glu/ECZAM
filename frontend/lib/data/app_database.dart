import 'package:sqflite/sqflite.dart';

/// Local SQLite database. Since the React→Flutter+backend migration this is a
/// **mirror cache**, not the source of truth: the Spring Boot backend owns the
/// data and rows here are copies kept for offline reads, plus an outbox
/// (`pending_ops`) of writes made while offline.
class AppDatabase {
  static const _dbName = 'eczam.db';

  // v3: backend migration. medications/dose_logs switch to TEXT (UUID) ids and
  // gain inventory + schedule + sync columns; a `pending_ops` outbox is added.
  // The pre-migration schema held no syncable production data, so the upgrade
  // recreates the tables rather than migrating int ids to UUIDs.
  static const _dbVersion = 3;

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
        await db.execute('DROP TABLE IF EXISTS pending_ops');
        await db.execute('DROP TABLE IF EXISTS dose_logs');
        await db.execute('DROP TABLE IF EXISTS medications');
        await _createSchema(db);
      },
    );
  }

  static Future<void> _createSchema(Database db) async {
    // Mirror of user_medications (+ catalog name/strength + attached schedule).
    // `kind` and `photo_file` are local-only presentation data with no backend
    // column. `sync_state`: synced | pending (offline create/update not yet
    // flushed).
    await db.execute('''
      CREATE TABLE medications(
        id TEXT PRIMARY KEY,
        catalog_id TEXT,
        schedule_id TEXT,
        name TEXT NOT NULL,
        dose TEXT,
        kind TEXT NOT NULL,
        reminder_minutes TEXT NOT NULL,
        photo_file TEXT,
        created_at INTEGER NOT NULL,
        quantity REAL NOT NULL DEFAULT 0,
        unit TEXT,
        expiration_date TEXT,
        low_stock INTEGER NOT NULL DEFAULT 0,
        expiry_status TEXT NOT NULL DEFAULT 'OK',
        sync_state TEXT NOT NULL DEFAULT 'synced',
        updated_at INTEGER NOT NULL DEFAULT 0
      )
    ''');
    // Local adherence history. Only `taken` doses sync to the backend
    // (`backend_id` set, sync_state='synced'); skipped/snoozed stay local.
    await db.execute('''
      CREATE TABLE dose_logs(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        backend_id TEXT,
        med_id TEXT NOT NULL,
        med_name TEXT NOT NULL,
        med_dose TEXT NOT NULL,
        minute_of_day INTEGER NOT NULL,
        date_key TEXT NOT NULL,
        status TEXT NOT NULL,
        scheduled_at INTEGER NOT NULL,
        logged_at INTEGER NOT NULL,
        sync_state TEXT NOT NULL DEFAULT 'local',
        UNIQUE(med_id, minute_of_day, date_key) ON CONFLICT REPLACE
      )
    ''');
    await db.execute('CREATE INDEX idx_logs_date ON dose_logs(date_key)');
    // Outbox: writes queued while offline, drained in order on reconnect.
    await db.execute('''
      CREATE TABLE pending_ops(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        entity TEXT NOT NULL,
        op TEXT NOT NULL,
        payload TEXT NOT NULL,
        created_at INTEGER NOT NULL
      )
    ''');
  }
}
