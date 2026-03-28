import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Cat } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { authService } from "@/services/auth";
import { userService } from "@/services/user";
import { useUserStore } from "@/stores/userStore";

export function LoginPage() {
  const navigate = useNavigate();
  const setUser = useUserStore((state) => state.setUser);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);

    try {
      const loginResponse = await authService.login({ username, password });
      if (loginResponse.code === 200 && loginResponse.data?.token) {
        const userResponse = await userService.getCurrentUser();
        if (userResponse.code === 200 && userResponse.data) {
          setUser(userResponse.data);
          navigate("/");
        } else {
          setError("获取用户信息失败");
        }
      } else {
        setError(loginResponse.message || "登录失败");
      }
    } catch (err) {
      setError("登录失败，请检查用户名和密码");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1">
          <div className="flex items-center justify-center gap-2 mb-4">
            <Cat className="h-8 w-8" />
          </div>
          <CardTitle className="text-2xl text-center">登录</CardTitle>
          <CardDescription className="text-center">
            请输入您的账号密码
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">用户名</Label>
              <Input
                id="username"
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input
                id="password"
                type="password"
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && <div className="text-sm text-destructive">{error}</div>}
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? "登录中..." : "登录"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
