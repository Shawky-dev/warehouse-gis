import { beforeEach, describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { useI18n } from "@/i18n";

function Probe() {
  const { locale, direction, setLocale, t } = useI18n();

  return (
    <div>
      <p data-testid="locale">{locale}</p>
      <p data-testid="direction">{direction}</p>
      <p data-testid="label">{t("common.language")}</p>
      <button type="button" onClick={() => setLocale("ar")}>
        set-ar
      </button>
      <button type="button" onClick={() => setLocale("en")}>
        set-en
      </button>
    </div>
  );
}

function createMockStorage(): Storage {
  const store = new Map<string, string>();

  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
}

function mockBrowserLocale(language: string, languages: string[]) {
  Object.defineProperty(window.navigator, "language", {
    configurable: true,
    value: language,
  });

  Object.defineProperty(window.navigator, "languages", {
    configurable: true,
    value: languages,
  });
}

beforeEach(() => {
  Object.defineProperty(window, "localStorage", {
    configurable: true,
    value: createMockStorage(),
  });
});

describe("I18nProvider", () => {
  it("detects Arabic from browser locale and updates document attrs", async () => {
    const storageKey = "i18n-test-detect";
    window.localStorage.removeItem(storageKey);
    mockBrowserLocale("ar-EG", ["ar-EG", "en-US"]);

    render(
      <I18nProvider storageKey={storageKey}>
        <Probe />
      </I18nProvider>
    );

    expect(screen.getByTestId("locale")).toHaveTextContent("ar");
    expect(screen.getByTestId("direction")).toHaveTextContent("rtl");
    expect(screen.getByTestId("label")).toHaveTextContent("اللغة");

    await waitFor(() => {
      expect(document.documentElement.lang).toBe("ar");
      expect(document.documentElement.dir).toBe("rtl");
    });

    expect(window.localStorage.getItem(storageKey)).toBe("ar");
  });

  it("prefers persisted locale over browser locale", () => {
    const storageKey = "i18n-test-persisted";
    window.localStorage.setItem(storageKey, "en");
    mockBrowserLocale("ar-EG", ["ar-EG", "en-US"]);

    render(
      <I18nProvider storageKey={storageKey}>
        <Probe />
      </I18nProvider>
    );

    expect(screen.getByTestId("locale")).toHaveTextContent("en");
    expect(screen.getByTestId("direction")).toHaveTextContent("ltr");
    expect(screen.getByTestId("label")).toHaveTextContent("Language");
  });

  it("persists locale changes and switches direction", async () => {
    const storageKey = "i18n-test-set-locale";
    window.localStorage.removeItem(storageKey);

    render(
      <I18nProvider storageKey={storageKey} initialLocale="en">
        <Probe />
      </I18nProvider>
    );

    fireEvent.click(screen.getByRole("button", { name: "set-ar" }));

    await waitFor(() => {
      expect(screen.getByTestId("locale")).toHaveTextContent("ar");
      expect(screen.getByTestId("direction")).toHaveTextContent("rtl");
      expect(document.documentElement.lang).toBe("ar");
      expect(document.documentElement.dir).toBe("rtl");
      expect(window.localStorage.getItem(storageKey)).toBe("ar");
    });

    fireEvent.click(screen.getByRole("button", { name: "set-en" }));

    await waitFor(() => {
      expect(screen.getByTestId("locale")).toHaveTextContent("en");
      expect(screen.getByTestId("direction")).toHaveTextContent("ltr");
      expect(document.documentElement.lang).toBe("en");
      expect(document.documentElement.dir).toBe("ltr");
      expect(window.localStorage.getItem(storageKey)).toBe("en");
    });
  });
});
