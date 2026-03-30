import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Logo } from "@/components/logo";

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center">
      <Logo className="h-16 w-16 mb-4" />
      <h1 className="text-4xl font-bold mb-2">404</h1>
      <p className="text-muted-foreground mb-4">页面不存在</p>
      <Button asChild>
        <Link to="/">返回首页</Link>
      </Button>
    </div>
  );
}
