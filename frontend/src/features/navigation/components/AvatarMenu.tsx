import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Check, LogOut, Moon, Settings, Sun, User } from "lucide-react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useAvatarMenu } from "../hooks/useAvatarMenu";
import { LogoutConfirmModal } from "./LogoutConfirmModal";
import { useTheme } from "@/lib/theme-provider";
import { useI18n } from "@/i18n";

export function AvatarMenu() {
  const { user, logout } = useAuth();
  const { showLogoutModal, openLogoutModal, closeLogoutModal } = useAvatarMenu();
  const { theme, setTheme } = useTheme();
  const { locale, setLocale, t } = useI18n();

  const isDark =
    theme === "dark" ||
    (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);

  const initials = user?.email?.slice(0, 1).toUpperCase() ?? "?";

  const handleLogout = async () => {
    await logout();
    closeLogoutModal();
  };

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Avatar className="cursor-pointer ring-2 ring-transparent transition-all hover:ring-primary">
            <AvatarFallback>{initials}</AvatarFallback>
          </Avatar>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="w-56">
          <DropdownMenuLabel className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold">{user?.email ?? t("common.userFallback")}</span>
            <span className="text-xs font-normal text-muted-foreground">{user?.roles.join(", ")}</span>
          </DropdownMenuLabel>

          <DropdownMenuSeparator />

          <DropdownMenuItem className="cursor-pointer gap-2">
            <User className="h-4 w-4" />
            {t("avatarMenu.profile")}
          </DropdownMenuItem>

          <DropdownMenuItem className="cursor-pointer gap-2">
            <Settings className="h-4 w-4" />
            {t("avatarMenu.settings")}
          </DropdownMenuItem>

          <DropdownMenuItem
            className="cursor-pointer gap-2"
            onClick={(e) => {
              e.preventDefault();
              setTheme(isDark ? "light" : "dark");
            }}
          >
            {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            {isDark ? t("avatarMenu.lightMode") : t("avatarMenu.darkMode")}
          </DropdownMenuItem>

          <DropdownMenuSeparator />

          <DropdownMenuLabel>{t("common.language")}</DropdownMenuLabel>

          <DropdownMenuItem className="cursor-pointer gap-2" onClick={() => setLocale("en")}>
            <span>{t("common.english")}</span>
            {locale === "en" ? <Check className="ms-auto h-4 w-4" /> : null}
          </DropdownMenuItem>

          <DropdownMenuItem className="cursor-pointer gap-2" onClick={() => setLocale("ar")}>
            <span>{t("common.arabic")}</span>
            {locale === "ar" ? <Check className="ms-auto h-4 w-4" /> : null}
          </DropdownMenuItem>

          <DropdownMenuSeparator />

          <DropdownMenuItem
            className="cursor-pointer gap-2 text-red-600 focus:text-red-600"
            onClick={openLogoutModal}
          >
            <LogOut className="h-4 w-4 text-red-600" />
            <span className="text-red-600">{t("avatarMenu.signOut")}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <LogoutConfirmModal
        open={showLogoutModal}
        onConfirm={handleLogout}
        onCancel={closeLogoutModal}
      />
    </>
  );
}
