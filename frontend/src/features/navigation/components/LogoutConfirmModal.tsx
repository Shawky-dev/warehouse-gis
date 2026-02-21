import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n";

interface Props {
  open: boolean;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
}

export function LogoutConfirmModal({ open, onConfirm, onCancel }: Props) {
  const { t } = useI18n();

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onCancel()}>
      <DialogContent className="sm:max-w-[380px]">
        <DialogHeader>
          <DialogTitle>{t("logoutModal.title")}</DialogTitle>
          <DialogDescription>{t("logoutModal.description")}</DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={onCancel}>
            {t("logoutModal.cancel")}
          </Button>
          <Button variant="destructive" onClick={() => void onConfirm()}>
            {t("logoutModal.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
