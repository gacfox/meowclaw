import { ExternalLink, Info } from "lucide-react";
import { siGithub } from "simple-icons";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

const GITHUB_URL = "https://github.com/gacfox/meowclaw";

export function AboutDialog() {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          title="关于 MeowClaw"
          aria-label="关于 MeowClaw"
        >
          <Info className="size-4" />
        </Button>
      </DialogTrigger>
      <DialogContent className="gap-6 p-6 sm:max-w-md">
        <DialogHeader className="items-center gap-3 text-center">
          <img
            src="/favicon.svg"
            alt="MeowClaw Logo"
            className="size-16 rounded-2xl bg-muted p-2 ring-1 ring-foreground/10"
          />
          <div className="space-y-1">
            <DialogTitle className="text-2xl">MeowClaw</DialogTitle>
            <p className="text-sm text-muted-foreground">喵爪智能体</p>
          </div>
          <DialogDescription className="leading-6">
            可自托管的单用户 ReAct 风格通用智能体工具。通过可配置的智能体、工具、记忆与 MCP 扩展，完成多轮对话和自动化任务。
          </DialogDescription>
        </DialogHeader>
        <div className="rounded-lg border bg-muted/30 px-4 py-3">
          <p className="text-xs font-medium text-muted-foreground">GitHub 仓库</p>
          <Button variant="link" asChild className="mt-1 h-auto max-w-full gap-1.5 p-0 text-sm">
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer">
              <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4 fill-current">
                <path d={siGithub.path} />
              </svg>
              <span className="break-all">github.com/gacfox/meowclaw</span>
              <ExternalLink className="size-3.5" />
            </a>
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
