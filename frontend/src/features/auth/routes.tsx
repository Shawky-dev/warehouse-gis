import { PATHS } from "@/shared/consts/paths"
import LoginPage from './login/LoginPage'

export const authRoutes = [
    {
        path: PATHS.LOGIN,
        element: <LoginPage />,
    },

]
