import type { ConversationDTO } from "./chat";

export interface ProjectDTO {
  id: number;
  agentId: number;
  name: string;
  path: string;
  createdAt: number;
  updatedAt: number;
  conversations: ConversationDTO[] | null;
}
