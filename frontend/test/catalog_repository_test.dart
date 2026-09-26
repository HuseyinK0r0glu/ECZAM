import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/medications/medication_repository.dart';

/// `CatalogRepository`'s new `category` filter param and the new
/// `GET /medications/categories` endpoint — verified at the query-building
/// level with a mocked Dio adapter (path-only matching, so we capture the
/// resolved `RequestOptions` ourselves to assert on query parameters).
void main() {
  late Dio dio;
  late DioAdapter adapter;
  late CatalogRepository repo;
  RequestOptions? captured;

  Map<String, dynamic> envelope(Object? data) =>
      {'data': data, 'meta': null, 'error': null};

  setUp(() {
    dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        captured = options;
        handler.next(options);
      },
    ));
    adapter = DioAdapter(dio: dio, matcher: const UrlRequestMatcher());
    repo = CatalogRepository(ApiClient(tokenStore: TokenStore(), dio: dio));
  });

  test('search omits the category param when none is given (regression guard)', () async {
    adapter.onGet('/medications', (server) => server.reply(200, envelope([])));

    await repo.search('ibuprofen');

    expect(captured!.queryParameters['q'], 'ibuprofen');
    expect(captured!.queryParameters.containsKey('category'), isFalse);
  });

  test('search sends the category param when provided', () async {
    adapter.onGet('/medications', (server) => server.reply(200, envelope([])));

    await repo.search('ibuprofen', category: 'Kas İskelet Sistemi');

    expect(captured!.queryParameters['q'], 'ibuprofen');
    expect(captured!.queryParameters['category'], 'Kas İskelet Sistemi');
  });

  test('search with a blank query and a category omits q but keeps category', () async {
    adapter.onGet('/medications', (server) => server.reply(200, envelope([])));

    await repo.search('', category: 'Kardiyovasküler Sistem');

    expect(captured!.queryParameters.containsKey('q'), isFalse);
    expect(captured!.queryParameters['category'], 'Kardiyovasküler Sistem');
  });

  test('categories() calls GET /medications/categories and parses count entries', () async {
    adapter.onGet(
      '/medications/categories',
      (server) => server.reply(200, envelope([
        {'category': 'Kas İskelet Sistemi', 'count': 842},
        {'category': 'Kardiyovasküler Sistem', 'count': 301},
      ])),
    );

    final cats = await repo.categories();

    expect(captured!.path, '/medications/categories');
    expect(cats, hasLength(2));
    expect(cats.first.category, 'Kas İskelet Sistemi');
    expect(cats.first.count, 842);
    expect(cats.last.count, 301);
  });
}
