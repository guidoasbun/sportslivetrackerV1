import { describe, it, expect } from "vitest";
import { validateEmail, validatePassword } from "../validation";

describe("validateEmail", () => {
  it("accepts valid email formats", () => {
    const validEmails = [
      "user@example.com",
      "firstname.lastname@domain.org",
      "user+tag@sub.domain.co",
      "a@b.cd",
    ];

    for (const email of validEmails) {
      const result = validateEmail(email);
      expect(result.success, `Expected "${email}" to be valid`).toBe(true);
      expect(result.errors).toHaveLength(0);
    }
  });

  it("rejects emails missing '@' character", () => {
    const result = validateEmail("userexample.com");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "atSign")).toBe(true);
  });

  it("rejects emails with missing domain dot", () => {
    const result = validateEmail("user@localhost");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "domainDot")).toBe(true);
  });

  it("rejects emails where local part exceeds 64 characters", () => {
    const longLocal = "a".repeat(65);
    const result = validateEmail(`${longLocal}@example.com`);
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "localPartLength")).toBe(true);
  });

  it("rejects emails with multiple '@' characters", () => {
    const result = validateEmail("user@@example.com");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "atSign")).toBe(true);
  });
});

describe("validatePassword", () => {
  it("rejects passwords shorter than 8 characters", () => {
    const result = validatePassword("Ab1!xyz");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "minLength")).toBe(true);
  });

  it("accepts valid passwords with all required character types", () => {
    const validPasswords = [
      "Abcdef1!",
      "StrongP@ss1",
      "MyP4$$word!",
    ];

    for (const password of validPasswords) {
      const result = validatePassword(password);
      expect(result.success, `Expected "${password}" to be valid`).toBe(true);
      expect(result.errors).toHaveLength(0);
    }
  });

  it("rejects passwords missing uppercase letter", () => {
    const result = validatePassword("abcdef1!");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "uppercase")).toBe(true);
  });

  it("rejects passwords missing a digit", () => {
    const result = validatePassword("Abcdefg!");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "digit")).toBe(true);
  });

  it("rejects passwords missing a special character", () => {
    const result = validatePassword("Abcdefg1");
    expect(result.success).toBe(false);
    expect(result.errors.some((e) => e.rule === "specialChar")).toBe(true);
  });
});
