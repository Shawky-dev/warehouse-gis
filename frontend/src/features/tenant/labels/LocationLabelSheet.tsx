import { useId } from "react";
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
  const rawId = useId();
  const printId = `sheet-${rawId.replace(/[^a-zA-Z0-9]/g, "")}`;

  function handlePrint() {
    const printable = document.getElementById(printId);
    if (!printable) {
      return;
    }

    const printWindow = window.open("", "_blank", "width=1024,height=768");
    if (!printWindow) {
      return;
    }

    const styles = Array.from(document.querySelectorAll("style, link[rel='stylesheet']"))
      .map((node) => node.outerHTML)
      .join("\n");

    printWindow.document.open();
    printWindow.document.write(`
      <!doctype html>
      <html>
        <head>
          <meta charset="utf-8" />
          <title>${printButtonLabel}</title>
          ${styles}
          <style>
            @page { margin: 10mm; }
            html, body { margin: 0; padding: 0; }
            #${printId} {
              display: grid;
              grid-template-columns: repeat(2, minmax(0, 1fr));
              gap: 8mm;
              align-items: start;
            }
            #${printId} > div {
              break-inside: avoid;
              page-break-inside: avoid;
            }
            #${printId} svg {
              width: 35mm !important;
              height: 35mm !important;
            }
          </style>
        </head>
        <body>
          ${printable.outerHTML}
        </body>
      </html>
    `);
    printWindow.document.close();

    let printTriggered = false;
    const runPrint = () => {
      if (printTriggered) {
        return;
      }
      printTriggered = true;
      printWindow.focus();
      printWindow.print();
    };

    const closeAfterPrint = () => {
      printWindow.close();
    };

    printWindow.addEventListener("afterprint", closeAfterPrint, { once: true });

    if (printWindow.document.readyState === "complete") {
      runPrint();
      return;
    }

    printWindow.addEventListener("load", runPrint, { once: true });
    window.setTimeout(runPrint, 350);
  }

  return (
    <div>
      <div id={printId} className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {items.map((item, i) => (
          <div
            key={`${item.scanCode}-${i}`}
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
    </div>
  );
}
