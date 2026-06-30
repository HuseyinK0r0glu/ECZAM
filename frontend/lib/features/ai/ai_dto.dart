/// Events emitted by the RAG assistant SSE stream (`POST /ai/chat`).
///
/// The backend sends `event: token|citation|done`. `done` carries
/// `{ "grounded": true|false }` — `false` means the question couldn't be
/// answered from the leaflets, so the UI shows the "consult a pharmacist"
/// guardrail message.
library;

sealed class ChatEvent {
  const ChatEvent();
}

/// A chunk of answer text — append these to build the streamed reply.
class TokenEvent extends ChatEvent {
  final String text;
  const TokenEvent(this.text);
}

/// A leaflet section cited as a source, e.g. "4. Possible side effects".
class CitationEvent extends ChatEvent {
  final String section;
  const CitationEvent(this.section);
}

/// Final event. [grounded] is false when nothing in the leaflets matched.
class DoneEvent extends ChatEvent {
  final bool grounded;
  const DoneEvent(this.grounded);
}
