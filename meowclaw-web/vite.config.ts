import path from "path";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, normalizePath } from "vite";
import react from "@vitejs/plugin-react";
import { viteStaticCopy } from "vite-plugin-static-copy";

const pdfjsDist = normalizePath(path.resolve(__dirname, "node_modules/pdfjs-dist"));

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    viteStaticCopy({
      targets: ["cmaps", "standard_fonts", "wasm", "iccs"].map((directory) => ({
        src: `${pdfjsDist}/${directory}`,
        dest: "pdfjs",
      })),
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/upload": "http://127.0.0.1:8080",
    },
  },
});
