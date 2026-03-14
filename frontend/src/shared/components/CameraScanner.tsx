import { useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";
import { X, ScanBarcode } from "lucide-react";
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

export function CameraScanner({
  tenantSlug,
  open,
  onClose,
  onResolved,
  acceptTypes,
  title = "Scan QR Code",
}: CameraScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [status, setStatus] = useState<string>("Point camera at a QR code");
  const [error, setError] = useState<string | null>(null);
  const [manualCode, setManualCode] = useState("");
  const [permissionDenied, setPermissionDenied] = useState(false);
  const isProcessing = useRef(false);

  useEffect(() => {
    if (!open) return;

    // Small delay to ensure DOM element is mounted
    const timer = setTimeout(async () => {
      const container = document.getElementById(SCANNER_DIV_ID);
      if (!container) return;

      try {
        const scanner = new Html5Qrcode(SCANNER_DIV_ID);
        scannerRef.current = scanner;

        await scanner.start(
          { facingMode: "environment" },
          { fps: 10, qrbox: { width: 250, height: 250 } },
          async (decodedText) => {
            if (isProcessing.current) return;
            isProcessing.current = true;
            setStatus("Resolving...");
            setError(null);
            try {
              const result = await resolveCode(tenantSlug, decodedText);
              if (acceptTypes && acceptTypes.length > 0 && !acceptTypes.includes(result.type)) {
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
      } catch (err: unknown) {
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
        scanner.stop().then(() => scanner.clear()).catch(() => {});
        scannerRef.current = null;
      }
      isProcessing.current = false;
    };
  }, [open]);

  async function handleManualSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = manualCode.trim();
    if (!trimmed) return;
    setError(null);
    try {
      const result = await resolveCode(tenantSlug, trimmed);
      if (acceptTypes && acceptTypes.length > 0 && !acceptTypes.includes(result.type)) {
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
            <div
              id={SCANNER_DIV_ID}
              className="overflow-hidden rounded-md"
              style={{ width: "100%", minHeight: 280 }}
            />
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
