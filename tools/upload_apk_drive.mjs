// Upload the release APK to Google Drive > My APKs > WoW Forever > wow-forever.apk (gws CLI).
// Usage: node tools/upload_apk_drive.mjs   (after ./gradlew assembleModernRelease)
import { execFileSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
// gws through its run.js, not gws.cmd: cmd.exe strips the quotes out of --params JSON
const GWS = join(process.env.APPDATA || "", "npm", "node_modules", "@googleworkspace", "cli", "run.js");
function gws(...args) {
  const out = execFileSync(process.execPath, [GWS, ...args], { encoding: "utf8", maxBuffer: 16 << 20 });
  const o = JSON.parse(out.slice(out.indexOf("{")));
  if (o.error) throw new Error(JSON.stringify(o.error));
  return o;
}
const q = (s) => s.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
function child(parent, name, folder) {
  const hit = gws("drive", "files", "list", "--params", JSON.stringify({
    q: `name='${q(name)}' and '${parent}' in parents and trashed=false` +
       (folder ? " and mimeType='application/vnd.google-apps.folder'" : ""),
    fields: "files(id,webViewLink)" })).files[0];
  if (hit || !folder) return hit;
  return gws("drive", "files", "create", "--params", JSON.stringify({ fields: "id" }), "--json",
    JSON.stringify({ name, parents: [parent], mimeType: "application/vnd.google-apps.folder" }));
}

const dir = child(child("root", "My APKs", true).id, "WoW Forever", true);
const apk = join(ROOT, "app/build/outputs/apk/modern/release/app-modern-release.apk");
const have = child(dir.id, "wow-forever.apk", false);
const type = ["--upload", apk, "--upload-content-type", "application/vnd.android.package-archive"];
have
  ? gws("drive", "files", "update", "--params", JSON.stringify({ fileId: have.id, fields: "id" }), ...type)
  : gws("drive", "files", "create", "--params", JSON.stringify({ fields: "id" }),
        "--json", JSON.stringify({ name: "wow-forever.apk", parents: [dir.id] }), ...type);
console.log("APK folder: https://drive.google.com/drive/folders/" + dir.id);
