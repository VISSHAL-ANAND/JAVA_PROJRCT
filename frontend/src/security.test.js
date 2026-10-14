import assert from "node:assert/strict";
import test from "node:test";
import { isApiUrlSecure } from "./security.js";

test("allows HTTPS API origins in production", () => {
  assert.equal(isApiUrlSecure("https://api.example.com", true), true);
});

test("rejects public HTTP API origins in production", () => {
  assert.equal(isApiUrlSecure("http://api.example.com", true), false);
});

test("allows loopback HTTP for local production-build previews", () => {
  assert.equal(isApiUrlSecure("http://localhost:8080", true), true);
  assert.equal(isApiUrlSecure("http://127.0.0.1:8080", true), true);
});

test("allows HTTP during local development", () => {
  assert.equal(isApiUrlSecure("http://api.example.test", false), true);
});

test("rejects invalid API origins in production", () => {
  assert.equal(isApiUrlSecure("not a URL", true), false);
});
