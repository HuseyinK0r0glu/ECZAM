import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import LogDoseButton from "./LogDoseButton";
import { logDose } from "../../services/logService";

vi.mock("../../services/logService", () => ({ logDose: vi.fn() }));
const mockLogDose = vi.mocked(logDose);

function renderButton() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <LogDoseButton userMedicationId="um-1" />
    </QueryClientProvider>
  );
}

describe("LogDoseButton — success path", () => {
  beforeEach(() => { mockLogDose.mockReset(); });

  it("shows the remaining quantity after a successful dose log", async () => {
    mockLogDose.mockResolvedValue({ log: { id: "l1", takenAt: "" }, newQuantity: 9, lowStock: false });
    renderButton();
    fireEvent.click(screen.getByRole("button", { name: /Aldım/ }));
    expect(await screen.findByRole("status")).toHaveTextContent("Kalan: 9");
    expect(mockLogDose).toHaveBeenCalledWith("um-1", 1, undefined);
  });
});
