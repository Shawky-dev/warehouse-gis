import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";
import { server } from "@/test/msw/server";
import { resetAuthSessionManager } from "@/features/auth/session/authSessionManager";

beforeAll(() => {
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetAuthSessionManager();
});

afterAll(() => {
  server.close();
});
