import { request } from "@/services/request";

export interface UserDto {
  id: number;
  username: string;
  displayUsername?: string;
  avatarUrl?: string;
}

export interface UpdateProfileDto {
  displayUsername?: string;
}

export const userService = {
  async getCurrentUser() {
    return request.request<UserDto>("/api/users/me");
  },

  async updateProfile(data: UpdateProfileDto, avatar?: File) {
    const formData = new FormData();
    formData.append("data", JSON.stringify(data));
    if (avatar) {
      formData.append("avatar", avatar);
    }

    return request.request<UserDto>("/api/users/me/profile", {
      method: "PUT",
      body: formData,
    });
  },

  async updatePassword(password: string) {
    return request.request<void>("/api/users/me/password", {
      method: "PUT",
      body: JSON.stringify({ password }),
    });
  },
};
