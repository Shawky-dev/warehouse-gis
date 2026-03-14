import { useId } from "react";
import { QRCodeSVG } from "qrcode.react";

interface DocumentQRProps {
  qrData: string;
  label: string;
  size?: number;
}

export function DocumentQR({ qrData, label, size = 96 }: DocumentQRProps) {
  const rawId = useId();
  const printId = `dqr-${rawId.replace(/[^a-zA-Z0-9]/g, "")}`;

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
        <QRCodeSVG value={qrData} size={size} />
        <p className="hidden text-sm font-medium print:block">{label}</p>
        <p className="hidden font-mono text-xs print:block">{qrData}</p>
      </div>

      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium">{label}</p>
        <p className="font-mono text-xs text-muted-foreground">{qrData}</p>
        <button
          type="button"
          onClick={handlePrint}
          className="mt-1 w-fit rounded-md border px-2 py-1 text-xs hover:bg-accent print:hidden"
        >
          Print QR
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
