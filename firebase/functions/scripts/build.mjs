// Bundles every function in src/functions/*.ts into its own deployable folder: dist/<name>/{index.js,package.json}.
// One folder per function means a change to one function's code changes only that function's zip, and so only that
// function is redeployed by Terraform (infra/firebase/functions.tf). Shared code is bundled into each, so changing it
// redeploys every function that uses it.
import { build } from "esbuild";
import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { basename, join } from "node:path";

const root = new URL("..", import.meta.url).pathname;
const pkg = JSON.parse(await readFile(join(root, "package.json"), "utf8"));
// Installed by Cloud Build from the generated package.json; everything else (zod) is bundled.
const externals = ["firebase-admin", "firebase-functions"];
const names = (await readdir(join(root, "src/functions")))
  .filter((f) => f.endsWith(".ts"))
  .map((f) => basename(f, ".ts"))
  .sort();

await rm(join(root, "dist"), { recursive: true, force: true });
for (const name of names) {
  const out = join(root, "dist", name);
  await mkdir(out, { recursive: true });
  await build({
    entryPoints: [join(root, "src/functions", `${name}.ts`)],
    outfile: join(out, "index.js"),
    bundle: true,
    platform: "node",
    target: "node22",
    format: "cjs",
    external: externals,
    sourcemap: false,
    legalComments: "none",
    logLevel: "warning",
  });
  const dependencies = Object.fromEntries(externals.map((d) => [d, pkg.dependencies[d]]));
  const manifest = { name: `stayfocus-fn-${name}`, private: true, main: "index.js", engines: { node: "22" }, dependencies };
  await writeFile(join(out, "package.json"), JSON.stringify(manifest, null, 2) + "\n");
}
console.log(`built ${names.length} function(s): ${names.join(", ")}`);
