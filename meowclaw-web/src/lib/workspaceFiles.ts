const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "avif"]);
const AUDIO_EXTENSIONS = new Set(["mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"]);
const VIDEO_EXTENSIONS = new Set(["mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v", "flv"]);
const ARCHIVE_EXTENSIONS = new Set(["zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz"]);
const SPREADSHEET_EXTENSIONS = new Set(["csv", "tsv", "xls", "xlsx", "xlsm", "ods"]);
const TERMINAL_EXTENSIONS = new Set(["sh", "bash", "zsh", "fish", "ps1", "psm1", "bat", "cmd"]);
const CODE_EXTENSIONS = new Set([
  "js", "mjs", "cjs", "jsx", "ts", "tsx", "json", "java", "kt", "kts", "py", "go", "rs",
  "c", "h", "cpp", "cc", "hpp", "cs", "rb", "php", "swift", "css", "scss", "less", "html",
  "htm", "xml", "sql", "vue", "svelte", "graphql", "gql", "lua", "pl", "scala",
]);

export type WorkspaceFileIconKind = "folder" | "image" | "audio" | "video" | "archive" | "spreadsheet" | "terminal" | "code" | "text" | "file";

export function extensionOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot <= 0 ? "" : name.slice(dot + 1).toLowerCase();
}

export function workspaceFileIconKind(name: string, directory = false): WorkspaceFileIconKind {
  if (directory) return "folder";
  const extension = extensionOf(name);
  if (IMAGE_EXTENSIONS.has(extension)) return "image";
  if (AUDIO_EXTENSIONS.has(extension)) return "audio";
  if (VIDEO_EXTENSIONS.has(extension)) return "video";
  if (ARCHIVE_EXTENSIONS.has(extension)) return "archive";
  if (SPREADSHEET_EXTENSIONS.has(extension)) return "spreadsheet";
  if (TERMINAL_EXTENSIONS.has(extension)) return "terminal";
  if (CODE_EXTENSIONS.has(extension)) return "code";
  return extension ? "text" : "file";
}

export function fileTypeLabel(name: string, directory = false): string {
  if (directory) return "文件夹";
  const extension = extensionOf(name);
  if (!extension) return "文件";
  switch (workspaceFileIconKind(name)) {
    case "image": return "图片";
    case "audio": return "音频";
    case "video": return "视频";
    case "archive": return "压缩文件";
    case "spreadsheet": return "表格";
    case "code":
    case "terminal": return "代码文件";
    default: return `${extension.toUpperCase()} 文件`;
  }
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}
