/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // `jose` (pulled in by firebase-admin through jwks-rsa) ships ESM only; compile it to CommonJS for Jest.
  transform: { "^.+\\.[tj]s$": ["ts-jest", { tsconfig: "tsconfig.test.json", diagnostics: true }] },
  transformIgnorePatterns: ["/node_modules/(?!jose/)"],
  silent: true,
  testTimeout: 20000,
};
