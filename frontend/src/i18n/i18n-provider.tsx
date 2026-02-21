import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { DirectionProvider } from "@/components/ui/direction";
import { enMessages } from "@/i18n/messages/en";
import { arMessages } from "@/i18n/messages/ar";
import type { Direction, Locale, TranslationParams } from "@/i18n/types";

const messagesByLocale = {
  en: enMessages,
  ar: arMessages,
} as const;

type Messages = typeof enMessages;
export type TranslationKey = keyof Messages;

const DEFAULT_STORAGE_KEY = "warehouse-gis-locale";

interface I18nContextValue {
  locale: Locale;
  direction: Direction;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: TranslationParams) => string;
}

interface I18nProviderProps {
  children: ReactNode;
  storageKey?: string;
  initialLocale?: Locale;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function isLocale(value: string | null): value is Locale {
  return value === "en" || value === "ar";
}

function safeGetItem(storageKey: string): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  const storage = window.localStorage as Partial<Storage>;
  if (typeof storage.getItem !== "function") {
    return null;
  }

  try {
    return storage.getItem(storageKey);
  } catch {
    return null;
  }
}

function safeSetItem(storageKey: string, value: string) {
  if (typeof window === "undefined") {
    return;
  }

  const storage = window.localStorage as Partial<Storage>;
  if (typeof storage.setItem !== "function") {
    return;
  }

  try {
    storage.setItem(storageKey, value);
  } catch {
    // ignore storage write failures
  }
}

function getLocaleDirection(locale: Locale): Direction {
  return locale === "ar" ? "rtl" : "ltr";
}

function detectBrowserLocale(): Locale {
  if (typeof navigator === "undefined") {
    return "en";
  }

  const browserLocales = [navigator.language, ...(navigator.languages ?? [])].filter(Boolean);
  return browserLocales.some((candidate) => candidate.toLowerCase().startsWith("ar"))
    ? "ar"
    : "en";
}

function resolveInitialLocale(storageKey: string, initialLocale?: Locale): Locale {
  if (initialLocale) {
    return initialLocale;
  }

  if (typeof window === "undefined") {
    return "en";
  }

  const storedLocale = safeGetItem(storageKey);
  if (isLocale(storedLocale)) {
    return storedLocale;
  }

  return detectBrowserLocale();
}

function getMessageValue(key: TranslationKey, locale: Locale): string {
  return messagesByLocale[locale][key] ?? key;
}

function formatMessage(template: string, params?: TranslationParams): string {
  if (!params) {
    return template;
  }

  return template.replace(/\{(\w+)\}/g, (fullMatch, name: string) => {
    const value = params[name];
    return value === undefined ? fullMatch : String(value);
  });
}

export function I18nProvider({
  children,
  storageKey = DEFAULT_STORAGE_KEY,
  initialLocale,
}: I18nProviderProps) {
  const [locale, setLocaleState] = useState<Locale>(() => resolveInitialLocale(storageKey, initialLocale));

  useEffect(() => {
    safeSetItem(storageKey, locale);
  }, [locale, storageKey]);

  useEffect(() => {
    const root = window.document.documentElement;
    root.lang = locale;
    root.dir = getLocaleDirection(locale);
  }, [locale]);

  const direction = getLocaleDirection(locale);

  const setLocale = useCallback((nextLocale: Locale) => {
    setLocaleState(nextLocale);
  }, []);

  const t = useCallback(
    (key: TranslationKey, params?: TranslationParams) => {
      const template = getMessageValue(key, locale);
      return formatMessage(template, params);
    },
    [locale]
  );

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      direction,
      setLocale,
      t,
    }),
    [direction, locale, setLocale, t]
  );

  return (
    <I18nContext.Provider value={value}>
      <DirectionProvider direction={direction}>{children}</DirectionProvider>
    </I18nContext.Provider>
  );
}

export function useI18nContext() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n must be used within <I18nProvider>");
  }

  return context;
}
