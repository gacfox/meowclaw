import { Link } from "react-router-dom";
import { Cat } from "lucide-react";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center">
      <Cat className="h-16 w-16 mb-4 text-muted-foreground" />
      <h1 className="text-4xl font-bold mb-2">404</h1>
      <p className="text-muted-foreground mb-4">页面不存在</p>
      <Button asChild>
        <Link to="/">返回首页</Link>
      </Button>
    </div>
  );
}
