import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/features/ai/ai_dto.dart';
import 'package:medtrack/features/ai/ai_repository.dart';
import 'package:medtrack/theme/med_theme.dart';

/// A single chat turn. Assistant turns stream in token-by-token and accumulate
/// cited leaflet sections.
class _ChatMessage {
  final bool fromUser;
  String text;
  final List<String> citations;
  bool grounded;
  bool streaming;

  _ChatMessage({
    required this.fromUser,
    this.text = '',
    List<String>? citations,
    this.grounded = true,
    this.streaming = false,
  }) : citations = citations ?? [];
}

/// RAG assistant chat. Answers are grounded strictly in drug leaflets; when the
/// stream ends `grounded:false`, the bubble shows the pharmacist guardrail.
/// TTS output only (mic input is out of scope per the brief).
class AiAssistantScreen extends StatefulWidget {
  /// Optional medication context (`user_medications.id` is not used here; pass
  /// the catalog medication id to scope retrieval to one leaflet).
  final String? medicationId;
  final String? medicationName;

  const AiAssistantScreen({super.key, this.medicationId, this.medicationName});

  @override
  State<AiAssistantScreen> createState() => _AiAssistantScreenState();
}

class _AiAssistantScreenState extends State<AiAssistantScreen> {
  final _inputCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  final FlutterTts _tts = FlutterTts();
  final List<_ChatMessage> _messages = [];
  CancelToken? _cancel;
  bool _sending = false;

  @override
  void dispose() {
    _cancel?.cancel();
    _inputCtrl.dispose();
    _scrollCtrl.dispose();
    _tts.stop();
    super.dispose();
  }

  List<String> get _history => _messages
      .where((m) => !m.streaming)
      .map((m) => '${m.fromUser ? 'user' : 'assistant'}: ${m.text}')
      .toList();

  Future<void> _send() async {
    final text = _inputCtrl.text.trim();
    if (text.isEmpty || _sending) return;
    final ai = context.read<AiRepository>();

    final assistant = _ChatMessage(fromUser: false, streaming: true);
    setState(() {
      _messages.add(_ChatMessage(fromUser: true, text: text));
      _messages.add(assistant);
      _inputCtrl.clear();
      _sending = true;
    });
    _scrollToEnd();

    _cancel = CancelToken();
    try {
      final stream = ai.chat(
        message: text,
        medicationId: widget.medicationId,
        history: _history,
        cancelToken: _cancel,
      );
      await for (final event in stream) {
        if (!mounted) return;
        setState(() {
          switch (event) {
            case TokenEvent(:final text):
              assistant.text += text;
            case CitationEvent(:final section):
              if (!assistant.citations.contains(section)) {
                assistant.citations.add(section);
              }
            case DoneEvent(:final grounded):
              assistant.grounded = grounded;
          }
        });
        _scrollToEnd();
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          assistant.text = e.code == 'RATE_LIMITED'
              ? 'Too many questions right now — please wait a moment.'
              : 'The assistant is unavailable right now. Please try again later.';
          assistant.grounded = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          assistant.text =
              'The assistant is unavailable right now. Please try again later.';
          assistant.grounded = false;
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          assistant.streaming = false;
          if (!assistant.grounded && assistant.text.trim().isEmpty) {
            assistant.text =
                "I don't have leaflet information to answer that. Please ask "
                'your pharmacist or doctor.';
          }
          _sending = false;
        });
      }
    }
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _speak(String text) async {
    await _tts.stop();
    await _tts.speak(text);
  }

  @override
  Widget build(BuildContext context) {
    final hasRepo = _tryReadAi() != null;
    return Scaffold(
      backgroundColor: MedColors.bgMid,
      appBar: AppBar(
        backgroundColor: MedColors.bgTop,
        title: Text(widget.medicationName == null
            ? 'Leaflet assistant'
            : 'About ${widget.medicationName}'),
        foregroundColor: MedColors.text,
      ),
      body: Column(
        children: [
          if (!hasRepo)
            const _Banner(
              text:
                  'The assistant needs the backend AI keys configured to answer.',
            ),
          Expanded(
            child: _messages.isEmpty
                ? const _EmptyState()
                : ListView.builder(
                    controller: _scrollCtrl,
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                    itemCount: _messages.length,
                    itemBuilder: (_, i) =>
                        _Bubble(message: _messages[i], onSpeak: _speak),
                  ),
          ),
          _Composer(
            controller: _inputCtrl,
            sending: _sending,
            onSend: hasRepo ? _send : null,
          ),
        ],
      ),
    );
  }

  AiRepository? _tryReadAi() {
    try {
      return context.read<AiRepository>();
    } catch (_) {
      return null;
    }
  }
}

class _Bubble extends StatelessWidget {
  final _ChatMessage message;
  final Future<void> Function(String) onSpeak;

  const _Bubble({required this.message, required this.onSpeak});

  @override
  Widget build(BuildContext context) {
    final fromUser = message.fromUser;
    return Align(
      alignment: fromUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
        constraints: const BoxConstraints(maxWidth: 320),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          color: fromUser ? MedColors.teal : Colors.white,
          boxShadow: const [
            BoxShadow(
              color: Color(0x1A403626),
              offset: Offset(0, 2),
              blurRadius: 8,
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              message.text.isEmpty && message.streaming ? '…' : message.text,
              style: TextStyle(
                fontSize: 14,
                height: 1.45,
                color: fromUser ? MedColors.tealInk : MedColors.text,
              ),
            ),
            if (message.citations.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final c in message.citations)
                    Container(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(6),
                        color: const Color(0x1A17A89F),
                      ),
                      child: Text(
                        c,
                        style: const TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w600,
                          color: MedColors.tealDeep,
                        ),
                      ),
                    ),
                ],
              ),
            ],
            if (!fromUser && !message.streaming && message.text.isNotEmpty)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => onSpeak(message.text),
                  icon: const Icon(Icons.volume_up, size: 18),
                  label: const Text('Read aloud'),
                  style: TextButton.styleFrom(
                    foregroundColor: MedColors.tealDeep,
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  final TextEditingController controller;
  final bool sending;
  final VoidCallback? onSend;

  const _Composer({
    required this.controller,
    required this.sending,
    required this.onSend,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 6, 12, 10),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: controller,
                minLines: 1,
                maxLines: 4,
                enabled: onSend != null,
                textInputAction: TextInputAction.send,
                onSubmitted: (_) => onSend?.call(),
                decoration: InputDecoration(
                  hintText: 'Ask about a leaflet…',
                  filled: true,
                  fillColor: Colors.white,
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            GestureDetector(
              onTap: sending ? null : onSend,
              child: Container(
                width: 46,
                height: 46,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: MedColors.teal,
                ),
                child: sending
                    ? const Padding(
                        padding: EdgeInsets.all(12),
                        child: CircularProgressIndicator(
                          strokeWidth: 2.2,
                          valueColor:
                              AlwaysStoppedAnimation(MedColors.tealInk),
                        ),
                      )
                    : const Icon(Icons.send, color: MedColors.tealInk),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) => const Center(
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: 40),
          child: Text(
            'Ask about side effects, dosage, or storage. Answers come only from '
            'the official leaflets — never general medical advice.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: MedText.serif,
              fontStyle: FontStyle.italic,
              fontSize: 16,
              height: 1.5,
              color: MedColors.textMuted,
            ),
          ),
        ),
      );
}

class _Banner extends StatelessWidget {
  final String text;
  const _Banner({required this.text});

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        color: const Color(0x1AB98A2E),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Text(text,
            style: const TextStyle(fontSize: 12, color: MedColors.late)),
      );
}
