import React, { useState, useEffect, useRef } from "react";
import { Save, User, Lock, Upload, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useUserStore } from "@/stores/userStore";
import { userService } from "@/services/user";

export const SystemSettings: React.FC = () => {
  const user = useUserStore((state) => state.user);
  const setUser = useUserStore((state) => state.setUser);

  const [displayUsername, setDisplayUsername] = useState("");
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [isProfileSaving, setIsProfileSaving] = useState(false);
  const [isPasswordSaving, setIsPasswordSaving] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");

  useEffect(() => {
    if (user) {
      setDisplayUsername(user.displayUsername || "");
      setAvatarPreview(user.avatarUrl || null);
    }
  }, [user]);

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        setProfileMessage("图片大小不能超过5MB");
        return;
      }
      if (!file.type.startsWith("image/")) {
        setProfileMessage("请上传图片文件");
        return;
      }
      setAvatarFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const clearAvatar = () => {
    setAvatarFile(null);
    setAvatarPreview(user?.avatarUrl || null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleProfileSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsProfileSaving(true);
    setProfileMessage("");

    try {
      const response = await userService.updateProfile(
        { displayUsername },
        avatarFile || undefined
      );

      if (response.code === 200 && response.data) {
        setUser(response.data);
        setAvatarFile(null);
        setProfileMessage("保存成功");
      } else {
        setProfileMessage(response.message || "保存失败");
      }
    } catch {
      setProfileMessage("保存失败，请重试");
    } finally {
      setIsProfileSaving(false);
    }
  };

  const handlePasswordSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsPasswordSaving(true);
    setPasswordMessage("");

    if (newPassword !== confirmPassword) {
      setPasswordMessage("两次输入的密码不一致");
      setIsPasswordSaving(false);
      return;
    }

    if (newPassword.length < 6) {
      setPasswordMessage("密码长度至少为6位");
      setIsPasswordSaving(false);
      return;
    }

    try {
      const response = await userService.updatePassword(newPassword);

      if (response.code === 200) {
        setPasswordMessage("密码修改成功");
        setNewPassword("");
        setConfirmPassword("");
      } else {
        setPasswordMessage(response.message || "密码修改失败");
      }
    } catch {
      setPasswordMessage("密码修改失败，请重试");
    } finally {
      setIsPasswordSaving(false);
    }
  };

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">系统设置</h1>

      <Tabs defaultValue="profile" className="w-full">
        <TabsList className="grid w-full grid-cols-2 max-w-md">
          <TabsTrigger value="profile" className="flex items-center gap-2">
            <User className="h-4 w-4" />
            个人资料
          </TabsTrigger>
          <TabsTrigger value="password" className="flex items-center gap-2">
            <Lock className="h-4 w-4" />
            修改密码
          </TabsTrigger>
        </TabsList>

        <TabsContent value="profile" className="mt-6">
          <div className="border rounded-lg p-6">
            <h2 className="text-lg font-semibold mb-4">个人资料</h2>
            <form onSubmit={handleProfileSave} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  id="username"
                  value={user?.username || ""}
                  disabled
                  className="bg-muted"
                />
                <p className="text-xs text-muted-foreground">
                  用户名不可修改
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="displayUsername">显示名称</Label>
                <Input
                  id="displayUsername"
                  value={displayUsername}
                  onChange={(e) => setDisplayUsername(e.target.value)}
                  placeholder="设置显示名称"
                />
              </div>
              <div className="space-y-2">
                <Label>头像</Label>
                <div className="flex items-center gap-4">
                  <Avatar className="h-20 w-20">
                    <AvatarImage src={avatarPreview || undefined} />
                    <AvatarFallback className="text-2xl">
                      {user?.username?.slice(0, 2).toUpperCase() || "U"}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <Upload className="h-4 w-4 mr-2" />
                      上传头像
                    </Button>
                    {avatarFile && (
                      <Button
                        type="button"
                        variant="outline"
                        onClick={clearAvatar}
                      >
                        <X className="h-4 w-4 mr-2" />
                        清除
                      </Button>
                    )}
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleAvatarChange}
                    className="hidden"
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  支持 JPG、PNG、GIF、WebP 格式，最大 5MB
                </p>
              </div>
              {profileMessage && (
                <p
                  className={`text-sm ${
                    profileMessage.includes("成功")
                      ? "text-green-500"
                      : "text-destructive"
                  }`}
                >
                  {profileMessage}
                </p>
              )}
              <Button type="submit" disabled={isProfileSaving}>
                <Save className="h-4 w-4 mr-2" />
                {isProfileSaving ? "保存中..." : "保存"}
              </Button>
            </form>
          </div>
        </TabsContent>

        <TabsContent value="password" className="mt-6">
          <div className="border rounded-lg p-6">
            <h2 className="text-lg font-semibold mb-4">修改密码</h2>
            <form onSubmit={handlePasswordSave} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="newPassword">新密码</Label>
                <Input
                  id="newPassword"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="输入新密码（至少6位）"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmPassword">确认新密码</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="再次输入新密码"
                  required
                />
              </div>
              {passwordMessage && (
                <p
                  className={`text-sm ${
                    passwordMessage.includes("成功")
                      ? "text-green-500"
                      : "text-destructive"
                  }`}
                >
                  {passwordMessage}
                </p>
              )}
              <Button type="submit" disabled={isPasswordSaving}>
                <Lock className="h-4 w-4 mr-2" />
                {isPasswordSaving ? "修改中..." : "修改密码"}
              </Button>
            </form>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
};
