export interface MemoryEntityDTO {
  id: number;
  name: string;
}

export interface MemoryRelationDTO {
  id: number;
  entityId: number;
  description: string;
}

export interface MemoryNodeDTO {
  id: number;
  type: string;
  content: string;
  lastAccessedAt: number | null;
  createdAt: number;
  updatedAt: number;
  entities: MemoryEntityDTO[] | null;
  relations: MemoryRelationDTO[] | null;
}

export interface MemoryEntityWithCount {
  id: number;
  name: string;
  memoryCount: number;
}

export interface MemoryGraph {
  memories: { id: number; type: string; content: string }[];
  entities: { id: number; name: string }[];
  relations: { memoryId: number; entityId: number; description: string }[];
}
