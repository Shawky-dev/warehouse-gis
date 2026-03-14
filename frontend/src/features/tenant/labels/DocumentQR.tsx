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
      <div id="printable-label">
        <QRCodeSVG value={qrData} size={size} />
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
          body > *:not(#printable-label) { display: none !important; }
        }
      `}</style>
    </div>
  );
}
