import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useTTS } from "./useTTS";

class FakeUtterance {
  text: string;
  voice: unknown = null;
  lang = "";
  onend: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(text: string) { this.text = text; }
}

const synth = {
  speak: vi.fn(),
  cancel: vi.fn(),
  pause: vi.fn(),
  resume: vi.fn(),
  getVoices: () => [] as SpeechSynthesisVoice[],
  onvoiceschanged: null as unknown,
};

beforeEach(() => {
  synth.speak.mockReset(); synth.cancel.mockReset(); synth.pause.mockReset(); synth.resume.mockReset();
  vi.stubGlobal("speechSynthesis", synth);
  vi.stubGlobal("SpeechSynthesisUtterance", FakeUtterance);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("useTTS", () => {
  it("play/pause/resume/stop drive the speech synthesis API and state", () => {
    const { result } = renderHook(() => useTTS());

    act(() => result.current.play("Merhaba"));
    expect(synth.speak).toHaveBeenCalledOnce();
    expect(result.current.speaking).toBe(true);
    expect(result.current.paused).toBe(false);

    act(() => result.current.pause());
    expect(synth.pause).toHaveBeenCalledOnce();
    expect(result.current.paused).toBe(true);

    act(() => result.current.resume());
    expect(synth.resume).toHaveBeenCalledOnce();
    expect(result.current.paused).toBe(false);

    act(() => result.current.stop());
    expect(synth.cancel).toHaveBeenCalled();
    expect(result.current.speaking).toBe(false);
  });

  it("does not crash when no voices are available (voice fallback)", () => {
    const { result } = renderHook(() => useTTS());
    expect(() => act(() => result.current.play("test"))).not.toThrow();
  });
});
