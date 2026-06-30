import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import LogDoseButton from "./LogDoseButton";
import { logDose } from "../../services/logService";

// Kept in its own file: vitest isolates files in separate contexts, and its spy
// tracking of a rejected mock result surfaces a derived "unhandled rejection" whose
// timing leaks across sibling tests in the same file (the component itself catches
// the error — see the rendered status text below). One rejected-mock test per file
// keeps this deterministic.
vi.mock("../../services/logService", () => ({ logDose: vi.fn() }));
const mockLogDose = vi.mocked(logDose);

describe("LogDoseButton — insufficient stock path", () => {
  it("reports insufficient stock from the API error code", async () => {
    mockLogDose.mockImplementation(() =>
      Promise.reject({ response: { data: { error: { code: "INSUFFICIENT_STOCK" } } } }));
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <LogDoseButton userMedicationId="um-1" />
      </QueryClientProvider>
    );
    fireEvent.click(screen.getByRole("button", { name: /Aldım/ }));
    await waitFor(() => expect(mockLogDose).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.getByRole("status")).toHaveTextContent("Yetersiz stok.");
  });
});
