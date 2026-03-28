import { ChatInterface } from "@/components/chat-interface";
import { LlmManager } from "@/components/llm-manager";
import { AgentManager } from "@/components/agent-manager";
import { ConversationManager } from "@/components/conversation-manager";
import { SystemSettings } from "@/components/system-settings";

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
