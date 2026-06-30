import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateEmail, validatePassword } from "../validation";

/**
 * Property-based tests for validation utility.
 * Feature: app-completion-hardening
 */

// --- Arbitraries (generators) ---

/** Generate a single character matching a regex pattern */
function charMatching(pattern: RegExp): fc.Arbitrary<string> {
  return fc.stringMatching(pattern, { minLength: 1, maxLength: 1 });
}

/** Generate a valid email local part: 1–64 alphanumeric characters */
function validLocalPart(): fc.Arbitrary<string> {
  return fc.stringMatching(/^[a-zA-Z0-9]+$/, { minLength: 1, maxLength: 64 });
}

/** Generate a valid domain label: 1–10 alphanumeric characters (subset of the full 1–63 range for fast generation) */
function validDomainLabel(): fc.Arbitrary<string> {
  return fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 10 });
}

/** Generate a valid domain with at least one dot (2–4 labels) */
function validDomain(): fc.Arbitrary<string> {
  return fc
    .tuple(
      validDomainLabel(),
      fc.array(validDomainLabel(), { minLength: 1, maxLength: 3 })
    )
    .map(([first, rest]) => [first, ...rest].join("."));
}

/** Generate a fully valid email address */
function validEmail(): fc.Arbitrary<string> {
  return fc
    .tuple(validLocalPart(), validDomain())
    .map(([local, domain]) => `${local}@${domain}`)
    .filter((email) => email.length <= 254);
}

/** Special characters allowed in passwords */
const SPECIAL_CHARS = `^ $ * . [ ] { } ( ) ? " ! @ # % & / \\ , > < ' : ; | _ ~ \` = + -`.split(
  " "
);

/** Generate a valid password (8–256 chars with all required character classes) */
function validPassword(): fc.Arbitrary<string> {
  return fc
    .tuple(
      charMatching(/^[A-Z]$/), // at least one uppercase
      charMatching(/^[a-z]$/), // at least one lowercase
      charMatching(/^[0-9]$/), // at least one digit
      fc.constantFrom(...SPECIAL_CHARS), // at least one special char
      // Fill remaining with valid characters (total password: 4 required + 4–252 filler = 8–256 chars)
      fc.array(
        fc.oneof(
          charMatching(/^[a-zA-Z0-9]$/),
          fc.constantFrom(...SPECIAL_CHARS)
        ),
        { minLength: 4, maxLength: 252 }
      )
    )
    .map(([upper, lower, digit, special, rest]) => {
      // Deterministic interleave: place required chars at fixed positions within the rest
      const filler = rest;
      const required = [upper, lower, digit, special];
      // Insert required chars at evenly spaced positions
      const total = filler.length + required.length;
      const result: string[] = new Array(total);
      for (let i = 0; i < required.length; i++) {
        const pos = Math.floor((i * total) / required.length);
        result[pos] = required[i];
      }
      let fillerIdx = 0;
      for (let i = 0; i < total; i++) {
        if (result[i] === undefined) {
          result[i] = filler[fillerIdx++];
        }
      }
      return result.join("");
    })
    .filter((pw) => pw.length >= 8 && pw.length <= 256);
}

