import { useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";
import { ScanBarcode } from "lucide-react";
import { resolveCode } from "@/features/tenant/api/scanApi";
import type { ScanResolveResult, ScanType } from "@/features/tenant/types/scan";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface CameraScannerProps {
  tenantSlug: string;
  open: boolean;
  onClose: () => void;
  onResolved: (result: ScanResolveResult) => void;
  acceptTypes?: ScanType[];
  title?: string;
}

const SCANNER_DIV_ID = "camera-scanner-qr-container";
// BarcodeDetector is available on Chrome/Edge/Android — fast, hardware-accelerated
const useNativeDetector = typeof window !== "undefined" && "BarcodeDetector" in window;

export function CameraScanner({
  tenantSlug,
  open,
  onClose,
  onResolved,
  acceptTypes,
  title = "Scan QR Code",
}: CameraScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const rafRef = useRef<number>(0);
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [status, setStatus] = useState<string>("Point camera at a QR code");
  const [error, setError] = useState<string | null>(null);
  const [manualCode, setManualCode] = useState("");
  const [permissionDenied, setPermissionDenied] = useState(false);
  const isProcessing = useRef(false);

  // Fast path: native BarcodeDetector (Chrome / Edge / Android WebView)
  // Uses requestAnimationFrame — effectively 30-60 fps with hardware decode
  useEffect(() => {
    if (!open || !useNativeDetector) return;
    let cancelled = false;

    (async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: "environment" },
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
        });
        if (cancelled) { stream.getTracks().forEach(t => t.stop()); return; }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
        }

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const detector = new (window as any).BarcodeDetector({ formats: ["qr_code"] });
        let detecting = false;

        const scan = async () => {
          if (cancelled) return;
          if (
            !detecting &&
            !isProcessing.current &&
            videoRef.current &&
            videoRef.current.readyState >= 2
          ) {
            detecting = true;
            try {
              const codes = await detector.detect(videoRef.current);
              if (codes.length > 0 && !isProcessing.current) {
                isProcessing.current = true;
                setStatus("Resolving...");
                setError(null);
                try {
                  const result = await resolveCode(tenantSlug, codes[0].rawValue);
                  if (acceptTypes?.length && !acceptTypes.includes(result.type)) {
                    setError(`Wrong code type (expected: ${acceptTypes.join(", ")})`);
                    setStatus("Point camera at a QR code");
                  } else {
                    onResolved(result);
                  }
                } catch {
                  setError("Code not recognised");
                  setStatus("Point camera at a QR code");
                } finally {
                  isProcessing.current = false;
                }
              }
            } catch { /* detection frame error — ignore */ }
            detecting = false;
          }
          if (!cancelled) rafRef.current = requestAnimationFrame(scan);
        };
        rafRef.current = requestAnimationFrame(scan);
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        if (msg.toLowerCase().includes("permission") || msg.toLowerCase().includes("notallowed")) {
          setPermissionDenied(true);
        }
      }
    })();

    return () => {
      cancelled = true;
      cancelAnimationFrame(rafRef.current);
      streamRef.current?.getTracks().forEach(t => t.stop());
      streamRef.current = null;
      isProcessing.current = false;
    };
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // Fallback: html5-qrcode for Safari / Firefox (no BarcodeDetector)
  // Bumped to fps:30 and larger qrbox so alignment is much more forgiving
  useEffect(() => {
    if (!open || useNativeDetector) return;
    const timer = setTimeout(async () => {
      const container = document.getElementById(SCANNER_DIV_ID);
      if (!container) return;
      try {
        const scanner = new Html5Qrcode(SCANNER_DIV_ID);
        scannerRef.current = scanner;
        await scanner.start(
          { facingMode: "environment" },
          { fps: 30, qrbox: { width: 300, height: 300 } },
          async (decodedText) => {
            if (isProcessing.current) return;
            isProcessing.current = true;
            setStatus("Resolving...");
            setError(null);
            try {
              const result = await resolveCode(tenantSlug, decodedText);
              if (acceptTypes?.length && !acceptTypes.includes(result.type)) {
                setError(`Wrong code type (expected: ${acceptTypes.join(", ")})`);
                setStatus("Point camera at a QR code");
              } else {
                onResolved(result);
              }
            } catch {
              setError("Code not recognised");
              setStatus("Point camera at a QR code");
            } finally {
              isProcessing.current = false;
            }
          },
          undefined
        );
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.toLowerCase().includes("permission") || message.toLowerCase().includes("notallowed")) {
          setPermissionDenied(true);
        }
      }
    }, 100);

    return () => {
      clearTimeout(timer);
      const scanner = scannerRef.current;
      if (scanner) {
        scanner.stop().then(() => scanner.clear()).catch(() => { });
        scannerRef.current = null;
      }
      isProcessing.current = false;
    };
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleManualSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const trimmed = manualCode.trim();
    if (!trimmed) return;
    setError(null);
    try {
      const result = await resolveCode(tenantSlug, trimmed);
      if (acceptTypes?.length && !acceptTypes.includes(result.type)) {
        setError(`Wrong code type (expected: ${acceptTypes.join(", ")})`);
        return;
      }
      setManualCode("");
      onResolved(result);
    } catch {
      setError("Code not recognised");
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ScanBarcode className="h-5 w-5" />
            {title}
          </DialogTitle>
        </DialogHeader>

        {!permissionDenied ? (
          <>
            {useNativeDetector ? (
              // Native path: plain <video> with a translucent overlay border
              <div
                className="relative overflow-hidden rounded-md"
                style={{ width: "100%", aspectRatio: "1" }}
              >
                <video
                  ref={videoRef}
                  className="h-full w-full object-cover"
                  autoPlay
                  playsInline
                  muted
                />
                {/* Dim surround + bright corner box so user knows where to aim */}
                <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                  <div className="h-56 w-56 rounded-lg border-2 border-white/80 shadow-[0_0_0_9999px_rgba(0,0,0,0.45)]" />
                </div>
              </div>
            ) : (
              // Fallback path: html5-qrcode renders its own video inside this div
              <div
                id={SCANNER_DIV_ID}
                className="overflow-hidden rounded-md"
                style={{ width: "100%", minHeight: 280 }}
              />
            )}
            <p className="text-center text-sm text-muted-foreground">{status}</p>
          </>
        ) : (
          <p className="text-sm text-muted-foreground">
            Camera permission denied — use the text input below.
          </p>
        )}

        {error && <p className="text-center text-sm text-destructive">{error}</p>}

        <form onSubmit={handleManualSubmit} className="flex gap-2">
          <input
            type="text"
            value={manualCode}
            onChange={(e) => setManualCode(e.target.value)}
            placeholder="Or type / paste a code..."
            className="h-9 flex-1 rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
          <button
            type="submit"
            className="h-9 rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            Go
          </button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
