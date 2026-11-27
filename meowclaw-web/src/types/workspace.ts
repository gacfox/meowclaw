export type CreateEntryType = "FILE" | "DIR";

export interface FileEntry {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  lastModified: number;
}

export type FileContent =
  | { kind: "TEXT"; content: string }
  | { kind: "IMAGE"; blob: Blob; mimeType: string }
  | { kind: "UNSUPPORTED" };

export type ConflictStrategy = "FAIL" | "OVERWRITE" | "SKIP" | "RENAME";

export interface FileOperationItemResult {
  sourcePath: string;
  targetPath: string | null;
  success: boolean;
  skipped: boolean;
  message: string | null;
}

export interface FileOperationResult {
  results: FileOperationItemResult[];
}

export interface WorkspaceProperties {
  files: number;
  directories: number;
  totalSize: number;
  lastModified: number;
}

export interface WorkspaceDownloadTicket {
  ticket: string;
  filename: string;
}

export interface WorkspaceUploadSession {
  uploadId: string;
  fileName: string;
  fileSize: number;
  chunkSize: number;
  totalChunks: number;
  receivedChunks: number[];
}

export interface WorkspaceUploadCompleteResult {
  entry: FileEntry | null;
  skipped: boolean;
  targetPath: string | null;
}
