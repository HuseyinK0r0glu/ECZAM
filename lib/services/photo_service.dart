import 'dart:io';

import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

/// Picks, compresses and stores medication photos under the app's documents
/// directory. Only the file name is persisted in the database; the directory
/// is resolved at runtime because iOS moves the app container on updates.
class PhotoService {
  final ImagePicker _picker = ImagePicker();

  static const _dirName = 'med_photos';
  static const _maxWidth = 1280.0;
  static const _jpegQuality = 80;

  Future<Directory> _photoDir() async {
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/$_dirName');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<String> pathFor(String fileName) async =>
      '${(await _photoDir()).path}/$fileName';

  /// Returns the stored file name, or null if the user cancelled the picker
  /// or the platform denied access (camera permission permanently denied
  /// surfaces here as a PlatformException, which we swallow into null so the
  /// caller can show a friendly message).
  Future<String?> pickAndStore(ImageSource source) async {
    final XFile? picked;
    try {
      picked = await _picker.pickImage(
        source: source,
        maxWidth: _maxWidth,
        imageQuality: _jpegQuality,
        requestFullMetadata: false,
      );
    } on Exception catch (_) {
      return null;
    }
    if (picked == null) return null;

    final fileName = 'med_${DateTime.now().millisecondsSinceEpoch}.jpg';
    final destination = await pathFor(fileName);
    await File(picked.path).copy(destination);
    // The picker's temp copy is no longer needed.
    try {
      await File(picked.path).delete();
    } on FileSystemException catch (_) {}
    return fileName;
  }

  Future<void> delete(String? fileName) async {
    if (fileName == null) return;
    final file = File(await pathFor(fileName));
    if (await file.exists()) await file.delete();
  }
}
