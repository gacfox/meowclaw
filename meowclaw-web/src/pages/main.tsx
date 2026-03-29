import { ChatInterface } from "@/pages/chat-interface";
import { LlmManager } from "@/pages/llm-manager";
import { AgentManager } from "@/pages/agent-manager";
import { ConversationManager } from "@/pages/conversation-manager";
import { SystemSettings } from "@/pages/system-settings";

interface MainPageProps {
  activeTab: string;
}

export function MainPage({ activeTab }: MainPageProps) {
  const renderContent = () => {
    switch (activeTab) {
      case "chat":
        return <ChatInterface />;
      case "llm":
        return <LlmManager />;
      case "agent":
        return <AgentManager />;
      case "conversation":
        return <ConversationManager />;
      case "settings":
        return <SystemSettings />;
      default:
        return <ChatInterface />;
    }
  };

  return (
    <div className="h-[calc(100vh-3.5rem)]">
      {renderContent()}
    </div>
  );
}
