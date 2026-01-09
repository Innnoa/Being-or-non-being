#pragma once

#include <cstdint>
#include <string>

// 服务器整体配置（由 JSON 加载，若读取失败则保持默认值）
struct ServerConfig {
  uint16_t tcp_port = 7777;
  uint16_t udp_port = 7778;
  uint32_t max_players_per_room = 4;
  uint32_t tick_rate = 60;
  uint32_t state_sync_rate = 30;
  uint32_t map_width = 2000;
  uint32_t map_height = 2000;
  float move_speed = 200.0f;
  std::string log_level = "info";
};

// 从配置文件加载配置；若未找到文件或解析失败，返回 false 并保留默认值
bool LoadServerConfig(ServerConfig* out);
