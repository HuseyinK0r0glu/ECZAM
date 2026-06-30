import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';

/// Thin wrapper over `connectivity_plus` exposing a simple online/offline
/// signal for the sync layer. Note: connectivity only reports whether a network
/// interface exists, not whether the backend is actually reachable — the sync
/// engine treats a failed request as "offline" regardless.
class ConnectivityService {
  final Connectivity _connectivity;

  ConnectivityService({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity();

  bool _online = true;
  bool get isOnline => _online;

  final _controller = StreamController<bool>.broadcast();

  /// Emits `true` on (re)connect and `false` when all interfaces drop.
  Stream<bool> get onlineStream => _controller.stream;

  StreamSubscription<List<ConnectivityResult>>? _sub;

  Future<void> init() async {
    _online = _isOnlineResult(await _connectivity.checkConnectivity());
    _sub = _connectivity.onConnectivityChanged.listen((results) {
      final next = _isOnlineResult(results);
      if (next != _online) {
        _online = next;
        _controller.add(next);
      }
    });
  }

  bool _isOnlineResult(List<ConnectivityResult> results) =>
      results.any((r) => r != ConnectivityResult.none);

  Future<void> dispose() async {
    await _sub?.cancel();
    await _controller.close();
  }
}
