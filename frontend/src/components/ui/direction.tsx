import type { ReactNode } from "react";
import { Direction } from "radix-ui";
import type { Direction as DirectionType } from "@/i18n/types";

interface DirectionProviderProps {
  direction: DirectionType;
  children: ReactNode;
}

export function DirectionProvider({ direction, children }: DirectionProviderProps) {
  return <Direction.DirectionProvider dir={direction}>{children}</Direction.DirectionProvider>;
}
