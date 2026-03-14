import { QRCodeSVG } from "qrcode.react";

interface LotLabelProps {
  sku: string;
  productName: string;
  lotNumber: string;
  expiryDate?: string | null;
}

export function LotLabel({ sku, productName, lotNumber, expiryDate }: LotLabelProps) {
  const qrValue = `${sku}:${lotNumber}`;

  function handlePrint() {
    window.print();
  }

  return (
    <div>
      <div
        id="printable-label"
        className="flex flex-col items-center gap-3 rounded-lg border p-6 print:border-none print:p-0"
      >
        <QRCodeSVG value={qrValue} size={128} />
        <p className="text-center text-base font-bold">{productName}</p>
        <p className="font-mono text-sm text-muted-foreground">{sku}</p>
        <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium tracking-wide text-muted-foreground">
          Lot: {lotNumber}
        </span>
        {expiryDate && (
          <p className="text-xs text-muted-foreground">Expiry: {expiryDate}</p>
        )}
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
          body > *:not(#printable-label) { display: none !important; }
        }
      `}</style>
    </div>
  );
}
