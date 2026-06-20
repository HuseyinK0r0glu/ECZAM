import { useCallback, useEffect, useRef, useState } from "react";

export function useTTS() {
  const [speaking, setSpeaking] = useState(false);
  const [paused, setPaused] = useState(false);
  const voiceRef = useRef<SpeechSynthesisVoice | null>(null);

  useEffect(() => {
    function pickVoice() {
      const voices = window.speechSynthesis.getVoices();
      const lang = navigator.language || "tr-TR";
      voiceRef.current = voices.find((v) => v.lang.startsWith(lang.split("-")[0])) ?? voices[0] ?? null;
    }
    pickVoice();
    window.speechSynthesis.onvoiceschanged = pickVoice;
    return () => window.speechSynthesis.cancel();
  }, []);

  const play = useCallback((text: string) => {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    if (voiceRef.current) { u.voice = voiceRef.current; u.lang = voiceRef.current.lang; }
    u.onend = () => { setSpeaking(false); setPaused(false); };
    u.onerror = () => { setSpeaking(false); setPaused(false); };
    window.speechSynthesis.speak(u);
    setSpeaking(true); setPaused(false);
  }, []);

  const pause = useCallback(() => { window.speechSynthesis.pause(); setPaused(true); }, []);
  const resume = useCallback(() => { window.speechSynthesis.resume(); setPaused(false); }, []);
  const stop = useCallback(() => { window.speechSynthesis.cancel(); setSpeaking(false); setPaused(false); }, []);

  return { speaking, paused, play, pause, resume, stop };
}
