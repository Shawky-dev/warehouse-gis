import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface Props {
    open: boolean;
    onConfirm: () => void | Promise<void>;
    onCancel: () => void;
}

export function LogoutConfirmModal({ open, onConfirm, onCancel }: Props) {
    return (
        <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onCancel()}>
            <DialogContent className="sm:max-w-[380px]">
                <DialogHeader>
                    <DialogTitle>Sign out</DialogTitle>
                    <DialogDescription>
                        Are you sure you want to sign out of your account?
                    </DialogDescription>
                </DialogHeader>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onCancel}>
                        Cancel
                    </Button>
                    <Button variant="destructive" onClick={() => void onConfirm()}>
                        Sign out
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
