import { createServer } from "node:http";
import { createApp, loadConfig } from "./app.js";

const config = loadConfig();
const server = createServer(createApp(config));
server.listen(config.port, () => {
  console.log(`AutoVerse 3D API listening on :${config.port} (AI provider: ${config.provider})`);
});
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => server.close(() => process.exit(0)));
