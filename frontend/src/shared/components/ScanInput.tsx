import { useRef, useState } from "react";
import { ScanBarcode, Camera, Loader2 } from "lucide-react";
import { resolveCode } from "@/features/tenant/api/scanApi";
import type { ScanResolveResult, ScanType } from "@/features/tenant/types/scan";
import { CameraScanner } from "./CameraScanner";

interface ScanInputProps {
  tenantSlug: string;
  onResolved: (result: ScanResolveResult) => void;
  onError?: (code: string) => void;
  placeholder?: string;
  acceptTypes?: ScanType[];
  disabled?: boolean;
}

export function ScanInput({
  tenantSlug,
  onResolved,
  onError,
  placeholder = "Scan or type a code...",
  acceptTypes,
  disabled = false,
}: ScanInputProps) {
  const [value, setValue] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function playScanBeep() {
    try {
      const ctx = new AudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.value = 1200;
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.08);
      osc.start(ctx.currentTime);
      osc.stop(ctx.currentTime + 0.08);
    } catch {
      // Web Audio not available — ignore
    }
  }

  async function handleSubmit(code: string) {
    const trimmed = code.trim();
    if (!trimmed) return;
    setLoading(true);
    setError(null);
    try {
      const result = await resolveCode(tenantSlug, trimmed);
      if (acceptTypes && acceptTypes.length > 0 && !acceptTypes.includes(result.type)) {
        if (result.type === "RECEIPT" && acceptTypes.includes("RECEIPT_LINE")) {
          setError("Scanned a receipt document QR. Scan a stock unit label (RECEIPT_LINE) instead.");
        } else {
          setError(`Wrong code type: ${result.type} (expected: ${acceptTypes.join(", ")})`);
        }
        onError?.(trimmed);
        return;
      }
      setValue("");
      playScanBeep();
      navigator.vibrate?.(50);
      onResolved(result);
    } catch {
      setError("Code not recognised");
      onError?.(trimmed);
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSubmit(value);
    }
  }

  function handlePaste(e: React.ClipboardEvent<HTMLInputElement>) {
    const pasted = e.clipboardData.getData("text");
    if (pasted.trim()) {
      e.preventDefault();
      setValue(pasted.trim());
      handleSubmit(pasted.trim());
    }
  }

  function handleCameraResolved(result: ScanResolveResult) {
    setCameraOpen(false);
    playScanBeep();
    navigator.vibrate?.(50);
    onResolved(result);
  }

  return (
    <div className="flex flex-col gap-1">
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-3 text-muted-foreground">
          {loading ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <ScanBarcode className="h-4 w-4" />
          )}
        </span>
        <input
          ref={inputRef}
          type="text"
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            setError(null);
          }}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder={placeholder}
          disabled={disabled || loading}
          className={
            "h-9 w-full rounded-md border bg-background px-9 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50" +
            (error ? " border-destructive" : " border-input")
          }
        />
        <button
          type="button"
          onClick={() => setCameraOpen(true)}
          disabled={disabled || loading}
          className="absolute right-2 rounded-sm p-1 text-muted-foreground hover:text-foreground disabled:opacity-50"
          title="Open camera scanner"
        >
          <Camera className="h-4 w-4" />
        </button>
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
      <CameraScanner
        tenantSlug={tenantSlug}
        open={cameraOpen}
        onClose={() => setCameraOpen(false)}
        onResolved={handleCameraResolved}
        acceptTypes={acceptTypes}
      />
    </div>
  );
}
