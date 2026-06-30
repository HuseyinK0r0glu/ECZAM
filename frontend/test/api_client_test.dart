import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/core/api/api_client.dart';

/// Error-envelope mapping (the pure part of ApiClient — no network needed).
/// The 401→refresh→retry interceptor is covered by an http_mock_adapter test
/// (see plans/testing-plan.md §5.1) once run on a Flutter toolchain.
void main() {
  test('maps a backend error envelope to a typed ApiException', () {
    final e = DioException(
      requestOptions: RequestOptions(path: '/user-medications'),
      type: DioExceptionType.badResponse,
      response: Response(
        requestOptions: RequestOptions(path: '/user-medications'),
        statusCode: 422,
        data: {
          'data': null,
          'error': {'code': 'INSUFFICIENT_STOCK', 'message': 'no stock'}
        },
      ),
    );
    final ex = ApiClient.toApiException(e);
    expect(ex.code, 'INSUFFICIENT_STOCK');
    expect(ex.statusCode, 422);
    expect(ex.message, 'no stock');
  });

  test('maps a transport failure to NETWORK_ERROR', () {
    final e = DioException(
      requestOptions: RequestOptions(path: '/x'),
      type: DioExceptionType.connectionTimeout,
    );
    final ex = ApiClient.toApiException(e);
    expect(ex.code, 'NETWORK_ERROR');
  });
}
