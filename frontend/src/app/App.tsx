import { useRoutes } from "react-router-dom"
import { ThemeProvider } from "@/shared/components/theme-provider"
import { routes } from "./routes"

export default function App() {
  const element = useRoutes(routes)
  return (
    <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
      {element}
    </ThemeProvider>
  )
}
