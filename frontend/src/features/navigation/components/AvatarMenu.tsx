import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Settings, User, LogOut, Sun, Moon } from "lucide-react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useAvatarMenu } from "../hooks/useAvatarMenu";
import { LogoutConfirmModal } from "./LogoutConfirmModal";
import { useTheme } from "@/lib/theme-provider";

export function AvatarMenu() {
    const { user, logout } = useAuth();
    const { showLogoutModal, openLogoutModal, closeLogoutModal } = useAvatarMenu();
    const { theme, setTheme } = useTheme();

    const isDark =
        theme === "dark" ||
        (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);

    const initials = user?.name
        ?.split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase() ?? "?";

    return (
        <>
            <DropdownMenu>
                <DropdownMenuTrigger asChild>
                    <Avatar className="cursor-pointer ring-2 ring-transparent hover:ring-primary transition-all">
                        <AvatarImage src={user?.avatarUrl} alt={user?.name} />
                        <AvatarFallback>{initials}</AvatarFallback>
                    </Avatar>
                </DropdownMenuTrigger>

                <DropdownMenuContent align="end" className="w-56">
                    <DropdownMenuLabel className="flex flex-col gap-0.5">
                        <span className="font-semibold text-sm">{user?.name ?? "User"}</span>
                        <span className="text-xs font-normal text-muted-foreground">
                            {user?.email}
                        </span>
                    </DropdownMenuLabel>

                    <DropdownMenuSeparator />

                    <DropdownMenuItem className="cursor-pointer gap-2">
                        <User className="h-4 w-4" />
                        Profile
                    </DropdownMenuItem>

                    <DropdownMenuItem className="cursor-pointer gap-2">
                        <Settings className="h-4 w-4" />
                        Settings
                    </DropdownMenuItem>

                    <DropdownMenuItem
                        className="cursor-pointer gap-2"
                        onClick={(e) => {
                            e.preventDefault();
                            setTheme(isDark ? "light" : "dark");
                        }}
                    >
                        {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                        {isDark ? "Light mode" : "Dark mode"}
                    </DropdownMenuItem>

                    <DropdownMenuSeparator />

                    <DropdownMenuItem
                        className="cursor-pointer gap-2 text-destructive focus:text-destructive"
                        onClick={openLogoutModal}
                    >
                        <LogOut className="h-4 w-4" />
                        Sign out
                    </DropdownMenuItem>
                </DropdownMenuContent>
            </DropdownMenu>

            <LogoutConfirmModal
                open={showLogoutModal}
                onConfirm={logout}
                onCancel={closeLogoutModal}
            />
        </>
    );
}
