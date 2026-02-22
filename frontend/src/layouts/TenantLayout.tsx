import { Outlet } from "react-router-dom";
import { Topbar } from "@/features/navigation";
import { TenantNavbar } from "@/features/tenant/navigation/TenantNavbar";

export function TenantLayout() {
  return (
    <div className="flex min-h-screen">
      <TenantNavbar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
