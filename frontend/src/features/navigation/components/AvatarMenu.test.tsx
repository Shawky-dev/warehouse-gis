import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AvatarMenu } from "@/features/navigation/components/AvatarMenu";
import { I18nProvider } from "@/i18n";

const mockUseAuth = vi.fn();
const mockUseTheme = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/lib/theme-provider", () => ({
  useTheme: () => mockUseTheme(),
}));

afterEach(() => {
  mockUseAuth.mockReset();
  mockUseTheme.mockReset();
});

describe("AvatarMenu language switch", () => {
  it("switches locale and document direction between English and Arabic", async () => {
    const user = userEvent.setup();
    const setTheme = vi.fn();

    mockUseAuth.mockReturnValue({
      user: {
        email: "admin@system.local",
        roles: ["ROLE_ADMIN"],
        permissions: [],
      },
      logout: vi.fn(),
    });

    mockUseTheme.mockReturnValue({
      theme: "dark",
      setTheme,
    });

    render(
      <I18nProvider storageKey="avatar-menu-test" initialLocale="en">
        <AvatarMenu />
      </I18nProvider>
    );

    await user.click(screen.getByText("A"));
    expect(await screen.findByText("Language")).toBeInTheDocument();

    await user.click(screen.getByText("Arabic"));

    await waitFor(() => {
      expect(document.documentElement.lang).toBe("ar");
      expect(document.documentElement.dir).toBe("rtl");
    });

    await user.click(screen.getByText("A"));
    expect(await screen.findByText("اللغة")).toBeInTheDocument();

    await user.click(screen.getByText("الإنجليزية"));

    await waitFor(() => {
      expect(document.documentElement.lang).toBe("en");
      expect(document.documentElement.dir).toBe("ltr");
    });
  });
});
