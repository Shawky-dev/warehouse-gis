import { Separator } from "@/components/ui/separator";
import { AvatarMenu } from "./AvatarMenu";

export function Topbar() {
    return (
        <>
            <header className="flex items-center justify-end h-14 px-6 bg-background shrink-0">
                <AvatarMenu />
            </header>
            <Separator />
        </>
    );
}
