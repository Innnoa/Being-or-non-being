#include <spdlog/spdlog.h>

#include "config/server_config.hpp"
#include "game/managers/game_manager.hpp"
#include "game/managers/room_manager.hpp"
#include "message.pb.h"
#include "network/tcp/tcp_server.hpp"
#include "network/udp/udp_server.hpp"

int main() {
  try {
    ServerConfig config;
    const bool loaded = LoadServerConfig(&config);
    if (!loaded) {
      spdlog::warn("未找到配置文件，使用默认配置");
    }

    asio::io_context io;
    GameManager::Instance().SetConfig(config);
    RoomManager::Instance().SetConfig(config);

    GameManager::Instance().SetIoContext(&io);
    UdpServer udp_server(io, config.udp_port);
    GameManager::Instance().SetUdpServer(&udp_server);
    TcpServer tcp_server(io, config.tcp_port);

    spdlog::level::level_enum level = spdlog::level::info;
    try {
      level = spdlog::level::from_str(config.log_level);
    } catch (...) {
      spdlog::warn("日志等级 {} 不合法，使用 info", config.log_level);
    }
    spdlog::set_level(level);
    // Keep the same timestamp/level/message layout, but wrap the level with
    // color markers so the console sink prints it with ANSI colors when the
    // output is a TTY.
    spdlog::set_pattern("%Y-%m-%d %H:%M:%S.%e %^[%l]%$ %v");
    spdlog::info("服务器启动，TCP 端口 {}，UDP 端口 {}", config.tcp_port,
                 config.udp_port);
    udp_server.Start();
    tcp_server.start();

    io.run();
  } catch (std::exception& e) {
    spdlog::error("错误: {}", e.what());
  }
  return 0;
}