describe("Feature: app-completion-hardening, Property 1: Email validation correctness", () => {
  /**
   * **Validates: Requirements 4.1**
   *
   * For any string that contains exactly one "@" with a local part of 1–64 characters,
   * a domain with at least one "." where each label is 1–63 characters, and a total
   * length ≤ 254, the email validator SHALL return success: true with an empty errors array.
   */
  it("accepts all valid emails", () => {
    fc.assert(
      fc.property(validEmail(), (email) => {
        const result = validateEmail(email);
        expect(result.success).toBe(true);
        expect(result.errors).toHaveLength(0);
      }),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.1**
   *
   * For any string that violates any email rules, the validator SHALL return
   * success: false with at least one error.
   */
  it("rejects emails with no @ character", () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z0-9.]+$/, { minLength: 1, maxLength: 100 }),
        (email) => {
          const result = validateEmail(email);
          expect(result.success).toBe(false);
          expect(result.errors.length).toBeGreaterThanOrEqual(1);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("rejects emails with multiple @ characters", () => {
    fc.assert(
      fc.property(
        fc
          .tuple(
            fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 30 }),
            fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 30 }),
            fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 30 })
          )
          .map(([a, b, c]) => `${a}@${b}@${c}`),
        (email) => {
          const result = validateEmail(email);
          expect(result.success).toBe(false);
          expect(result.errors.length).toBeGreaterThanOrEqual(1);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("rejects emails with empty local part", () => {
    fc.assert(
      fc.property(validDomain(), (domain) => {
        const email = `@${domain}`;
        const result = validateEmail(email);
        expect(result.success).toBe(false);
        expect(result.errors.some((e) => e.rule === "localPartLength")).toBe(true);
      }),
      { numRuns: 100 }
    );
  });

  it("rejects emails with local part > 64 characters", () => {
    fc.assert(
      fc.property(
        // Generate a short string and repeat it to guarantee 65+ chars
        fc.stringMatching(/^[a-z]+$/, { minLength: 5, maxLength: 10 }),
        validDomain(),
        (base, domain) => {
          // Repeat base to exceed 64 characters
          const local = base.repeat(Math.ceil(65 / base.length)).slice(0, 70);
          const email = `${local}@${domain}`;
          if (email.length <= 254) {
            const result = validateEmail(email);
            expect(result.success).toBe(false);
            expect(result.errors.some((e) => e.rule === "localPartLength")).toBe(
              true
            );
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("rejects emails with domain missing a dot", () => {
    fc.assert(
      fc.property(
        validLocalPart(),
        fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 20 }),
        (local, domainNoDot) => {
          const email = `${local}@${domainNoDot}`;
          if (email.length <= 254) {
            const result = validateEmail(email);
            expect(result.success).toBe(false);
            expect(result.errors.some((e) => e.rule === "domainDot")).toBe(true);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});

describe("Feature: app-completion-hardening, Property 2: Password validation correctness", () => {
  /**
   * **Validates: Requirements 4.2**
   *
   * For any string of 8–256 characters containing at least one uppercase letter,
   * one lowercase letter, one digit, and one special character from the allowed set,
   * the password validator SHALL return success: true.
   */
  it("accepts all valid passwords", () => {
    fc.assert(
      fc.property(validPassword(), (password) => {
        const result = validatePassword(password);
        expect(result.success).toBe(true);
        expect(result.errors).toHaveLength(0);
      }),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.2**
   *
   * Passwords shorter than 8 characters are rejected.
   */
  it("rejects passwords shorter than 8 characters", () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z0-9!@#]+$/, { minLength: 1, maxLength: 7 }),
        (password) => {
          const result = validatePassword(password);
          expect(result.success).toBe(false);
          expect(result.errors.some((e) => e.rule === "minLength")).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.2**
   *
   * Passwords missing uppercase letters are rejected.
   */
  it("rejects passwords missing uppercase letters", () => {
    fc.assert(
      fc.property(
        fc
          .array(
            fc.oneof(
              charMatching(/^[a-z]$/),
              charMatching(/^[0-9]$/),
              fc.constantFrom(...SPECIAL_CHARS)
            ),
            { minLength: 8, maxLength: 30 }
          )
          .map((arr) => arr.join("")),
        (password) => {
          const result = validatePassword(password);
          expect(result.success).toBe(false);
          expect(result.errors.some((e) => e.rule === "uppercase")).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.2**
   *
   * Passwords missing lowercase letters are rejected.
   */
  it("rejects passwords missing lowercase letters", () => {
    fc.assert(
      fc.property(
        fc
          .array(
            fc.oneof(
              charMatching(/^[A-Z]$/),
              charMatching(/^[0-9]$/),
              fc.constantFrom(...SPECIAL_CHARS)
            ),
            { minLength: 8, maxLength: 30 }
          )
          .map((arr) => arr.join("")),
        (password) => {
          const result = validatePassword(password);
          expect(result.success).toBe(false);
          expect(result.errors.some((e) => e.rule === "lowercase")).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.2**
   *
   * Passwords missing digits are rejected.
   */
  it("rejects passwords missing digits", () => {
    fc.assert(
      fc.property(
        fc
          .array(
            fc.oneof(
              charMatching(/^[a-zA-Z]$/),
              fc.constantFrom(...SPECIAL_CHARS)
            ),
            { minLength: 8, maxLength: 30 }
          )
          .map((arr) => arr.join("")),
        (password) => {
          const result = validatePassword(password);
          expect(result.success).toBe(false);
          expect(result.errors.some((e) => e.rule === "digit")).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 4.2**
   *
   * Passwords missing special characters are rejected.
   */
  it("rejects passwords missing special characters", () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z0-9]+$/, { minLength: 8, maxLength: 30 }),
        (password) => {
          const result = validatePassword(password);
          expect(result.success).toBe(false);
          expect(result.errors.some((e) => e.rule === "specialChar")).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });
});

describe("Feature: app-completion-hardening, Property 3: Validation reports all failures simultaneously", () => {
  /**
   * **Validates: Requirements 4.4**
   *
   * For any input that violates N distinct validation rules (where N > 1),
   * the validator SHALL return exactly N error objects in the errors array,
   * each identifying a different failed rule.
   */
  it("password validation reports exactly N errors for N violated rules", () => {
    // Generate a password configuration specifying which rules to violate
    const violationConfig = fc.record({
      tooShort: fc.boolean(),
      noUppercase: fc.boolean(),
      noLowercase: fc.boolean(),
      noDigit: fc.boolean(),
      noSpecial: fc.boolean(),
    });

    fc.assert(
      fc.property(violationConfig, (config) => {
        // Count how many rules we will violate
        const violatedRules: string[] = [];

        // Build a password that violates exactly the selected rules
        const chars: string[] = [];

        if (!config.noUppercase) chars.push("A");
        else violatedRules.push("uppercase");

        if (!config.noLowercase) chars.push("a");
        else violatedRules.push("lowercase");

        if (!config.noDigit) chars.push("1");
        else violatedRules.push("digit");

        if (!config.noSpecial) chars.push("!");
        else violatedRules.push("specialChar");

        if (config.tooShort) {
          violatedRules.push("minLength");
          // Keep it short: use only what we have (max 4 chars)
        } else {
          // Pad to at least 8 characters with characters that don't add missing classes
          while (chars.length < 8) {
            if (!config.noLowercase) chars.push("b");
            else if (!config.noUppercase) chars.push("B");
            else if (!config.noDigit) chars.push("2");
            else if (!config.noSpecial) chars.push("@");
            else chars.push(" "); // space is not in any required class
          }
        }

        // Only test if at least 2 rules are violated (Property 3 is about N > 1)
        if (violatedRules.length < 2) return;

        const password = chars.join("");
        const result = validatePassword(password);

        expect(result.success).toBe(false);
        expect(result.errors).toHaveLength(violatedRules.length);

        // Verify each violated rule is reported
        const reportedRules = result.errors.map((e) => e.rule);
        for (const rule of violatedRules) {
          expect(reportedRules).toContain(rule);
        }
      }),
      { numRuns: 100 }
    );
  });

  it("email validation reports multiple errors simultaneously", () => {
    // Test: email with local part > 64 chars AND domain missing a dot
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-z]+$/, { minLength: 5, maxLength: 10 }),
        fc.stringMatching(/^[a-z0-9]+$/, { minLength: 1, maxLength: 20 }),
        (base, domainNoDot) => {
          // Repeat base to exceed 64 characters
          const longLocal = base.repeat(Math.ceil(65 / base.length)).slice(0, 70);
          const email = `${longLocal}@${domainNoDot}`;
          if (email.length <= 254) {
            const result = validateEmail(email);
            expect(result.success).toBe(false);
            // Should report both localPartLength AND domainDot errors
            expect(result.errors.length).toBeGreaterThanOrEqual(2);
            expect(result.errors.some((e) => e.rule === "localPartLength")).toBe(
              true
            );
            expect(result.errors.some((e) => e.rule === "domainDot")).toBe(true);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
