import { useRoutes } from "react-router-dom"
import { ThemeProvider } from "@/lib/theme-provider"
import { AuthProvider } from "@/features/auth/context/AuthContext"
import { routes } from "./routes"

export default function App() {
  const element = useRoutes(routes)
  return (
    <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
      <AuthProvider>
        {element}
      </AuthProvider>
    </ThemeProvider>
  )
}
