import { QRCodeSVG } from "qrcode.react";

interface DocumentQRProps {
  qrData: string;
  label: string;
  size?: number;
}

export function DocumentQR({ qrData, label, size = 96 }: DocumentQRProps) {
  function handlePrint() {
    window.print();
  }

  return (
    <div className="flex items-center gap-4">
      <div id="printable-label" className="flex flex-col items-center gap-1">
        <QRCodeSVG value={qrData} size={size} />
        {/* Shown only when printing — hidden on screen */}
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
          /* Hide everything, then reveal only the printable label */
          body * { visibility: hidden; }
          #printable-label,
          #printable-label * { visibility: visible; }
          #printable-label {
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            text-align: center;
          }
          /* Scale QR up for a decent print size */
          #printable-label svg {
            width: 200px !important;
            height: 200px !important;
          }
        }
      `}</style>
    </div>
  );
}
