import { MemoryRouter } from "react-router-dom";
import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement } from "react";
import { I18nProvider } from "@/i18n";
import type { Locale } from "@/i18n";

export function renderWithRouter(
  ui: ReactElement,
  initialEntries: string[] = ["/"],
  locale: Locale = "en"
) {
  const options: RenderOptions = {
    wrapper: ({ children }) => (
      <I18nProvider initialLocale={locale} storageKey={`test-locale-${locale}`}>
        <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
      </I18nProvider>
    ),
  };

  return render(ui, options);
}
