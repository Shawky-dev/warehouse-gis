import { useRoutes } from "react-router-dom";
import { ThemeProvider } from "@/lib/theme-provider";
import { AuthProvider } from "@/features/auth/context/AuthContext";
import { I18nProvider } from "@/i18n";
import { routes } from "./routes";

export default function App() {
  const element = useRoutes(routes);

  return (
    <I18nProvider>
      <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
        <AuthProvider>{element}</AuthProvider>
      </ThemeProvider>
    </I18nProvider>
  );
}
