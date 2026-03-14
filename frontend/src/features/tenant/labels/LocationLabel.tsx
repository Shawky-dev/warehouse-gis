import { QRCodeSVG } from "qrcode.react";

interface LocationLabelProps {
  scanCode: string;
  fullCode: string | null;
  locationKindName: string | null;
  pathLabel: string | null;
}

export function LocationLabel({ scanCode, fullCode, locationKindName, pathLabel }: LocationLabelProps) {
  const displayLabel = fullCode ?? pathLabel ?? scanCode;

  function handlePrint() {
    window.print();
  }

  return (
    <div>
      <div
        id="printable-label"
        className="flex flex-col items-center gap-3 rounded-lg border p-6 print:border-none print:p-0"
      >
        <QRCodeSVG value={scanCode} size={160} />
        <p className="text-center text-lg font-bold">{displayLabel}</p>
        {locationKindName && (
          <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {locationKindName}
          </span>
        )}
        <p className="font-mono text-xs text-muted-foreground">{scanCode}</p>
      </div>
      <div className="mt-4 flex justify-center print:hidden">
        <button
          type="button"
          onClick={handlePrint}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          Print
        </button>
      </div>
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #printable-label, #printable-label * { visibility: visible; }
          #printable-label {
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            text-align: center;
          }
          #printable-label svg { width: 200px !important; height: 200px !important; }
        }
      `}</style>
    </div>
  );
}
