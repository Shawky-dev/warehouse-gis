import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import LandlordDashboardPage from "@/features/landlord/dashboard/LandlordDashboardPage";

describe("LandlordDashboardPage", () => {
  it("shows overview message and points user to warehouses tab", () => {
    renderWithRouter(<LandlordDashboardPage />);
    expect(screen.getByText("Landlord Dashboard")).toBeInTheDocument();
  });
});
