import { Outlet } from "react-router-dom";
import { Navbar, Topbar } from "@/features/navigation";

export function LandlordLayout() {
    return (
        <div className="flex min-h-screen">
            {/* Left sidebar */}
            <Navbar />

            {/* Right column: topbar + page content */}
            <div className="flex flex-col flex-1 min-w-0">
                <Topbar />
                <main className="flex-1 p-6 overflow-auto">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}
