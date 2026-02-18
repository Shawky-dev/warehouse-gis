import { Outlet } from "react-router-dom";
import { Navbar } from "@/features/navigation";

export function LandlordLayout() {
    return (
        <div className="min-h-screen flex flex-col">
            <Navbar />
            <main className="flex-1 p-6">
                <Outlet />
            </main>
        </div>
    );
}
