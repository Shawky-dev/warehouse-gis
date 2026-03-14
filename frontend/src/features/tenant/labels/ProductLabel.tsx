import { QRCodeSVG } from "qrcode.react";

interface ProductLabelProps {
  sku: string;
  name: string;
  categoryName?: string | null;
}

export function ProductLabel({ sku, name, categoryName }: ProductLabelProps) {
  function handlePrint() {
    window.print();
  }

  return (
    <div>
      <div
        id="printable-label"
        className="flex flex-col items-center gap-3 rounded-lg border p-6 print:border-none print:p-0"
      >
        <QRCodeSVG value={sku} size={128} />
        <p className="text-center text-base font-bold">{name}</p>
        <p className="font-mono text-sm text-muted-foreground">{sku}</p>
        {categoryName && (
          <p className="text-xs text-muted-foreground">{categoryName}</p>
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
