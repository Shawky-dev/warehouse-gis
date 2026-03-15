import { useId } from "react";
import { QRCodeSVG } from "qrcode.react";

interface LocationLabelProps {
  scanCode: string;
  fullCode: string | null;
  locationKindName: string | null;
  pathLabel: string | null;
}

export function LocationLabel({ scanCode, fullCode, locationKindName, pathLabel }: LocationLabelProps) {
  const rawId = useId();
  const printId = `loc-${rawId.replace(/[^a-zA-Z0-9]/g, "")}`;
  const displayLabel = fullCode ?? pathLabel ?? scanCode;
  const primaryLabel = [displayLabel, locationKindName].filter(Boolean).join(" · ");
  const showRawScanCode = scanCode.trim() !== displayLabel.trim();

  function handlePrint() {
    document.body.dataset.printTarget = printId;
    const cleanup = () => {
      delete document.body.dataset.printTarget;
      window.removeEventListener("afterprint", cleanup);
    };
    window.addEventListener("afterprint", cleanup);
    window.print();
  }

  return (
    <div className="flex items-center gap-4">
      <div id={printId} className="flex flex-col items-center gap-1">
        <QRCodeSVG value={scanCode} size={160} />
        <p className="hidden text-center text-sm font-medium print:block">{primaryLabel}</p>
        {showRawScanCode ? (
          <p className="hidden font-mono text-xs print:block">{scanCode}</p>
        ) : null}
      </div>

      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium">{primaryLabel}</p>
        {showRawScanCode ? (
          <p className="font-mono text-xs text-muted-foreground">{scanCode}</p>
        ) : null}
        <button
          type="button"
          onClick={handlePrint}
          className="mt-1 w-fit rounded-md border px-2 py-1 text-xs hover:bg-accent print:hidden"
        >
          Print
        </button>
      </div>

      <style>{`
        @media print {
          body[data-print-target="${printId}"] * { visibility: hidden; }
          body[data-print-target="${printId}"] #${printId},
          body[data-print-target="${printId}"] #${printId} * { visibility: visible; }
          body[data-print-target="${printId}"] #${printId} {
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            text-align: center;
          }
          body[data-print-target="${printId}"] #${printId} svg {
            width: 200px !important;
            height: 200px !important;
          }
        }
      `}</style>
    </div>
  );
}
