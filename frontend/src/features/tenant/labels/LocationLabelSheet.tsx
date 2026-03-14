import { QRCodeSVG } from "qrcode.react";

export interface LocationLabelSheetItem {
  scanCode: string;
  displayLabel: string;
  locationKindName: string | null;
}

interface LocationLabelSheetProps {
  items: LocationLabelSheetItem[];
  printButtonLabel?: string;
}

export function LocationLabelSheet({ items, printButtonLabel = "Print Sheet" }: LocationLabelSheetProps) {
  function handlePrint() {
    window.print();
  }

  return (
    <div>
      <div
        id="bulk-print-sheet"
        className="grid grid-cols-2 gap-4 sm:grid-cols-3"
      >
        {items.map((item) => (
          <div
            key={item.scanCode}
            className="flex flex-col items-center gap-2 rounded-lg border p-4"
          >
            <QRCodeSVG value={item.scanCode} size={100} />
            <p className="text-center text-sm font-bold leading-tight">{item.displayLabel}</p>
            {item.locationKindName ? (
              <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                {item.locationKindName}
              </span>
            ) : null}
            <p className="font-mono text-[10px] text-muted-foreground">{item.scanCode}</p>
          </div>
        ))}
      </div>

      <div className="mt-4 flex justify-center print:hidden">
        <button
          type="button"
          onClick={handlePrint}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          {printButtonLabel}
        </button>
      </div>

      <style>{`
        @media print {
          body * { visibility: hidden; }
          #bulk-print-sheet,
          #bulk-print-sheet * { visibility: visible; }
          #bulk-print-sheet {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            padding: 16px;
          }
          #bulk-print-sheet > div {
            break-inside: avoid;
          }
          #bulk-print-sheet svg {
            width: 120px !important;
            height: 120px !important;
          }
        }
      `}</style>
    </div>
  );
}
