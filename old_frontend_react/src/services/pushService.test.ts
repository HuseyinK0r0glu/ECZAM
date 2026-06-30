import { describe, it, expect } from "vitest";
import { urlBase64ToUint8Array } from "./pushService";

describe("urlBase64ToUint8Array", () => {
  it("decodes a standard base64 string with no padding needed", () => {
    expect(Array.from(urlBase64ToUint8Array("AQID"))).toEqual([1, 2, 3]);
  });

  it("handles URL-safe chars (- _) and missing padding", () => {
    // base64url "-_8" === standard "+/8=" === bytes [251, 255]
    expect(Array.from(urlBase64ToUint8Array("-_8"))).toEqual([251, 255]);
  });

  it("produces a Uint8Array whose length matches the decoded bytes", () => {
    const out = urlBase64ToUint8Array("AQID");
    expect(out).toBeInstanceOf(Uint8Array);
    expect(out.length).toBe(3);
  });
});
